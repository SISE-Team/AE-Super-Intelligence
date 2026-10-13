// SPDX-License-Identifier: MIT
package dev.aesi.planner;

import dev.aesi.planner.Model.*;

import java.math.BigInteger;
import java.util.*;

/** Finite backtracking with a shared inventory ledger across every dependency branch. */
public final class Planner<K> {
    public record Result<K>(
            boolean feasible,
            boolean searchComplete,
            Map<K, String> routes,
            Map<String, Long> batches,
            Map<K, Long> withdrawals,
            long finishTick,
            boolean timingKnown,
            int explored,
            List<String> diagnostics) {}

    private final Snapshot<K> snapshot;
    private final Map<K, List<Recipe<K>>> byOutput = new HashMap<>();
    private final Map<String, Recipe<K>> byId = new HashMap<>();
    private final Set<String> diagnostics = new LinkedHashSet<>();
    private int explored;
    private boolean exhausted;
    private static final int MAX_FRONTIER = 128, MAX_DEPTH = 64;

    public Planner(Snapshot<K> snapshot) {
        this.snapshot = snapshot;
        for (var recipe : snapshot.recipes()) {
            if (byId.putIfAbsent(recipe.id(), recipe) != null)
                throw new IllegalArgumentException("duplicate recipe identity");
            byOutput.computeIfAbsent(recipe.output().key(), k -> new ArrayList<>()).add(recipe);
        }
        byOutput.values().forEach(list -> list.sort(Comparator.comparing(Recipe::id)));
    }

    public Result<K> plan(K target, long quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity");
        var initial = new State<K>(snapshot.inventory());
        // AE2 crafting requests intentionally ignore stored final outputs.
        initial.stock.remove(target);
        initial.natural.remove(target);
        List<State<K>> solutions;
        try {
            solutions = produce(target, quantity, initial, new LinkedHashSet<>(), 0);
        } catch (ArithmeticException overflow) {
            diagnostics.add("Quantity exceeds safe arithmetic range");
            solutions = List.of();
        }
        if (solutions.isEmpty())
            return new Result<>(
                    false,
                    !exhausted,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    0,
                    false,
                    explored,
                    List.copyOf(diagnostics));
        var best = solutions.stream().min(this::compare).orElseThrow();
        return new Result<>(
                true,
                !exhausted,
                Map.copyOf(best.routes),
                Map.copyOf(best.batches),
                Map.copyOf(best.withdrawals),
                best.finish,
                best.timingKnown,
                explored,
                List.copyOf(diagnostics));
    }

    private List<State<K>> produce(
            K key, long quantity, State<K> input, Set<K> ancestors, int depth) {
        if (Thread.currentThread().isInterrupted()) {
            exhausted = true;
            return List.of();
        }
        if (++explored > snapshot.searchBudget()) {
            exhausted = true;
            return List.of();
        }
        var state = input.copy();
        long available = state.stock.getOrDefault(key, 0L);
        long take = Math.min(available, quantity);
        if (take > 0) {
            state.stock.put(key, available - take);
            long natural = Math.min(state.natural.getOrDefault(key, 0L), take);
            state.natural.put(key, state.natural.getOrDefault(key, 0L) - natural);
            if (natural > 0) state.withdrawals.merge(key, natural, Math::addExact);
            state.requiredReady =
                    Math.max(state.requiredReady, state.availableAt.getOrDefault(key, 0L));
        }
        long missing = quantity - take;
        if (missing == 0) return List.of(state);
        if (depth >= MAX_DEPTH || ancestors.contains(key)) {
            diagnostics.add("Cyclic or excessively deep dependency: " + key);
            return List.of();
        }
        var visited = new LinkedHashSet<>(ancestors);
        visited.add(key);
        var results = new ArrayList<State<K>>();
        for (var recipe : byOutput.getOrDefault(key, List.of())) {
            var selected = state.routes.get(key);
            if (selected != null && !selected.equals(recipe.id())) continue;
            if (recipe.slots().isEmpty()) {
                diagnostics.add("No compatible machine: " + recipe.id());
                continue;
            }
            long batches = 1 + (missing - 1) / recipe.output().count();
            var branch = state.copy();
            branch.routes.put(key, recipe.id());
            // This step depends only on materials reserved for this branch; finish tracks the whole
            // order.
            long outerReady = branch.requiredReady;
            branch.requiredReady = 0;
            List<State<K>> frontier = List.of(branch);
            for (var ingredient : recipe.inputs()) {
                var next = new ArrayList<State<K>>();
                for (var current : frontier) {
                    for (var option : ingredient.alternatives()) {
                        long amount = Math.multiplyExact(batches, option.count());
                        next.addAll(produce(option.key(), amount, current, visited, depth + 1));
                        if (exhausted) break;
                    }
                    if (exhausted) break;
                }
                frontier = trim(next);
                if (frontier.isEmpty()) break;
            }
            for (var current : frontier) {
                var assignments =
                        Allocation.allocate(
                                batches, current.requiredReady, recipe.slots(), current.occupied);
                long end = 0;
                for (var assignment : assignments) {
                    current.occupied.put(assignment.machine(), assignment.endTick());
                    end = Math.max(end, assignment.endTick());
                }
                current.finish = Math.max(current.finish, end);
                current.timingKnown &= recipe.slots().stream().allMatch(Slot::timeKnown);
                current.batches.merge(recipe.id(), batches, Math::addExact);
                for (var output : recipe.outputs()) {
                    current.stock.merge(
                            output.key(),
                            Math.multiplyExact(batches, output.count()),
                            Math::addExact);
                    current.availableAt.merge(output.key(), end, Math::max);
                }
                current.stock.put(
                        key, Math.subtractExact(current.stock.getOrDefault(key, 0L), missing));
                current.requiredReady = Math.max(outerReady, end);
                results.add(current);
            }
            if (exhausted) break;
        }
        if (results.isEmpty()) diagnostics.add("Unavailable material or dependency: " + key);
        return trim(results);
    }

    private List<State<K>> trim(List<State<K>> values) {
        if (values.size() <= MAX_FRONTIER) return values;
        exhausted = true;
        var sorted = new ArrayList<>(values);
        sorted.sort(this::compare);
        return new ArrayList<>(sorted.subList(0, MAX_FRONTIER));
    }

    private BigInteger material(State<K> s) {
        return s.withdrawals.entrySet().stream()
                .map(
                        e ->
                                BigInteger.valueOf(e.getValue())
                                        .multiply(
                                                snapshot.materialCosts()
                                                        .getOrDefault(
                                                                e.getKey(), snapshot.costUnit())))
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    private int compare(State<K> a, State<K> b) {
        int result =
                switch (snapshot.strategy()) {
                    case MATERIALS -> material(a).compareTo(material(b));
                    case SPEED -> Long.compare(a.finish, b.finish);
                    case BALANCED ->
                            material(a)
                                    .multiply(BigInteger.valueOf(20))
                                    .add(BigInteger.valueOf(a.finish).multiply(snapshot.costUnit()))
                                    .compareTo(
                                            material(b)
                                                    .multiply(BigInteger.valueOf(20))
                                                    .add(
                                                            BigInteger.valueOf(b.finish)
                                                                    .multiply(
                                                                            snapshot.costUnit())));
                };
        if (result == 0) result = material(a).compareTo(material(b));
        if (result == 0) result = Long.compare(a.finish, b.finish);
        if (result == 0) result = a.routes.toString().compareTo(b.routes.toString());
        return result;
    }

    private static final class State<K> {
        final Map<K, Long> stock = new HashMap<>(),
                natural = new HashMap<>(),
                withdrawals = new HashMap<>(),
                availableAt = new HashMap<>();
        final Map<K, String> routes = new LinkedHashMap<>();
        final Map<String, Long> batches = new HashMap<>(), occupied = new HashMap<>();
        long requiredReady, finish;
        boolean timingKnown = true;

        State(Map<K, Long> inventory) {
            stock.putAll(inventory);
            natural.putAll(inventory);
        }

        State<K> copy() {
            var s = new State<K>(Map.of());
            s.stock.putAll(stock);
            s.natural.putAll(natural);
            s.withdrawals.putAll(withdrawals);
            s.availableAt.putAll(availableAt);
            s.routes.putAll(routes);
            s.batches.putAll(batches);
            s.occupied.putAll(occupied);
            s.requiredReady = requiredReady;
            s.finish = finish;
            s.timingKnown = timingKnown;
            return s;
        }
    }
}

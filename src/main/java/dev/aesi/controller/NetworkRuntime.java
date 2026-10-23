// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;

import dev.aesi.planner.*;
import dev.aesi.planner.Model.*;

import java.util.*;

public final class NetworkRuntime {
    public record Bundle(
            List<Recipe<AEKey>> recipes,
            Map<String, IPatternDetails> patterns,
            Map<AEKey, Strategy> goals) {
        public Bundle {
            recipes = List.copyOf(recipes);
            patterns = Map.copyOf(patterns);
            goals = Map.copyOf(goals);
        }
    }

    private record Published(IGrid grid, Bundle bundle, int priority, String position) {}

    private static final Map<SmartLogic, Published> CONTROLLERS = new WeakHashMap<>();
    private static final Map<IGrid, List<String>> DECISIONS = new WeakHashMap<>();
    private static final Set<String> DISPATCHING = new HashSet<>();

    private NetworkRuntime() {}

    public static synchronized void publish(
            SmartLogic owner, IGrid grid, Bundle bundle, int priority, String position) {
        CONTROLLERS.put(owner, new Published(grid, bundle, priority, position));
    }

    public static synchronized void remove(SmartLogic owner) {
        CONTROLLERS.remove(owner);
    }

    public static synchronized Bundle snapshot(IGrid grid) {
        var recipes = new LinkedHashMap<String, Recipe<AEKey>>();
        var patterns = new LinkedHashMap<String, IPatternDetails>();
        var goals = new LinkedHashMap<AEKey, Strategy>();
        CONTROLLERS.entrySet().stream()
                .filter(e -> e.getKey().operational())
                .map(Map.Entry::getValue)
                .filter(p -> p.grid() == grid)
                .sorted(
                        Comparator.comparingInt(Published::priority)
                                .reversed()
                                .thenComparing(Published::position))
                .forEach(
                        p -> {
                            p.bundle().recipes().forEach(r -> recipes.putIfAbsent(r.id(), r));
                            p.bundle().patterns().forEach(patterns::putIfAbsent);
                            p.bundle().goals().forEach(goals::putIfAbsent);
                        });
        return new Bundle(List.copyOf(recipes.values()), patterns, goals);
    }

    public static synchronized boolean claim(String machine) {
        return DISPATCHING.add(machine);
    }

    public static synchronized void release(String machine) {
        DISPATCHING.remove(machine);
    }

    public static synchronized void clear() {
        CONTROLLERS.clear();
        DISPATCHING.clear();
        DECISIONS.clear();
    }

    public static synchronized void noteDecision(
            IGrid grid, AEKey output, long amount, Planner.Result<AEKey> result) {
        CONTROLLERS.forEach((owner,published)->{
            if(published.grid()==grid && published.bundle().goals().containsKey(output))owner.notePlanning();
        });
        var lines = new ArrayList<String>();
        lines.add(
                "Target "
                        + amount
                        + " "
                        + output
                        + ": feasible="
                        + result.feasible()
                        + ", searchComplete="
                        + result.searchComplete()
                        + ", explored="
                        + result.explored());
        lines.add(
                "Estimated ticks="
                        + (result.feasible() ? result.finishTick() : "unknown")
                        + ", all timings known="
                        + result.timingKnown());
        lines.add("Withdrawals: " + result.withdrawals());
        lines.add("Selected routes: " + result.routes());
        result.diagnostics().stream().limit(12).forEach(lines::add);
        DECISIONS.put(grid, List.copyOf(lines));
    }

    public static synchronized List<String> lastDecision(IGrid grid) {
        return DECISIONS.getOrDefault(grid, List.of());
    }

    public static Recipe<AEKey> normalize(String id, IPatternDetails pattern, List<Slot> slots) {
        return normalize(id, pattern, slots, Set.of(), null);
    }

    public static Recipe<AEKey> normalize(
            String id,
            IPatternDetails pattern,
            List<Slot> slots,
            Set<AEKey> components,
            net.minecraft.world.level.Level level) {
        try {
            var inputs = new ArrayList<Ingredient<AEKey>>();
            for (var input : pattern.getInputs()) {
                var alternatives = new ArrayList<Amount<AEKey>>();
                for (var option : input.getPossibleInputs()) {
                    alternatives.add(
                            new Amount<>(
                                    option.what(),
                                    Math.multiplyExact(option.amount(), input.getMultiplier())));
                }
                if (alternatives.isEmpty()) return null;
                if (level != null)
                    for (var key : components) {
                        if (Arrays.stream(input.getPossibleInputs())
                                        .anyMatch(o -> o.what().getType() == key.getType())
                                && input.isValid(key, level)
                                && alternatives.stream().noneMatch(a -> a.key().equals(key))) {
                            for (var option : input.getPossibleInputs())
                                if (option.what().getType() == key.getType()) {
                                    alternatives.add(
                                            new Amount<>(
                                                    key,
                                                    Math.multiplyExact(
                                                            option.amount(),
                                                            input.getMultiplier())));
                                    break;
                                }
                        }
                    }
                inputs.add(new Ingredient<>(alternatives));
            }
            if (inputs.isEmpty()) return null;
            var outputs = new ArrayList<Amount<AEKey>>();
            for (var output : pattern.getOutputs()) {
                outputs.add(new Amount<>(output.what(), output.amount()));
            }
            if (outputs.isEmpty()) return null;
            return new Recipe<>(id, inputs, outputs, slots);
        } catch (ArithmeticException | IllegalArgumentException unsupported) {
            return null;
        }
    }
}

// SPDX-License-Identifier: MIT
package dev.aesi.planner;

import dev.aesi.planner.Model.Slot;

import java.util.*;

/** Bounded binary-search allocation of identical batches over compatible machine lanes. */
public final class Allocation {
    private Allocation() {}

    public record Assignment(String machine, long batches, long endTick) {}

    public static List<Assignment> allocate(
            long batches, long dependenciesReady, List<Slot> lanes, Map<String, Long> occupied) {
        if (batches <= 0 || lanes.isEmpty()) throw new IllegalArgumentException("allocation");
        var unique = new LinkedHashMap<String, Slot>();
        for (var lane : lanes) unique.putIfAbsent(lane.machine(), lane);
        var slots = List.copyOf(unique.values());
        long low = dependenciesReady, high = dependenciesReady;
        for (var slot : slots) {
            long ready =
                    Math.max(
                            dependenciesReady,
                            Math.max(slot.readyTick(), occupied.getOrDefault(slot.machine(), 0L)));
            high =
                    Math.max(
                            high,
                            Math.addExact(ready, Math.multiplyExact(slot.batchTicks(), batches)));
        }
        while (low < high) {
            long mid = low + (high - low) / 2;
            if (capacity(mid, dependenciesReady, slots, occupied, batches) >= batches) high = mid;
            else low = mid + 1;
        }
        long remaining = batches;
        var result = new ArrayList<Assignment>();
        // Fill later-starting lanes last for stable, deterministic plans.
        var ordered = new ArrayList<>(slots);
        ordered.sort(
                Comparator.comparingLong(
                                (Slot s) ->
                                        Math.max(
                                                s.readyTick(),
                                                occupied.getOrDefault(s.machine(), 0L)))
                        .thenComparing(Slot::machine));
        for (var slot : ordered) {
            long ready =
                    Math.max(
                            dependenciesReady,
                            Math.max(slot.readyTick(), occupied.getOrDefault(slot.machine(), 0L)));
            long count = Math.min(remaining, Math.max(0, low - ready) / slot.batchTicks());
            if (count > 0) {
                result.add(
                        new Assignment(
                                slot.machine(),
                                count,
                                Math.addExact(
                                        ready, Math.multiplyExact(count, slot.batchTicks()))));
                remaining -= count;
            }
        }
        if (remaining != 0) throw new IllegalStateException("allocation did not conserve batches");
        return List.copyOf(result);
    }

    private static long capacity(
            long end, long dep, List<Slot> slots, Map<String, Long> occupied, long limit) {
        long total = 0;
        for (var slot : slots) {
            long ready =
                    Math.max(
                            dep,
                            Math.max(slot.readyTick(), occupied.getOrDefault(slot.machine(), 0L)));
            long amount = Math.max(0, end - ready) / slot.batchTicks();
            if (amount >= limit - total) return limit;
            total += amount;
        }
        return total;
    }
}

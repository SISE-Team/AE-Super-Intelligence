// SPDX-License-Identifier: MIT
package dev.aesi.recipe;

import appeng.api.stacks.*;

import java.util.*;
import java.util.function.Predicate;

/** Exact capacity matching for overlapping tags; a greedy matcher can miss an ambiguous recipe. */
public final class InputMatcher {
    public record Demand(Predicate<AEKey> accepts, long amount) {}

    private InputMatcher() {}

    public static List<GenericStack> supplied(appeng.api.crafting.IPatternDetails pattern) {
        var values = new ArrayList<GenericStack>();
        for (var input : pattern.getInputs()) {
            var option = input.getPossibleInputs()[0];
            values.add(
                    new GenericStack(
                            option.what(),
                            Math.multiplyExact(option.amount(), input.getMultiplier())));
        }
        return ProcessingRecipes.merge(values);
    }

    public static boolean matches(List<GenericStack> supply, List<Demand> demands) {
        int n = supply.size() + demands.size() + 2, sink = n - 1;
        long[][] edges = new long[n][n];
        long required = 0;
        for (int i = 0; i < supply.size(); i++) edges[0][i + 1] = supply.get(i).amount();
        for (int j = 0; j < demands.size(); j++) {
            var demand = demands.get(j);
            int target = supply.size() + j + 1;
            edges[target][sink] = demand.amount();
            required = Math.addExact(required, demand.amount());
            for (int i = 0; i < supply.size(); i++)
                if (demand.accepts().test(supply.get(i).what()))
                    edges[i + 1][target] = Long.MAX_VALUE;
        }
        long flow = 0;
        while (flow < required) {
            int[] previous = new int[n];
            Arrays.fill(previous, -1);
            previous[0] = 0;
            var queue = new ArrayDeque<Integer>();
            queue.add(0);
            while (!queue.isEmpty() && previous[sink] < 0) {
                int a = queue.remove();
                for (int b = 1; b < n; b++)
                    if (previous[b] < 0 && edges[a][b] > 0) {
                        previous[b] = a;
                        queue.add(b);
                    }
            }
            if (previous[sink] < 0) return false;
            long amount = required - flow;
            for (int b = sink; b != 0; b = previous[b])
                amount = Math.min(amount, edges[previous[b]][b]);
            for (int b = sink; b != 0; b = previous[b]) {
                edges[previous[b]][b] -= amount;
                edges[b][previous[b]] += amount;
            }
            flow += amount;
        }
        return true;
    }
}

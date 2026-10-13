// SPDX-License-Identifier: MIT
package dev.aesi.planner;

import dev.aesi.planner.Model.*;

import java.util.*;

public final class PlannerTests {
    private static int checks;

    private static void require(boolean b, String msg) {
        if (!b) throw new AssertionError(msg);
        checks++;
    }

    private static Ingredient<String> in(String key, long count) {
        return new Ingredient<>(List.of(new Amount<>(key, count)));
    }

    @SafeVarargs
    private static Recipe<String> r(
            String id, String out, long n, long time, String machine, Ingredient<String>... ins) {
        return new Recipe<>(
                id,
                List.of(ins),
                List.of(new Amount<>(out, n)),
                List.of(new Slot(machine, 0, time, true)));
    }

    private static Planner.Result<String> plan(
            Map<String, Long> stock,
            List<Recipe<String>> recipes,
            String goal,
            long n,
            Strategy strategy) {
        return new Planner<>(new Snapshot<>(stock, recipes, strategy, 100_000)).plan(goal, n);
    }

    public static void main(String[] args) {
        var base = r("A", "plate", 1, 1, "craft", in("ingot", 2));
        var press =
                new Recipe<>(
                        "B",
                        List.of(in("ingot", 1)),
                        List.of(new Amount<>("plate", 1)),
                        List.of(new Slot("idle", 0, 20, true), new Slot("busy", 100, 20, true)));
        var high =
                new Recipe<>(
                        "C",
                        List.of(in("ingot", 1)),
                        List.of(new Amount<>("plate", 2)),
                        List.<Slot>of());
        var acceptance =
                plan(
                        Map.of("ingot", 80L),
                        List.of(base, press, high),
                        "plate",
                        64,
                        Strategy.MATERIALS);
        require(
                acceptance.feasible() && acceptance.routes().get("plate").equals("B"),
                "64 plates select feasible press");
        require(
                acceptance.withdrawals().get("ingot") == 64 && acceptance.finishTick() == 700,
                "stock conservation and parallel timing");
        require(acceptance.searchComplete(), "small graph fully searched");
        var a = r("a", "a", 1, 10, "a-machine", in("ore", 1));
        var b = r("b", "b", 1, 10, "b-machine", in("ore", 1));
        var finalR = r("final", "goal", 1, 10, "final-machine", in("a", 1), in("b", 1));
        require(
                !plan(Map.of("ore", 1L), List.of(a, b, finalR), "goal", 1, Strategy.MATERIALS)
                        .feasible(),
                "shared ore cannot be reserved twice");
        var shared = r("shared", "part", 2, 10, "shared-machine", in("ore", 1));
        var use = r("use", "goal", 1, 10, "use-machine", in("part", 1), in("part", 1));
        var result = plan(Map.of("ore", 1L), List.of(shared, use), "goal", 1, Strategy.MATERIALS);
        require(
                result.feasible()
                        && result.batches().get("shared") == 1
                        && result.withdrawals().get("ore") == 1,
                "surplus shared across dependencies");
        require(result.finishTick() == 20, "dependency completion time");
        require(
                plan(Map.of("ore", 2L), List.of(a, b, finalR), "goal", 1, Strategy.SPEED)
                                .finishTick()
                        == 20,
                "independent dependencies run in parallel");
        var x = r("x", "x", 1, 1, "x", in("y", 1));
        var y = r("y", "y", 1, 1, "y", in("x", 1));
        require(
                !plan(Map.of(), List.of(x, y), "x", 1, Strategy.MATERIALS).feasible(),
                "cycle rejected");
        var options =
                new Ingredient<String>(
                        List.of(new Amount<>("wood_a", 1), new Amount<>("wood_b", 1)));
        var choose = r("choose", "goal", 1, 1, "craft", options, in("wood_a", 1));
        require(
                plan(
                                Map.of("wood_a", 1L, "wood_b", 1L),
                                List.of(choose),
                                "goal",
                                1,
                                Strategy.MATERIALS)
                        .feasible(),
                "ingredient backtracking shares inventory");
        require(
                !plan(Map.of("wood_a[named]", 2L), List.of(choose), "goal", 1, Strategy.MATERIALS)
                        .feasible(),
                "component-distinct keys never merged");
        var fast = r("fast", "goal", 1, 1, "fast", in("ore", 2));
        var cheap = r("cheap", "goal", 1, 100, "cheap", in("ore", 1));
        require(
                plan(Map.of("ore", 2L), List.of(fast, cheap), "goal", 1, Strategy.SPEED)
                        .routes()
                        .get("goal")
                        .equals("fast"),
                "speed strategy");
        require(
                plan(Map.of("ore", 2L), List.of(fast, cheap), "goal", 1, Strategy.MATERIALS)
                        .routes()
                        .get("goal")
                        .equals("cheap"),
                "material strategy");
        long huge = 2_000_000_000_000_000_000L;
        var precisionCheap = r("precise_cheap", "goal", 1, 2, "precise_c", in("ore", huge));
        var precisionFast = r("precise_fast", "goal", 1, 1, "precise_f", in("ore", huge + 1));
        require(
                plan(
                                Map.of("ore", huge + 1),
                                List.of(precisionCheap, precisionFast),
                                "goal",
                                1,
                                Strategy.MATERIALS)
                        .routes()
                        .get("goal")
                        .equals("precise_cheap"),
                "64-bit material costs retain one-item differences");
        require(
                !new Planner<>(
                                new Snapshot<>(
                                        Map.of("ore", 2L), List.of(fast, cheap), Strategy.SPEED, 1))
                        .plan("goal", 1)
                        .searchComplete(),
                "budget exhaustion explicit");
        var unknown =
                new Recipe<>(
                        "unknown",
                        List.of(in("ore", 1)),
                        List.of(new Amount<>("goal", 1)),
                        List.of(new Slot("u", 0, 20, false)));
        require(
                !plan(Map.of("ore", 1L), List.of(unknown), "goal", 1, Strategy.SPEED).timingKnown(),
                "unknown timing is not certified");
        var allocated =
                Allocation.allocate(
                        1_000_000_000L,
                        0,
                        List.of(new Slot("x", 0, 1, true), new Slot("y", 0, 1, true)),
                        Map.of());
        var solid = r("solid", "goal", 1, 80, "solid", in("item", 1));
        var liquid = r("liquid", "goal", 1, 100, "liquid", in("fluid", 500));
        var unitCosts =
                Map.of(
                        "item",
                        java.math.BigInteger.valueOf(1000),
                        "fluid",
                        java.math.BigInteger.ONE);
        var normalized =
                new Planner<>(
                                new Snapshot<>(
                                        Map.of("item", 1L, "fluid", 500L),
                                        List.of(solid, liquid),
                                        Strategy.MATERIALS,
                                        1000,
                                        unitCosts,
                                        java.math.BigInteger.valueOf(1000)))
                        .plan("goal", 1);
        require(
                normalized.routes().get("goal").equals("liquid"),
                "item/fluid material costs use display units instead of raw millibuckets");
        require(
                normalized.withdrawals().get("fluid") == 500,
                "normalized scoring preserves exact raw transfer quantity");
        var normalizedBalanced =
                new Planner<>(
                                new Snapshot<>(
                                        Map.of("item", 1L, "fluid", 500L),
                                        List.of(solid, liquid),
                                        Strategy.BALANCED,
                                        1000,
                                        unitCosts,
                                        java.math.BigInteger.valueOf(1000)))
                        .plan("goal", 1);
        require(
                normalizedBalanced.routes().get("goal").equals("solid"),
                "balanced time cost uses the same rational scale as material cost");
        require(
                allocated.stream().mapToLong(Allocation.Assignment::batches).sum()
                        == 1_000_000_000L,
                "large batch allocation bounded and conserved");
        // Exhaustive comparison with an independent two-route oracle.
        for (long stock = 0; stock < 15; stock++)
            for (long demand = 1; demand < 8; demand++) {
                var ra = r("a", "goal", 2, 5, "a", in("ore", 3));
                var rb = r("b", "goal", 3, 8, "b", in("ore", 2));
                long na = (demand + 1) / 2, nb = (demand + 2) / 3, ca = na * 3, cb = nb * 2;
                var p =
                        plan(
                                Map.of("ore", stock),
                                List.of(ra, rb),
                                "goal",
                                demand,
                                Strategy.MATERIALS);
                require(p.feasible() == (ca <= stock || cb <= stock), "oracle feasibility");
                if (p.feasible())
                    require(
                            p.withdrawals().get("ore")
                                    == Math.min(
                                            ca <= stock ? ca : Long.MAX_VALUE,
                                            cb <= stock ? cb : Long.MAX_VALUE),
                            "oracle optimal cost");
            }
        System.out.println("AESI planner: " + checks + " assertions passed");
    }
}

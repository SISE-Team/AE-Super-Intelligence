// SPDX-License-Identifier: MIT
package dev.aesi.recipe;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.*;

import java.util.*;
import java.util.function.Function;

/** Bounded, component-preserving enumeration. Never silently publish a truncated ingredient set. */
public final class ProcessingRecipes {
    public static final int VARIANT_LIMIT = 128;

    private ProcessingRecipes() {}

    public static List<GenericStack> items(Ingredient ingredient, int amount) {
        var available = ComponentInputs.items(ingredient::test, amount);
        var samples =
                ingredient
                        .items()
                        .map(
                                h -> {
                                    var sample = h.value().getDefaultInstance();
                                    if (ingredient.getCustomIngredient()
                                            instanceof
                                            net.neoforged.neoforge.common.crafting
                                                            .DataComponentIngredient
                                                    components)
                                        sample.applyComponents(components.components());
                                    return sample;
                                })
                        .filter(ingredient::test)
                        .map(s -> new GenericStack(AEItemKey.of(s), amount))
                        .limit(VARIANT_LIMIT + 1);
        var result =
                java.util.stream.Stream.concat(samples, available.stream())
                        .distinct()
                        .limit(VARIANT_LIMIT + 1)
                        .toList();
        if (result.isEmpty())
            throw new IllegalArgumentException(
                    "custom item ingredient has no valid representative or matching resource in"
                        + " this grid");
        return result;
    }

    public static List<List<GenericStack>> combinations(List<List<GenericStack>> alternatives) {
        List<List<GenericStack>> result = List.of(List.of());
        for (var choices : alternatives) {
            if (choices.isEmpty())
                throw new IllegalArgumentException("ingredient has no registered instances");
            if ((long) result.size() * choices.size() > VARIANT_LIMIT)
                throw new IllegalArgumentException("ingredient variant budget exceeded (128)");
            var next = new ArrayList<List<GenericStack>>();
            for (var prefix : result)
                for (var choice : choices) {
                    var variant = new ArrayList<>(prefix);
                    variant.add(choice);
                    next.add(List.copyOf(variant));
                }
            result = next;
        }
        return result;
    }

    public static List<GenericStack> fluids(
            net.neoforged.neoforge.fluids.crafting.FluidIngredient ingredient, int amount) {
        var available = ComponentInputs.fluids(ingredient::test, amount);
        var samples =
                ingredient.fluids().stream()
                        .map(h -> new net.neoforged.neoforge.fluids.FluidStack(h, amount))
                        .filter(ingredient::test)
                        .map(GenericStack::fromFluidStack);
        var result =
                java.util.stream.Stream.concat(samples, available.stream())
                        .distinct()
                        .limit(VARIANT_LIMIT + 1)
                        .toList();
        if (result.isEmpty())
            throw new IllegalArgumentException(
                    "fluid ingredient has no matching resource in this grid");
        return result;
    }

    public static List<GenericStack> merge(List<GenericStack> stacks) {
        var merged = new LinkedHashMap<AEKey, Long>();
        for (var stack : stacks)
            if (stack != null && stack.amount() > 0)
                merged.merge(stack.what(), stack.amount(), Math::addExact);
        return merged.entrySet().stream()
                .map(e -> new GenericStack(e.getKey(), e.getValue()))
                .toList();
    }

    public static void add(
            ServerLevel level,
            List<Blueprint> output,
            String id,
            String family,
            Recipe<?> source,
            List<List<GenericStack>> alternatives,
            Function<List<GenericStack>, List<GenericStack>> outputs,
            long ticks,
            long eu) {
        if (alternatives.isEmpty())
            throw new IllegalArgumentException(
                    "input-free production requires an explicit world-condition adapter");
        for (var variant : combinations(alternatives)) {
            var result = merge(outputs.apply(variant));
            if (result.isEmpty())
                throw new IllegalArgumentException("recipe has no deterministic output");
            for (int primary = 0; primary < result.size(); primary++) {
                var ordered = new ArrayList<>(result);
                var first = ordered.remove(primary);
                ordered.addFirst(first);
                var nativePattern =
                        PatternDetailsHelper.encodeProcessingPattern(merge(variant), ordered);
                output.add(
                        new Blueprint(
                                id,
                                family,
                                VirtualPatterns.create(level, id, family, source, nativePattern),
                                Math.max(1, ticks),
                                false,
                                eu));
            }
        }
    }
}

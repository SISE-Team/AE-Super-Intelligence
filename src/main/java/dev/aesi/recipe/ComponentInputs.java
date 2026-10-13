// SPDX-License-Identifier: MIT
package dev.aesi.recipe;

import appeng.api.stacks.*;

import java.util.*;
import java.util.function.Predicate;

/** Server-thread catalog scope. Retains the exact resources already present in this ME grid. */
public final class ComponentInputs implements AutoCloseable {
    private static final ThreadLocal<Set<AEKey>> CURRENT = ThreadLocal.withInitial(Set::of);
    private final Set<AEKey> previous;

    public ComponentInputs(Set<AEKey> keys) {
        previous = CURRENT.get();
        CURRENT.set(Set.copyOf(keys));
    }

    public static List<GenericStack> items(
            Predicate<net.minecraft.world.item.ItemStack> predicate, int count) {
        return CURRENT.get().stream()
                .filter(AEItemKey.class::isInstance)
                .map(AEItemKey.class::cast)
                .filter(k -> predicate.test(k.toStack(count)))
                .map(k -> new GenericStack(k, count))
                .toList();
    }

    public static List<GenericStack> fluids(
            Predicate<net.neoforged.neoforge.fluids.FluidStack> predicate, int count) {
        return CURRENT.get().stream()
                .filter(AEFluidKey.class::isInstance)
                .map(AEFluidKey.class::cast)
                .filter(k -> predicate.test(k.toStack(count)))
                .map(k -> new GenericStack(k, count))
                .toList();
    }

    @Override
    public void close() {
        CURRENT.set(previous);
    }
}

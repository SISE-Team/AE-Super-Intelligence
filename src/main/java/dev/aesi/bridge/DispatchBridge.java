// SPDX-License-Identifier: MIT
package dev.aesi.bridge;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.Direction;

/**
 * A thread-confined capability for exactly one native call. Never alters provider pattern slots.
 */
public final class DispatchBridge {
    public record Authorization(
            appeng.api.networking.crafting.ICraftingProvider provider,
            IPatternDetails pattern,
            Direction side) {}

    private static final ThreadLocal<Authorization> CURRENT = new ThreadLocal<>();

    private DispatchBridge() {}

    public static Authorization current() {
        return CURRENT.get();
    }

    public static boolean permitted(Object provider, IPatternDetails pattern) {
        if (!dev.aesi.recipe.VirtualPatterns.isVirtual(pattern)) return true;
        var auth = CURRENT.get();
        return auth != null && auth.provider() == provider && auth.pattern() == pattern;
    }

    public static boolean push(
            appeng.api.networking.crafting.ICraftingProvider provider,
            Direction side,
            IPatternDetails pattern,
            KeyCounter[] inputs) {
        if (!(provider instanceof ProviderAccess access)
                || CURRENT.get() != null
                || access.getGrid() == null
                || provider.isBusy()) return false;
        if (!access.aesiActiveSides().contains(side)) return false;
        CURRENT.set(new Authorization(provider, pattern, side));
        try {
            return provider.pushPattern(pattern, inputs);
        } finally {
            CURRENT.remove();
        }
    }
}

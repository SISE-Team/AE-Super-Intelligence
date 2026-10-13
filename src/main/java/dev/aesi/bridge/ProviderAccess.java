// SPDX-License-Identifier: MIT
package dev.aesi.bridge;

import appeng.api.crafting.IPatternDetails;

import net.minecraft.core.Direction;

import java.util.Set;

public interface ProviderAccess extends appeng.api.networking.crafting.ICraftingProvider {
    Set<Direction> aesiActiveSides();

    void aesiOnPushSuccess(IPatternDetails pattern);

    appeng.api.networking.IGrid getGrid();

    appeng.api.config.LockCraftingMode getCraftingLockedReason();

    appeng.helpers.patternprovider.PatternProviderReturnInventory getReturnInv();

    default appeng.helpers.patternprovider.PatternProviderReturnInventory aesiReturnBuffer() {
        return getReturnInv();
    }

    appeng.api.inventories.InternalInventory getPatternInv();
}

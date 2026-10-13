// SPDX-License-Identifier: MIT
package dev.aesi.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.NetworkCraftingSimulationState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = NetworkCraftingSimulationState.class, remap = false)
public interface NetworkInventoryMixin {
    @Accessor("list")
    KeyCounter aesiInventory();
}

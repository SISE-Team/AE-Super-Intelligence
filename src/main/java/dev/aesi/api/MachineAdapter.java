// SPDX-License-Identifier: MIT
package dev.aesi.api;

import dev.aesi.recipe.Blueprint;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Capability matching must describe the real process, not infer it from inventory acceptance. */
public interface MachineAdapter {
    boolean supports(BlockEntity machine, Blueprint recipe);

    default boolean ready(BlockEntity machine, Blueprint recipe) {
        return true;
    }

    default long queueTicks(BlockEntity machine, Blueprint recipe) {
        return 0;
    }

    default boolean canRecover(
            BlockEntity machine, Blueprint recipe, appeng.api.stacks.AEKey output) {
        return false;
    }

    default java.util.List<dev.aesi.bridge.OutputRecovery.Port> outputs(BlockEntity machine) {
        return java.util.List.of();
    }
}

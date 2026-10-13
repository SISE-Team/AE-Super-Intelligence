// SPDX-License-Identifier: MIT
package dev.aesi.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.*;

import dev.aesi.bridge.TaskPlanAccess;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

import java.util.Collection;

@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class CraftingTreeMixin {
    @Shadow @Final private CraftingCalculation job;

    @Redirect(
            method = "buildChildPatterns",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/api/networking/crafting/ICraftingService;getCraftingFor(Lappeng/api/stacks/AEKey;)Ljava/util/Collection;"))
    private Collection<IPatternDetails> aesiTaskRoutes(ICraftingService service, AEKey output) {
        return ((TaskPlanAccess) job).aesiSelect(output, service.getCraftingFor(output));
    }
}

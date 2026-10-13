// SPDX-License-Identifier: MIT
package dev.aesi.mixin;

import appeng.helpers.patternprovider.PatternProviderReturnInventory;

import org.spongepowered.asm.mixin.*;

/** The public return inventory is a page view; recovery belongs in the persisted full buffer. */
@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic", remap = false)
public abstract class OverloadedReturnBufferMixin {
    @Shadow
    public abstract PatternProviderReturnInventory getInternalReturnInv();

    public PatternProviderReturnInventory aesiReturnBuffer() {
        return getInternalReturnInv();
    }
}

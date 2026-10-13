// SPDX-License-Identifier: MIT
package dev.aesi.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import com.llamalad7.mixinextras.injector.wrapoperation.*;

import dev.aesi.bridge.DispatchBridge;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(
        targets = {
            "io.github.lounode.ae2cs.common.me.logic.ResonatingPatternProviderLogic",
            "io.github.lounode.ae2cs.common.me.logic.MeteoritePatternProviderLogic",
            "com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic"
        },
        remap = false)
public abstract class AddonProviderMixin {
    @Inject(method = "pushPattern", at = @At("HEAD"), order = 900, cancellable = true, require = 0)
    private void aesiRequireScope(
            IPatternDetails pattern, KeyCounter[] inputs, CallbackInfoReturnable<Boolean> cir) {
        if (!DispatchBridge.permitted(this, pattern)) cir.setReturnValue(false);
    }

    @WrapOperation(
            method = "pushPattern",
            at = @At(value = "INVOKE", target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"),
            require = 0)
    private boolean aesiAuthorized(List<?> patterns, Object pattern, Operation<Boolean> original) {
        var auth = DispatchBridge.current();
        return original.call(patterns, pattern)
                || auth != null && auth.provider() == (Object) this && auth.pattern() == pattern;
    }
}

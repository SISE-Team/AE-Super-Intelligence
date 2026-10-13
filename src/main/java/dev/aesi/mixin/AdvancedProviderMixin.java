// SPDX-License-Identifier: MIT
package dev.aesi.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import appeng.helpers.patternprovider.PatternProviderTarget;

import com.llamalad7.mixinextras.injector.wrapoperation.*;

import dev.aesi.bridge.*;

import net.minecraft.core.Direction;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

/** AdvancedAE owns a separate provider implementation; scope its native checks identically. */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic", remap = false)
public abstract class AdvancedProviderMixin implements ProviderAccess {
    @Invoker("getActiveSides")
    public abstract Set<Direction> aesiActiveSides();

    @Invoker("onPushPatternSuccess")
    public abstract void aesiOnPushSuccess(IPatternDetails pattern);

    @Inject(method = "pushPattern", at = @At("HEAD"), order = 900, cancellable = true)
    private void aesiRequireScope(
            IPatternDetails pattern, KeyCounter[] inputs, CallbackInfoReturnable<Boolean> cir) {
        if (!DispatchBridge.permitted(this, pattern)) cir.setReturnValue(false);
    }

    @WrapOperation(
            method = "pushPattern",
            at = @At(value = "INVOKE", target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
    private boolean aesiAuthorized(List<?> patterns, Object pattern, Operation<Boolean> original) {
        var auth = DispatchBridge.current();
        return original.call(patterns, pattern)
                || auth != null && auth.provider() == (Object) this && auth.pattern() == pattern;
    }

    @Inject(method = "getActiveSides", at = @At("RETURN"), cancellable = true)
    private void aesiDirection(CallbackInfoReturnable<Set<Direction>> cir) {
        var auth = DispatchBridge.current();
        if (auth != null && auth.provider() == (Object) this)
            cir.setReturnValue(
                    cir.getReturnValue().contains(auth.side())
                            ? EnumSet.of(auth.side())
                            : EnumSet.noneOf(Direction.class));
    }

    @WrapOperation(
            method = "pushPattern",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/helpers/patternprovider/PatternProviderTarget;containsPatternInput(Ljava/util/Set;)Z"))
    private boolean aesiBlocking(
            PatternProviderTarget target, Set<AEKey> original, Operation<Boolean> operation) {
        var auth = DispatchBridge.current();
        if (auth == null || auth.provider() != (Object) this)
            return operation.call(target, original);
        var keys = new HashSet<>(original);
        for (var input : auth.pattern().getInputs())
            for (var option : input.getPossibleInputs()) keys.add(option.what().dropSecondary());
        return operation.call(target, keys);
    }
}

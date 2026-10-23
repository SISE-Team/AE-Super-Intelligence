// SPDX-License-Identifier: MIT
package dev.aesi.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.*;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.inv.NetworkCraftingSimulationState;

import dev.aesi.bridge.TaskPlanAccess;
import dev.aesi.controller.NetworkRuntime;
import dev.aesi.planner.*;
import dev.aesi.planner.Model.Snapshot;

import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import java.util.*;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationMixin implements TaskPlanAccess {
    @Shadow @Final private NetworkCraftingSimulationState networkInv;
    @Shadow @Final private AEKey output;
    @Unique private NetworkRuntime.Bundle aesiBundle;
    @Unique private IGrid aesiGrid;
    @Unique private Map<AEKey, IPatternDetails> aesiRoutes = Map.of();
    @Unique private Set<AEItemKey> aesiAuthorized = Set.of();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void aesiSnapshot(
            Level level,
            IGrid grid,
            ICraftingSimulationRequester requester,
            GenericStack output,
            CalculationStrategy strategy,
            CallbackInfo ci) {
        aesiBundle = NetworkRuntime.snapshot(grid);
        aesiAuthorized = aesiBundle.patterns().values().stream().map(IPatternDetails::getDefinition)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        aesiGrid = grid;
    }

    @Inject(method = "runCraftAttempt", at = @At("HEAD"))
    private void aesiPlan(boolean simulate, long amount, CallbackInfoReturnable<?> cir) {
        aesiRoutes = Map.of();
        if (aesiBundle == null || !aesiBundle.goals().containsKey(output)) return;
        var stock = new HashMap<AEKey, Long>();
        for (var entry : ((NetworkInventoryMixin) networkInv).aesiInventory())
            if (entry.getLongValue() > 0) stock.put(entry.getKey(), entry.getLongValue());
        var unit = java.math.BigInteger.ONE;
        for (var key : stock.keySet()) {
            var amountPerUnit = java.math.BigInteger.valueOf(Math.max(1, key.getAmountPerUnit()));
            unit = unit.divide(unit.gcd(amountPerUnit)).multiply(amountPerUnit);
        }
        var costs = new HashMap<AEKey, java.math.BigInteger>();
        for (var key : stock.keySet())
            costs.put(
                    key,
                    unit.divide(java.math.BigInteger.valueOf(Math.max(1, key.getAmountPerUnit()))));
        var result =
                new Planner<>(
                                new Snapshot<>(
                                        stock,
                                        aesiBundle.recipes(),
                                        aesiBundle.goals().get(output),
                                        16_384,
                                        costs,
                                        unit))
                        .plan(output, amount);
        NetworkRuntime.noteDecision(aesiGrid, output, amount, result);
        if (!result.feasible()) return; // Native simulation explains missing resources normally.
        var routes = new HashMap<AEKey, IPatternDetails>();
        result.routes()
                .forEach(
                        (key, id) -> {
                            var pattern = aesiBundle.patterns().get(id);
                            if (pattern != null) routes.put(key, pattern);
                        });
        aesiRoutes = Map.copyOf(routes);
    }

    public Collection<IPatternDetails> aesiSelect(
            AEKey key, Collection<IPatternDetails> candidates) {
        // AE's crafting-provider index can lag a card removal by one tick. A fresh job may only
        // use virtual definitions that were authorized in its immutable controller snapshot.
        candidates=candidates.stream().filter(p->!dev.aesi.recipe.VirtualPatterns.isVirtual(p)
                ||aesiAuthorized.contains(p.getDefinition())).toList();
        var selected = aesiRoutes.get(key);
        if (selected == null) return candidates;
        var exact =
                candidates.stream()
                        .filter(p -> p.getDefinition().equals(selected.getDefinition()))
                        .toList();
        return exact.isEmpty() ? candidates : exact;
    }
}

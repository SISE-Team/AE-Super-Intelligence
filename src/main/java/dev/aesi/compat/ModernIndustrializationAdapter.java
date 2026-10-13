// SPDX-License-Identifier: MIT
package dev.aesi.compat;

import appeng.api.stacks.*;

import aztech.modern_industrialization.machines.blockentities.AbstractCraftingMachineBlockEntity;
import aztech.modern_industrialization.machines.recipe.*;

import dev.aesi.api.*;
import dev.aesi.recipe.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.*;

/**
 * Deterministic multi-input item/fluid contracts, checked against the machine's real recipe tier.
 */
public final class ModernIndustrializationAdapter implements RecipeAdapter, MachineAdapter {
    public void read(ServerLevel level, List<Blueprint> output, Map<String, String> unsupported) {
        for (var registered : BuiltInRegistries.RECIPE_TYPE)
            if (registered instanceof MachineRecipeType type)
                for (var holder : type.getRecipesWithCache(level)) {
                    var recipe = holder.value();
                    String id = holder.id().identifier().toString();
                    try {
                        if (!recipe.conditions.isEmpty()
                                || recipe.itemInputs.stream().anyMatch(i -> i.probability() != 1)
                                || recipe.fluidInputs.stream().anyMatch(i -> i.probability() != 1)
                                || recipe.itemOutputs.stream().anyMatch(i -> i.probability() != 1)
                                || recipe.fluidOutputs.stream().anyMatch(i -> i.probability() != 1))
                            throw new IllegalArgumentException(
                                    "probability, reusable catalyst or process condition needs an"
                                        + " explicit machine contract");
                        var inputs = new ArrayList<List<GenericStack>>();
                        recipe.itemInputs.forEach(
                                i ->
                                        inputs.add(
                                                ProcessingRecipes.items(
                                                        i.ingredient(), i.amount())));
                        recipe.fluidInputs.forEach(
                                i -> inputs.add(ProcessingRecipes.fluids(i.fluid(), i.amount())));
                        var outputs = new ArrayList<GenericStack>();
                        recipe.itemOutputs.forEach(
                                o -> outputs.add(GenericStack.fromItemStack(o.getStack())));
                        recipe.fluidOutputs.forEach(
                                o ->
                                        outputs.add(
                                                GenericStack.fromFluidStack(
                                                        new FluidStack(o.fluid(), o.amount()))));
                        ProcessingRecipes.add(
                                level,
                                output,
                                id,
                                "mi/" + type.getId(),
                                recipe,
                                inputs,
                                v -> outputs,
                                recipe.duration,
                                recipe.eu);
                    } catch (IllegalArgumentException error) {
                        unsupported.put(id, "MI: " + error.getMessage());
                    }
                }
    }

    private static List<InputMatcher.Demand> demands(MachineRecipe recipe) {
        var result = new ArrayList<InputMatcher.Demand>();
        recipe.itemInputs.forEach(
                i ->
                        result.add(
                                new InputMatcher.Demand(
                                        k ->
                                                k instanceof AEItemKey item
                                                        && i.ingredient().test(item.toStack()),
                                        i.amount())));
        recipe.fluidInputs.forEach(
                i ->
                        result.add(
                                new InputMatcher.Demand(
                                        k ->
                                                k instanceof AEFluidKey fluid
                                                        && i.fluid().test(fluid.toStack(1)),
                                        i.amount())));
        return result;
    }

    public boolean supports(BlockEntity machine, Blueprint blueprint) {
        if (!(machine instanceof AbstractCraftingMachineBlockEntity m)
                || !(m.getLevel() instanceof ServerLevel level)
                || !blueprint.family().equals("mi/" + m.recipeType().getId())
                || blueprint.requiredEu() > m.getMaxRecipeEu()) return false;
        var original =
                m.recipeType()
                        .getRecipe(level, net.minecraft.resources.Identifier.parse(blueprint.id()));
        if (original == null
                || m.getCrafterComponent().getBehavior().banRecipe(original.value())
                || !VirtualPatterns.matchesCurrentRecipe(level, original.value(), blueprint))
            return false;
        var supplied = InputMatcher.supplied(blueprint.pattern());
        if (!InputMatcher.matches(supplied, demands(original.value()))) return false;
        for (var holder : m.recipeType().getRecipesWithCache(level)) {
            var other = holder.value();
            if (other != original.value()
                    && other.eu <= m.getMaxRecipeEu()
                    && !m.getCrafterComponent().getBehavior().banRecipe(other)
                    && InputMatcher.matches(supplied, demands(other))) return false;
        }
        return true;
    }

    public boolean ready(BlockEntity machine, Blueprint blueprint) {
        var m = (AbstractCraftingMachineBlockEntity) machine;
        if (!m.isEnabled() || m.getCrafterComponent().hasActiveRecipe()) return false;
        long required = Math.max(1, blueprint.requiredEu());
        if (m.consumeEu(required, aztech.modern_industrialization.util.Simulation.SIMULATE)
                < required) return false;
        var inventory = m.getCrafterComponent().getInventory();
        if (inventory.getItemInputs().stream().anyMatch(s -> !s.isEmpty())
                || inventory.getFluidInputs().stream().anyMatch(s -> !s.isEmpty())) return false;
        var used = new IdentityHashMap<Object, Long>();
        for (var output : blueprint.pattern().getOutputs()) {
            long remaining = output.amount();
            if (output.what() instanceof AEItemKey key)
                for (var slot : inventory.getItemOutputs()) {
                    if (!slot.isResourceAllowedByLock(key.toResource())
                            || !slot.isEmpty() && !slot.getResource().equals(key.toResource()))
                        continue;
                    long amount =
                            Math.min(
                                    remaining,
                                    Math.max(
                                            0,
                                            slot.getRemainingCapacityFor(key.toResource())
                                                    - used.getOrDefault(slot, 0L)));
                    used.merge(slot, amount, Long::sum);
                    remaining -= amount;
                }
            else if (output.what() instanceof AEFluidKey key)
                for (var tank : inventory.getFluidOutputs()) {
                    if (!tank.isResourceAllowedByLock(key.getFluid())
                            || !tank.isEmpty() && !tank.getVariant().equals(key.toResource()))
                        continue;
                    long amount =
                            Math.min(
                                    remaining,
                                    Math.max(
                                            0,
                                            tank.getRemainingSpace()
                                                    - used.getOrDefault(tank, 0L)));
                    used.merge(tank, amount, Long::sum);
                    remaining -= amount;
                }
            if (remaining > 0) return false;
        }
        return true;
    }

    public long queueTicks(BlockEntity machine, Blueprint recipe) {
        return ((AbstractCraftingMachineBlockEntity) machine)
                        .getCrafterComponent()
                        .hasActiveRecipe()
                ? recipe.batchTicks()
                : 0;
    }

    public boolean canRecover(BlockEntity machine, Blueprint recipe, AEKey key) {
        return machine instanceof AbstractCraftingMachineBlockEntity
                && (key instanceof AEItemKey || key instanceof AEFluidKey);
    }

    public List<dev.aesi.bridge.OutputRecovery.Port> outputs(BlockEntity machine) {
        if (!(machine instanceof AbstractCraftingMachineBlockEntity m)) return List.of();
        var ports = new ArrayList<dev.aesi.bridge.OutputRecovery.Port>();
        var inv = m.getCrafterComponent().getInventory();
        for (var slot : inv.getItemOutputs())
            if (!slot.isEmpty() && slot.canPipesExtract()) {
                var resource = slot.getResource();
                ports.add(
                        new dev.aesi.bridge.OutputRecovery.Port(
                                AEItemKey.of(resource),
                                slot.getAmount(),
                                (amount, tx) -> {
                                    if (!slot.getResource().equals(resource)) return 0;
                                    int n = Math.min(amount, slot.getAmount());
                                    slot.updateSnapshots(tx);
                                    slot.decrement(n);
                                    return n;
                                }));
            }
        for (var slot : inv.getFluidOutputs())
            if (!slot.isEmpty() && slot.canPipesExtract()) {
                var resource = slot.getResource();
                ports.add(
                        new dev.aesi.bridge.OutputRecovery.Port(
                                AEFluidKey.of(resource),
                                slot.getAmount(),
                                (amount, tx) -> {
                                    if (!slot.getResource().equals(resource)) return 0;
                                    int n = Math.min(amount, slot.getAmount());
                                    slot.updateSnapshots(tx);
                                    slot.decrement(n);
                                    return n;
                                }));
            }
        return ports;
    }
}

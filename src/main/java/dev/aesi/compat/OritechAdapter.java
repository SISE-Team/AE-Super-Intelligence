// SPDX-License-Identifier: MIT
package dev.aesi.compat;

import appeng.api.stacks.*;

import dev.aesi.api.*;
import dev.aesi.bridge.OutputRecovery;
import dev.aesi.recipe.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import rearth.oritech.api.transfer.fluid.InOutFluidStorage;
import rearth.oritech.block.base.entity.MachineBlockEntity;
import rearth.oritech.init.recipes.OritechRecipe;

import java.util.*;

/** Explicit base-machine API adapter; generators and world-dependent extractors are excluded. */
public final class OritechAdapter implements RecipeAdapter, MachineAdapter {
    private static final Set<String> MACHINES =
            Set.of(
                    "AssemblerBlockEntity",
                    "PulverizerBlockEntity",
                    "FoundryBlockEntity",
                    "RefineryBlockEntity",
                    "CentrifugeBlockEntity",
                    "IndustrialChillerBlockEntity",
                    "PoweredFurnaceBlockEntity");

    public void read(ServerLevel level, List<Blueprint> output, Map<String, String> unsupported) {
        for (var holder : level.getServer().getRecipeManager().getRecipes())
            if (holder.value() instanceof OritechRecipe recipe) {
                String id = holder.id().identifier().toString();
                try {
                    var inputs = new ArrayList<List<GenericStack>>();
                    recipe.itemInputs().forEach(i -> inputs.add(ProcessingRecipes.items(i, 1)));
                    recipe.fluidInput()
                            .ifPresent(
                                    i ->
                                            inputs.add(
                                                    ProcessingRecipes.fluids(
                                                            i.ingredient(), i.amount())));
                    var result = new ArrayList<GenericStack>();
                    recipe.itemResults()
                            .forEach(i -> result.add(GenericStack.fromItemStack(i.create())));
                    recipe.fluidOutputs()
                            .forEach(f -> result.add(GenericStack.fromFluidStack(f.create())));
                    ProcessingRecipes.add(
                            level,
                            output,
                            id,
                            "oritech/" + BuiltInRegistries.RECIPE_TYPE.getKey(recipe.recipeType()),
                            recipe,
                            inputs,
                            v -> result,
                            recipe.time(),
                            0);
                } catch (IllegalArgumentException error) {
                    unsupported.put(id, "Oritech: " + error.getMessage());
                }
            }
    }

    private static Object member(MachineBlockEntity m, String name) {
        try {
            var field = MachineBlockEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(m);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Oritech 2.0.0-exp6 API changed", error);
        }
    }

    private static Object type(MachineBlockEntity m) {
        try {
            var method = MachineBlockEntity.class.getDeclaredMethod("getOwnRecipeType");
            method.setAccessible(true);
            return method.invoke(m);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Oritech 2.0.0-exp6 recipe API changed", error);
        }
    }

    private static List<InOutFluidStorage> tanks(MachineBlockEntity m) {
        var result = new ArrayList<InOutFluidStorage>();
        for (Class<?> c = m.getClass(); c != null; c = c.getSuperclass())
            for (var field : c.getDeclaredFields())
                if (InOutFluidStorage.class.isAssignableFrom(field.getType()))
                    try {
                        field.setAccessible(true);
                        var tank = (InOutFluidStorage) field.get(m);
                        if (tank != null) result.add(tank);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Oritech fluid storage API changed", error);
                    }
        return result;
    }

    public boolean supports(BlockEntity machine, Blueprint recipe) {
        if (!(machine instanceof MachineBlockEntity m)
                || !MACHINES.contains(m.getClass().getSimpleName())) return false;
        if (!recipe.family()
                        .equals(
                                "oritech/"
                                        + BuiltInRegistries.RECIPE_TYPE.getKey(
                                                (net.minecraft.world.item.crafting.RecipeType<?>)
                                                        type(m)))
                || !(m.getLevel() instanceof ServerLevel level)) return false;
        var expected =
                level.getServer()
                        .getRecipeManager()
                        .byKey(
                                net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.RECIPE,
                                        net.minecraft.resources.Identifier.parse(recipe.id())))
                        .orElse(null);
        if (expected == null
                || !VirtualPatterns.matchesCurrentRecipe(level, expected.value(), recipe))
            return false;
        var supply = InputMatcher.supplied(recipe.pattern());
        for (var holder : level.getServer().getRecipeManager().getRecipes())
            if (holder.value() instanceof OritechRecipe other
                    && other.recipeType() == type(m)
                    && !holder.id().identifier().toString().equals(recipe.id())) {
                var demands = new ArrayList<InputMatcher.Demand>();
                other.itemInputs()
                        .forEach(
                                i ->
                                        demands.add(
                                                new InputMatcher.Demand(
                                                        k ->
                                                                k instanceof AEItemKey item
                                                                        && i.test(item.toStack()),
                                                        1)));
                other.fluidInput()
                        .ifPresent(
                                i ->
                                        demands.add(
                                                new InputMatcher.Demand(
                                                        k ->
                                                                k instanceof AEFluidKey fluid
                                                                        && i.ingredient()
                                                                                .test(
                                                                                        fluid
                                                                                                .toStack(
                                                                                                        1)),
                                                        i.amount())));
                if (InputMatcher.matches(supply, demands)) return false;
            }
        return true;
    }

    public boolean ready(BlockEntity machine, Blueprint recipe) {
        var m = (MachineBlockEntity) machine;
        if (!m.isAssembled(m.getBlockState())
                || m.isActivelyWorking()
                || Boolean.TRUE.equals(member(m, "disabledViaRedstone"))
                || m.energyStorage.getAmountAsLong() < m.getEnergyPerTick()) return false;
        var slots = m.getSlotAssignments();
        for (int i = 0; i < slots.inputCount(); i++)
            if (m.inventory.getAmountAsLong(slots.inputToRealSlot(i)) > 0) return false;
        return tanks(m).stream().allMatch(t -> t.getInStack().isEmpty());
    }

    public boolean canRecover(BlockEntity machine, Blueprint recipe, AEKey output) {
        return machine instanceof MachineBlockEntity m
                && MACHINES.contains(m.getClass().getSimpleName())
                && (output instanceof AEItemKey
                        || output instanceof AEFluidKey && !tanks(m).isEmpty());
    }

    public List<OutputRecovery.Port> outputs(BlockEntity machine) {
        if (!(machine instanceof MachineBlockEntity m)
                || !MACHINES.contains(m.getClass().getSimpleName())) return List.of();
        var ports =
                new ArrayList<>(
                        OutputRecovery.handler(m.inventory.getOutputContainer(), AEItemKey::of));
        for (var tank : tanks(m))
            ports.addAll(OutputRecovery.handler(tank.getOutputContainer(), AEFluidKey::of));
        return ports;
    }
}

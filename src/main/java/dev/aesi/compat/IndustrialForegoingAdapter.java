// SPDX-License-Identifier: MIT
package dev.aesi.compat;

import appeng.api.stacks.*;

import com.buuz135.industrial.block.core.tile.DissolutionChamberTile;
import com.buuz135.industrial.block.core.tile.LatexProcessingUnitTile;
import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.recipe.DissolutionChamberRecipe;

import dev.aesi.api.*;
import dev.aesi.bridge.OutputRecovery;
import dev.aesi.recipe.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public final class IndustrialForegoingAdapter implements RecipeAdapter, MachineAdapter {
    public void read(ServerLevel level, List<Blueprint> output, Map<String, String> unsupported) {
        for (var holder : level.getServer().getRecipeManager().getRecipes())
            if (holder.value() instanceof DissolutionChamberRecipe recipe) {
                String id = holder.id().identifier().toString();
                try {
                    var inputs = new ArrayList<List<GenericStack>>();
                    recipe.input.forEach(i -> inputs.add(ProcessingRecipes.items(i, 1)));
                    inputs.add(
                            ProcessingRecipes.fluids(
                                    recipe.inputFluid.ingredient(), recipe.inputFluid.amount()));
                    var outputs = new ArrayList<GenericStack>();
                    recipe.output().ifPresent(o -> outputs.add(GenericStack.fromItemStack(o)));
                    recipe.outputFluid()
                            .ifPresent(o -> outputs.add(GenericStack.fromFluidStack(o)));
                    ProcessingRecipes.add(
                            level,
                            output,
                            id,
                            "if/dissolution",
                            recipe,
                            inputs,
                            v -> outputs,
                            recipe.processingTime,
                            0);
                } catch (IllegalArgumentException error) {
                    unsupported.put(id, "Industrial Foregoing: " + error.getMessage());
                }
            }
        int latex = constant("AMOUNT_LATEX"), water = constant("AMOUNT_WATER");
        var declared = new net.minecraft.nbt.CompoundTag();
        declared.putString("process", "industrialforegoing/latex_processing");
        declared.putInt("latex", latex);
        declared.putInt("water", water);
        declared.putString("product", ModuleCore.DRY_RUBBER.getId().toString());
        var inputs =
                List.of(
                        ProcessingRecipes.fluids(
                                net.neoforged.neoforge.fluids.crafting.FluidIngredient.of(
                                        ModuleCore.LATEX.getSourceFluid().get()),
                                latex),
                        ProcessingRecipes.fluids(
                                net.neoforged.neoforge.fluids.crafting.FluidIngredient.of(
                                        net.minecraft.world.level.material.Fluids.WATER),
                                water));
        for (var variant : ProcessingRecipes.combinations(inputs)) {
            var pattern =
                    appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                            variant,
                            List.of(
                                    new GenericStack(
                                            AEItemKey.of(ModuleCore.DRY_RUBBER.get()), 1)));
            output.add(
                    new Blueprint(
                            "industrialforegoing:runtime/latex_processing",
                            "if/latex",
                            VirtualPatterns.createDeclared(
                                    level,
                                    "industrialforegoing:runtime/latex_processing",
                                    "if/latex",
                                    declared,
                                    pattern),
                            com.buuz135.industrial.config.machine.core.LatexProcessingUnitConfig
                                    .maxProgress,
                            false,
                            0));
        }
    }

    private static int constant(String name) {
        try {
            var field = LatexProcessingUnitTile.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Industrial Foregoing latex API changed", error);
        }
    }

    public boolean supports(BlockEntity machine, Blueprint recipe) {
        if (machine instanceof LatexProcessingUnitTile) return recipe.family().equals("if/latex");
        if (!(machine instanceof DissolutionChamberTile)
                || !recipe.family().equals("if/dissolution")
                || !(machine.getLevel() instanceof ServerLevel level)) return false;
        var holder =
                level.getServer()
                        .getRecipeManager()
                        .byKey(
                                net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.RECIPE,
                                        net.minecraft.resources.Identifier.parse(recipe.id())))
                        .orElse(null);
        if (holder == null || !VirtualPatterns.matchesCurrentRecipe(level, holder.value(), recipe))
            return false;
        var supply = InputMatcher.supplied(recipe.pattern());
        for (var other : level.getServer().getRecipeManager().getRecipes())
            if (other.value() instanceof DissolutionChamberRecipe candidate
                    && !other.id().equals(holder.id())) {
                var demands = new ArrayList<InputMatcher.Demand>();
                candidate.input.forEach(
                        i ->
                                demands.add(
                                        new InputMatcher.Demand(
                                                k ->
                                                        k instanceof AEItemKey item
                                                                && i.test(item.toStack()),
                                                1)));
                demands.add(
                        new InputMatcher.Demand(
                                k ->
                                        k instanceof AEFluidKey fluid
                                                && candidate
                                                        .inputFluid
                                                        .ingredient()
                                                        .test(fluid.toStack(1)),
                                candidate.inputFluid.amount()));
                if (InputMatcher.matches(supply, demands)) return false;
            }
        return true;
    }

    public boolean ready(BlockEntity machine, Blueprint recipe) {
        var m = (com.buuz135.industrial.block.tile.IndustrialProcessingTile<?>) machine;
        if (m.isPaused() || m.isActive() || m.getEnergyStorage().getEnergyStored() == 0)
            return false;
        for (var handler : m.getMultiInventoryComponent().getInventoryHandlers())
            if (handler.getName().equals("input"))
                for (int i = 0; i < handler.size(); i++)
                    if (handler.getAmountAsLong(i) > 0) return false;
        for (var tank : m.getMultiTankComponent().getTanks())
            if (!tank.getName().equals("output_fluid") && tank.getFluidAmount() > 0) return false;
        return true;
    }

    public boolean canRecover(BlockEntity machine, Blueprint recipe, AEKey key) {
        return (machine instanceof DissolutionChamberTile
                        || machine instanceof LatexProcessingUnitTile)
                && (key instanceof AEItemKey || key instanceof AEFluidKey);
    }

    public List<OutputRecovery.Port> outputs(BlockEntity machine) {
        if (!(machine instanceof DissolutionChamberTile
                || machine instanceof LatexProcessingUnitTile)) return List.of();
        var m = (com.buuz135.industrial.block.tile.IndustrialProcessingTile<?>) machine;
        var ports = new ArrayList<OutputRecovery.Port>();
        for (var handler : m.getMultiInventoryComponent().getInventoryHandlers())
            if (handler.getName().equals("output"))
                ports.addAll(OutputRecovery.handler(handler, AEItemKey::of));
        for (var tank : m.getMultiTankComponent().getTanks())
            if (tank.getName().equals("output_fluid"))
                ports.addAll(OutputRecovery.handler(tank, AEFluidKey::of));
        return ports;
    }
}

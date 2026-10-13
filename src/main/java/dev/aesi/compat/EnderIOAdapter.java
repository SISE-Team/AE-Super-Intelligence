// SPDX-License-Identifier: MIT
package dev.aesi.compat;

import appeng.api.stacks.*;

import com.enderio.enderio.content.machines.alloy.*;
import com.enderio.enderio.content.machines.sag_mill.*;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;

import dev.aesi.api.*;
import dev.aesi.bridge.OutputRecovery;
import dev.aesi.recipe.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public final class EnderIOAdapter implements RecipeAdapter, MachineAdapter {
    public void read(ServerLevel level, List<Blueprint> output, Map<String, String> unsupported) {
        for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            String id = holder.id().identifier().toString();
            try {
                if (holder.value() instanceof AlloySmeltingRecipe recipe) {
                    var inputs =
                            recipe.inputs().stream()
                                    .map(i -> ProcessingRecipes.items(i.ingredient(), i.count()))
                                    .toList();
                    ProcessingRecipes.add(
                            level,
                            output,
                            id,
                            "eio/alloy",
                            recipe,
                            inputs,
                            v -> List.of(GenericStack.fromItemStack(recipe.output().create())),
                            200,
                            0);
                } else if (holder.value() instanceof SagMillingRecipe recipe) {
                    if (recipe.outputs().stream()
                            .anyMatch(
                                    o ->
                                            o.chance() != 1
                                                    || o.isOptional()
                                                    || o.getItemStackTemplate().isEmpty()))
                        throw new IllegalArgumentException("random or optional SAG mill output");
                    var outputs =
                            recipe.outputs().stream()
                                    .map(
                                            o ->
                                                    GenericStack.fromItemStack(
                                                            o.getItemStackTemplate()
                                                                    .orElseThrow()
                                                                    .create()))
                                    .toList();
                    ProcessingRecipes.add(
                            level,
                            output,
                            id,
                            "eio/sag",
                            recipe,
                            List.of(ProcessingRecipes.items(recipe.input(), 1)),
                            v -> outputs,
                            200,
                            0);
                }
            } catch (IllegalArgumentException error) {
                unsupported.put(id, "Ender IO: " + error.getMessage());
            }
        }
    }

    public boolean supports(BlockEntity machine, Blueprint recipe) {
        if (!(machine.getLevel() instanceof ServerLevel level)) return false;
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
        if (machine instanceof AlloySmelterBlockEntity m
                && recipe.family().equals("eio/alloy")
                && holder.value() instanceof AlloySmeltingRecipe expected) {
            if (!(expected.isSmelting() ? m.getMode().canSmelt() : m.getMode().canAlloy()))
                return false;
            var supply = InputMatcher.supplied(recipe.pattern());
            if (!expected.isSmelting() && supply.size() != expected.inputs().size()) return false;
            for (var other : level.getServer().getRecipeManager().getRecipes())
                if (other.value() instanceof AlloySmeltingRecipe candidate
                        && !other.id().equals(holder.id())
                        && (candidate.isSmelting()
                                ? m.getMode().canSmelt()
                                : m.getMode().canAlloy())
                        && InputMatcher.matches(
                                supply,
                                candidate.inputs().stream()
                                        .map(
                                                i ->
                                                        new InputMatcher.Demand(
                                                                k ->
                                                                        k instanceof AEItemKey item
                                                                                && i.ingredient()
                                                                                        .test(
                                                                                                item
                                                                                                        .toStack()),
                                                                i.count()))
                                        .toList())) return false;
            return true;
        }
        if (machine instanceof SagMillBlockEntity && recipe.family().equals("eio/sag")) {
            var supply = InputMatcher.supplied(recipe.pattern());
            for (var other : level.getServer().getRecipeManager().getRecipes())
                if (other.value() instanceof SagMillingRecipe candidate
                        && !other.id().equals(holder.id())
                        && InputMatcher.matches(
                                supply,
                                List.of(
                                        new InputMatcher.Demand(
                                                k ->
                                                        k instanceof AEItemKey item
                                                                && candidate
                                                                        .input()
                                                                        .test(item.toStack()),
                                                1)))) return false;
        }
        return machine instanceof SagMillBlockEntity sag
                && recipe.family().equals("eio/sag")
                && sag.getGrindingBallData()
                        .equals(com.enderio.enderio.api.components.GrindingBallData.IDENTITY);
    }

    public boolean ready(BlockEntity machine, Blueprint recipe) {
        var m = (PoweredMachineBlockEntity) machine;
        if (m.isActive() || !m.hasEnergy() || !m.canAct()) return false;
        var inventory = m.getInventory();
        if (m instanceof AlloySmelterBlockEntity)
            return inventory.getStacks(AlloySmelterBlockEntity.INPUTS).stream()
                    .allMatch(net.minecraft.world.item.ItemStack::isEmpty);
        return inventory.getStack(SagMillBlockEntity.INPUT).isEmpty();
    }

    public boolean canRecover(BlockEntity machine, Blueprint recipe, AEKey output) {
        return output instanceof AEItemKey
                && (machine instanceof AlloySmelterBlockEntity
                        || machine instanceof SagMillBlockEntity);
    }

    public List<OutputRecovery.Port> outputs(BlockEntity machine) {
        if (!(machine instanceof AlloySmelterBlockEntity || machine instanceof SagMillBlockEntity))
            return List.of();
        var inventory = ((PoweredMachineBlockEntity) machine).getInventory();
        var result = new ArrayList<OutputRecovery.Port>();
        var layout = inventory.layout();
        int count = machine instanceof SagMillBlockEntity ? 4 : 1;
        for (int i = 0; i < count; i++) {
            int index =
                    machine instanceof SagMillBlockEntity
                            ? layout.indexOf(SagMillBlockEntity.OUTPUT, i)
                            : layout.indexOf(AlloySmelterBlockEntity.OUTPUT);
            var resource = inventory.getResource(index);
            if (!resource.isEmpty())
                result.add(
                        new OutputRecovery.Port(
                                AEItemKey.of(resource),
                                inventory.getAmountAsLong(index),
                                (amount, tx) -> inventory.extract(index, resource, amount, tx)));
        }
        return result;
    }
}

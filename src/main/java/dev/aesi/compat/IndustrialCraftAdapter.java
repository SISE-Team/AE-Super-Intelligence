// SPDX-License-Identifier: MIT
package dev.aesi.compat;

import appeng.api.stacks.*;

import dev.aesi.api.*;
import dev.aesi.bridge.OutputRecovery;
import dev.aesi.recipe.*;

import ic2.api.recipe.IRecipeInput;
import ic2.core.block.machine.tileentity.*;
import ic2.core.ref.Ic2RecipeTypes;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.*;

public final class IndustrialCraftAdapter implements RecipeAdapter, MachineAdapter {
    private static final Set<RecipeType<?>> TYPES =
            Set.of(
                    Ic2RecipeTypes.MACERATOR,
                    Ic2RecipeTypes.EXTRACTOR,
                    Ic2RecipeTypes.COMPRESSOR,
                    Ic2RecipeTypes.CENTRIFUGE,
                    Ic2RecipeTypes.ORE_WASHER,
                    Ic2RecipeTypes.METAL_FORMER_CUTTING,
                    Ic2RecipeTypes.METAL_FORMER_EXTRUDING,
                    Ic2RecipeTypes.METAL_FORMER_ROLLING);

    public void read(ServerLevel level, List<Blueprint> output, Map<String, String> unsupported) {
        for (var holder : level.getServer().getRecipeManager().getRecipes())
            if (holder.value() instanceof ic2.core.recipe.v2.RecipeHolder<?, ?> wrapper) {
                String id = holder.id().identifier().toString();
                try {
                    if (wrapper.recipe() instanceof ic2.api.recipe.MachineRecipeWeighted<?>)
                        throw new IllegalArgumentException("weighted random output");
                    if (!TYPES.contains(wrapper.type())
                            || !(wrapper.recipe().getInput() instanceof IRecipeInput input)
                            || !(wrapper.recipe().getOutput() instanceof Collection<?> outputs))
                        throw new IllegalArgumentException(
                                "special machine mode, catalyst, probability or non-item result");
                    var choices =
                            java.util.stream.Stream.concat(
                                            input.getInputs().stream()
                                                    .filter(input::matches)
                                                    .map(
                                                            s ->
                                                                    new GenericStack(
                                                                            AEItemKey.of(s),
                                                                            input.getAmount())),
                                            ComponentInputs.items(input::matches, input.getAmount())
                                                    .stream())
                                    .distinct()
                                    .toList();
                    var ingredients = new ArrayList<List<GenericStack>>();
                    ingredients.add(choices);
                    if (wrapper.type() == Ic2RecipeTypes.ORE_WASHER) {
                        var meta = wrapper.recipe().getMetaData();
                        if (meta == null || meta.getIntOr("amount", 0) <= 0)
                            throw new IllegalArgumentException("missing water demand");
                        ingredients.add(
                                List.of(
                                        new GenericStack(
                                                AEFluidKey.of(Fluids.WATER),
                                                meta.getIntOr("amount", 0))));
                    }
                    var result =
                            outputs.stream()
                                    .map(o -> GenericStack.fromItemStack((ItemStack) o))
                                    .toList();
                    ProcessingRecipes.add(
                            level,
                            output,
                            id,
                            "ic2/" + BuiltInRegistries.RECIPE_TYPE.getKey(wrapper.type()),
                            wrapper,
                            ingredients,
                            v -> result,
                            300,
                            0);
                } catch (IllegalArgumentException error) {
                    unsupported.put(id, "IC2: " + error.getMessage());
                }
            }
    }

    private static RecipeType<?> type(BlockEntity machine) {
        if (machine instanceof TileEntityMacerator) return Ic2RecipeTypes.MACERATOR;
        if (machine instanceof TileEntityExtractor) return Ic2RecipeTypes.EXTRACTOR;
        if (machine instanceof TileEntityCompressor) return Ic2RecipeTypes.COMPRESSOR;
        if (machine instanceof TileEntityCentrifuge) return Ic2RecipeTypes.CENTRIFUGE;
        if (machine instanceof TileEntityOreWashing) return Ic2RecipeTypes.ORE_WASHER;
        if (machine instanceof TileEntityMetalFormer m)
            return switch (m.getMode()) {
                case 0 -> Ic2RecipeTypes.METAL_FORMER_EXTRUDING;
                case 1 -> Ic2RecipeTypes.METAL_FORMER_ROLLING;
                case 2 -> Ic2RecipeTypes.METAL_FORMER_CUTTING;
                default -> null;
            };
        return null;
    }

    public boolean supports(BlockEntity machine, Blueprint blueprint) {
        var type = type(machine);
        if (type == null
                || !blueprint.family().equals("ic2/" + BuiltInRegistries.RECIPE_TYPE.getKey(type))
                || !(machine.getLevel() instanceof ServerLevel level)) return false;
        var supply = InputMatcher.supplied(blueprint.pattern());
        var expected =
                level.getServer()
                        .getRecipeManager()
                        .byKey(
                                net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.RECIPE,
                                        net.minecraft.resources.Identifier.parse(blueprint.id())))
                        .orElse(null);
        if (expected == null
                || !VirtualPatterns.matchesCurrentRecipe(level, expected.value(), blueprint))
            return false;
        for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            if (holder.id().identifier().toString().equals(blueprint.id())
                    || holder.value().getType() != type) continue;
            if (holder.value() instanceof ic2.core.recipe.v2.RecipeHolder<?, ?> r
                    && r.recipe().getInput() instanceof IRecipeInput input
                    && InputMatcher.matches(
                            supply,
                            List.of(
                                    new InputMatcher.Demand(
                                            k ->
                                                    k instanceof AEItemKey item
                                                            && input.matches(item.toStack()),
                                            input.getAmount())))) return false;
        }
        return true;
    }

    public boolean ready(BlockEntity machine, Blueprint blueprint) {
        var m = (TileEntityStandardMachine<?, ?, ?>) machine;
        if (m.inputSlot == null
                || !m.inputSlot.isEmpty()
                || !m.getComponent(ic2.core.block.comp.Energy.class).canUseEnergy(m.energyConsume))
            return false;
        if (m instanceof TileEntityCentrifuge centrifuge) {
            var original =
                    ((ic2.core.recipe.v2.RecipeHolder<?, ?>)
                                    ((ServerLevel) m.getLevel())
                                            .getServer()
                                            .getRecipeManager()
                                            .byKey(
                                                    net.minecraft.resources.ResourceKey.create(
                                                            net.minecraft.core.registries.Registries
                                                                    .RECIPE,
                                                            net.minecraft.resources.Identifier
                                                                    .parse(blueprint.id())))
                                            .orElseThrow()
                                            .value())
                            .recipe();
            if (original.getMetaData() == null
                    || original.getMetaData().getIntOr("minHeat", 0) > TileEntityCentrifuge.maxHeat)
                return false;
        }
        var items =
                blueprint.pattern().getOutputs().stream()
                        .filter(o -> o.what() instanceof AEItemKey)
                        .map(o -> ((AEItemKey) o.what()).toStack((int) o.amount()))
                        .toList();
        return m.outputSlot.canAdd(items);
    }

    public boolean canRecover(BlockEntity machine, Blueprint recipe, AEKey key) {
        return type(machine) != null && key instanceof AEItemKey;
    }

    public List<OutputRecovery.Port> outputs(BlockEntity machine) {
        if (type(machine) == null || !(machine instanceof TileEntityStandardMachine<?, ?, ?> m))
            return List.of();
        var handler =
                m.getLevel()
                        .getCapability(Capabilities.Item.BLOCK, m.getBlockPos(), Direction.DOWN);
        if (handler == null) return List.of();
        var ports = new ArrayList<OutputRecovery.Port>();
        for (int i = 0; i < m.getContainerSize(); i++) {
            var sample = m.getItem(i);
            int index = i;
            if (sample.isEmpty()) continue;
            boolean output = false;
            for (var item : m.outputSlot) if (item == sample) output = true;
            if (!output) continue;
            var resource = handler.getResource(i);
            ports.add(
                    new OutputRecovery.Port(
                            AEItemKey.of(resource),
                            handler.getAmountAsLong(i),
                            (amount, tx) -> handler.extract(index, resource, amount, tx)));
        }
        return ports;
    }
}

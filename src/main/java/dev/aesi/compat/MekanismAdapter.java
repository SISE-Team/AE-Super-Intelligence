// SPDX-License-Identifier: MIT
package dev.aesi.compat;

import appeng.api.stacks.*;

import dev.aesi.api.*;
import dev.aesi.bridge.OutputRecovery;
import dev.aesi.recipe.*;

import me.ramidzkh.mekae2.ae2.MekanismKey;

import mekanism.api.AutomationType;
import mekanism.api.recipes.*;
import mekanism.api.recipes.ingredients.*;
import mekanism.api.resource.IResourceContainer;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.recipe.lookup.IRecipeLookupHandler;
import mekanism.common.tile.component.config.*;
import mekanism.common.tile.component.config.slot.*;
import mekanism.common.tile.prefab.*;

import net.minecraft.core.TypedInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.resource.Resource;

import java.util.*;

/**
 * Uses Mekanism's real recipe classes and output-slot declarations, including inherited addon
 * machines.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class MekanismAdapter implements RecipeAdapter, MachineAdapter {
    private static GenericStack stack(Object instance) {
        return switch (instance) {
            case net.minecraft.world.item.ItemStack s -> GenericStack.fromItemStack(s);
            case net.neoforged.neoforge.fluids.FluidStack s -> GenericStack.fromFluidStack(s);
            case mekanism.api.chemical.ChemicalStack s ->
                    new GenericStack(Objects.requireNonNull(MekanismKey.of(s)), s.amount());
            default ->
                    throw new IllegalArgumentException(
                            "unsupported input representation: "
                                    + instance.getClass().getSimpleName());
        };
    }

    private static TypedInstance instance(GenericStack s) {
        if (s.amount() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("batch amount exceeds machine API");
        return switch (s.what()) {
            case AEItemKey item -> item.toStack((int) s.amount());
            case AEFluidKey fluid -> fluid.toStack((int) s.amount());
            case MekanismKey chemical -> chemical.withAmount(s.amount());
            default -> throw new IllegalArgumentException("unknown resource");
        };
    }

    private static List<GenericStack> output(Object result) {
        return switch (result) {
            case ItemStackTemplate item -> List.of(GenericStack.fromItemStack(item.create()));
            case FluidStackTemplate fluid -> List.of(GenericStack.fromFluidStack(fluid.create()));
            case mekanism.api.chemical.ChemicalStackTemplate chemical ->
                    List.of(stack(chemical.create()));
            case ElectrolysisRecipe.ElectrolysisRecipeOutput split ->
                    List.of(stack(split.left().create()), stack(split.right().create()));
            case PressurizedReactionRecipe.PressurizedReactionRecipeOutput reaction -> {
                var list = new ArrayList<GenericStack>();
                if (reaction.item() != null) list.add(stack(reaction.item().create()));
                if (reaction.chemical() != null) list.add(stack(reaction.chemical().create()));
                yield list;
            }
            case ItemStackToFluidOptionalItemRecipe.FluidOptionalItemOutput liquid -> {
                var list = new ArrayList<GenericStack>();
                list.add(stack(liquid.fluid().create()));
                if (liquid.optionalItem() != null) list.add(stack(liquid.optionalItem().create()));
                yield list;
            }
            default ->
                    throw new IllegalArgumentException("probabilistic or unknown output contract");
        };
    }

    private static List<InputIngredient> ingredients(MekanismRecipe recipe) {
        if (recipe instanceof SingleInputRecipe one) return List.of(one.getInput());
        if (recipe instanceof TwoInputMekRecipe two)
            return List.of(two.getInputA(), two.getInputB());
        if (recipe instanceof PressurizedReactionRecipe three)
            return List.of(three.getInputSolid(), three.getInputFluid(), three.getInputChemical());
        throw new IllegalArgumentException(
                "world condition, energy-only, rotary mode or specialized process");
    }

    private static Object result(MekanismRecipe recipe, List<GenericStack> inputs) {
        if (recipe instanceof SingleInputRecipe one) return one.getOutput(instance(inputs.get(0)));
        if (recipe instanceof TwoInputMekRecipe two)
            return two.getOutput(instance(inputs.get(0)), instance(inputs.get(1)));
        var three = (PressurizedReactionRecipe) recipe;
        return three.getOutput(
                (net.minecraft.world.item.ItemStack) instance(inputs.get(0)),
                (net.neoforged.neoforge.fluids.FluidStack) instance(inputs.get(1)),
                (mekanism.api.chemical.ChemicalStack) instance(inputs.get(2)));
    }

    public void read(ServerLevel level, List<Blueprint> output, Map<String, String> unsupported) {
        var context = SlotDisplayContext.fromLevel(level);
        for (var holder : level.getServer().getRecipeManager().getRecipes())
            if (holder.value() instanceof MekanismRecipe recipe) {
                String id = holder.id().identifier().toString();
                try {
                    if (recipe instanceof ItemStackChemicalToObjectRecipe chemical
                            && chemical.perTickUsage())
                        throw new IllegalArgumentException(
                                "per-tick chemical demand depends on speed/usage upgrades");
                    if (recipe instanceof SawmillRecipe)
                        throw new IllegalArgumentException("random secondary output");
                    var alternatives = new ArrayList<List<GenericStack>>();
                    for (var ingredient : ingredients(recipe)) {
                        if (ingredient instanceof ItemStackIngredient item) {
                            alternatives.add(
                                    ProcessingRecipes.items(
                                            item.ingredient().ingredient(),
                                            item.ingredient().count()));
                            continue;
                        }
                        if (ingredient instanceof FluidStackIngredient fluid) {
                            alternatives.add(
                                    ProcessingRecipes.fluids(
                                            fluid.ingredient().ingredient(),
                                            fluid.ingredient().amount()));
                            continue;
                        }
                        var reps = (List<?>) ingredient.getRepresentations(context);
                        var choices =
                                reps.stream()
                                        .filter(s -> ingredient.test((TypedInstance) s))
                                        .map(MekanismAdapter::stack)
                                        .distinct()
                                        .toList();
                        alternatives.add(choices);
                    }
                    long ticks =
                            recipe instanceof PressurizedReactionRecipe r ? r.getDuration() : 200;
                    ProcessingRecipes.add(
                            level,
                            output,
                            id,
                            "mek/" + BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()),
                            recipe,
                            alternatives,
                            v -> output(result(recipe, v)),
                            ticks,
                            0);
                } catch (IllegalArgumentException error) {
                    unsupported.put(id, "Mekanism: " + error.getMessage());
                }
            }
    }

    public boolean supports(BlockEntity entity, Blueprint blueprint) {
        if (!(entity instanceof TileEntityConfigurableMachine m)
                || !(m instanceof IRecipeLookupHandler<?> lookup)
                || !blueprint.family().equals("mek/" + lookup.getRecipeType().getRegistryName()))
            return false;
        // A conflicting recipe that can consume the same supplied batch must never be dispatched.
        var supplied = InputMatcher.supplied(blueprint.pattern());
        var original =
                lookup.getRecipeType().getRecipes(m.getLevel()).stream()
                        .filter(h -> h.id().identifier().toString().equals(blueprint.id()))
                        .findFirst()
                        .orElse(null);
        if (original == null
                || !VirtualPatterns.matchesCurrentRecipe(
                        (ServerLevel) m.getLevel(), original.value(), blueprint)) return false;
        candidates:
        for (var holder : lookup.getRecipeType().getRecipes(m.getLevel())) {
            if (holder.id().identifier().toString().equals(blueprint.id())) continue;
            try {
                var demands = new ArrayList<InputMatcher.Demand>();
                for (var ingredient : ingredients(holder.value())) {
                    // Mek sized ingredients have one fixed amount across their accepted variants.
                    var representations =
                            ingredient.getRepresentations(
                                    SlotDisplayContext.fromLevel(m.getLevel()));
                    if (representations.isEmpty()) continue candidates;
                    var sample = representations.getFirst();
                    int amount = ingredient.getNeededAmount((TypedInstance) sample);
                    demands.add(new InputMatcher.Demand(k -> accepts(ingredient, k), amount));
                }
                if (InputMatcher.matches(supplied, demands)) return false;
            } catch (IllegalArgumentException error) {
                return false;
            }
        }
        return true;
    }

    private static boolean accepts(InputIngredient ingredient, AEKey key) {
        if (ingredient instanceof ItemStackIngredient && !(key instanceof AEItemKey)
                || ingredient instanceof FluidStackIngredient && !(key instanceof AEFluidKey)
                || ingredient instanceof ChemicalStackIngredient && !(key instanceof MekanismKey))
            return false;
        return ingredient.testType(instance(new GenericStack(key, 1)));
    }

    public boolean ready(BlockEntity entity, Blueprint blueprint) {
        var m = (TileEntityConfigurableMachine) entity;
        if (!m.canFunction()
                || m.getActive()
                || m.getEnergyContainer() != null && m.getEnergyContainer().isEmpty()) return false;
        for (var type :
                List.of(TransmissionType.ITEM, TransmissionType.FLUID, TransmissionType.CHEMICAL)) {
            var config = m.getConfig().getConfig(type);
            if (config == null) continue;
            for (var data :
                    List.of(DataType.INPUT, DataType.INPUT_1, DataType.INPUT_2, DataType.EXTRA)) {
                var info = config.getSlotInfo(data);
                if (info != null && !info.isEmpty()) return false;
            }
        }
        return true;
    }

    public boolean canRecover(BlockEntity machine, Blueprint recipe, AEKey output) {
        if (!(machine instanceof TileEntityConfigurableMachine m)) return false;
        var type =
                output instanceof AEItemKey
                        ? TransmissionType.ITEM
                        : output instanceof AEFluidKey
                                ? TransmissionType.FLUID
                                : TransmissionType.CHEMICAL;
        var config = m.getConfig().getConfig(type);
        if (config == null) return false;
        for (var typeData : List.of(DataType.OUTPUT, DataType.OUTPUT_1, DataType.OUTPUT_2)) {
            switch (config.getSlotInfo(typeData)) {
                case InventorySlotInfo slots -> {
                    if (slots.getSlots().stream().anyMatch(s -> outputOnly(config, s))) return true;
                }
                case FluidSlotInfo tanks -> {
                    if (tanks.getTanks().stream().anyMatch(t -> outputOnly(config, t))) return true;
                }
                case ChemicalSlotInfo tanks -> {
                    if (tanks.getTanks().stream().anyMatch(t -> outputOnly(config, t))) return true;
                }
                case null, default -> {}
            }
        }
        return false;
    }

    private static boolean outputOnly(ConfigInfo config, Object container) {
        return config.getDataTypeForContainer(container).stream()
                .noneMatch(
                        t ->
                                t == DataType.INPUT
                                        || t == DataType.INPUT_1
                                        || t == DataType.INPUT_2
                                        || t == DataType.EXTRA
                                        || t == DataType.ENERGY);
    }

    private static <R extends Resource> void port(
            List<OutputRecovery.Port> ports,
            IResourceContainer<R> container,
            java.util.function.Function<R, AEKey> key) {
        if (container.isEmpty()) return;
        var resource = container.resource();
        ports.add(
                new OutputRecovery.Port(
                        key.apply(resource),
                        container.amountAsLong(),
                        (amount, tx) ->
                                container.extract(resource, amount, tx, AutomationType.EXTERNAL)));
    }

    public List<OutputRecovery.Port> outputs(BlockEntity entity) {
        if (!(entity instanceof TileEntityConfigurableMachine m)
                || !(m instanceof IRecipeLookupHandler)) return List.of();
        var ports = new ArrayList<OutputRecovery.Port>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (var transmission :
                List.of(TransmissionType.ITEM, TransmissionType.FLUID, TransmissionType.CHEMICAL)) {
            var config = m.getConfig().getConfig(transmission);
            if (config == null) continue;
            for (var data : List.of(DataType.OUTPUT, DataType.OUTPUT_1, DataType.OUTPUT_2)) {
                switch (config.getSlotInfo(data)) {
                    case InventorySlotInfo slots -> {
                        for (var slot : slots.getSlots())
                            if (outputOnly(config, slot) && seen.add(slot))
                                port(ports, slot, AEItemKey::of);
                    }
                    case FluidSlotInfo tanks -> {
                        for (var tank : tanks.getTanks())
                            if (outputOnly(config, tank) && seen.add(tank))
                                port(ports, tank, AEFluidKey::of);
                    }
                    case ChemicalSlotInfo tanks -> {
                        for (var tank : tanks.getTanks())
                            if (outputOnly(config, tank) && seen.add(tank))
                                port(ports, tank, MekanismKey::of);
                    }
                    case null, default -> {}
                }
            }
        }
        return ports;
    }

    public long queueTicks(BlockEntity machine, Blueprint recipe) {
        return ((TileEntityConfigurableMachine) machine).getActive() ? recipe.batchTicks() : 0;
    }
}

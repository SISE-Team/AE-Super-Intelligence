// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.config.LockCraftingMode;
import appeng.api.networking.*;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.helpers.patternprovider.*;
import appeng.parts.AEBasePart;
import appeng.parts.automation.ExportBusPart;
import appeng.parts.automation.ImportBusPart;
import appeng.parts.misc.InterfacePart;

import dev.aesi.api.MachineAdapter;
import dev.aesi.bridge.*;
import dev.aesi.planner.Model.Slot;
import dev.aesi.recipe.*;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.*;

import java.util.*;

public final class MachineRegistry {
    public record Endpoint(
            String identity,
            ProviderAccess provider,
            IGridNode node,
            ServerLevel level,
            BlockPos machine,
            Direction direction) {
        public BlockEntity entity() {
            return level.hasChunkAt(machine) ? level.getBlockEntity(machine) : null;
        }
    }

    private final List<Endpoint> endpoints;
    private final Map<String, List<IGridNode>> returning = new HashMap<>();
    private final List<MachineAdapter> adapters = new ArrayList<>();

    public MachineRegistry(IGrid grid) {
        var discovered = new ArrayList<Endpoint>();
        var seen = new HashSet<String>();
        for (var node : grid.getNodes()) {
            if (!node.isActive()) continue;
            var owner = node.getOwner();
            if (owner instanceof ImportBusPart bus && bus.getLevel() instanceof ServerLevel level) {
                String machine =
                        level.dimension().identifier()
                                + "/"
                                + bus.getBlockEntity()
                                        .getBlockPos()
                                        .relative(bus.getSide())
                                        .asLong();
                returning.computeIfAbsent(machine, k -> new ArrayList<>()).add(node);
            }
            if (owner instanceof InterfaceBlockEntity be
                    && be.getLevel() instanceof ServerLevel level) {
                for (var side : Direction.values())
                    addDirect(discovered, seen, node, level, be.getBlockPos(), side);
            } else if (owner instanceof AEBasePart part
                    && (owner instanceof InterfacePart || owner instanceof ExportBusPart)
                    && part.getLevel() instanceof ServerLevel level) {
                addDirect(
                        discovered,
                        seen,
                        node,
                        level,
                        part.getBlockEntity().getBlockPos(),
                        part.getSide());
            }
            if (owner instanceof SmartController) continue;
            var service = node.getService(appeng.api.networking.crafting.ICraftingProvider.class);
            if (!(service instanceof ProviderAccess access)) continue;
            BlockEntity be =
                    owner instanceof PatternProviderLogicHost host
                            ? host.getBlockEntity()
                            : owner instanceof AEBasePart part
                                    ? part.getBlockEntity()
                                    : owner instanceof BlockEntity entity ? entity : null;
            if (be == null || !(be.getLevel() instanceof ServerLevel level)) continue;
            for (var side : access.aesiActiveSides()) {
                var pos = be.getBlockPos().relative(side);
                if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) == null) continue;
                String id = level.dimension().identifier() + "/" + pos.asLong();
                // Keep alternative connection endpoints but deduplicate capacity in slots().
                String endpoint = id + "/" + be.getBlockPos().asLong() + "/" + side;
                if (seen.add(endpoint))
                    discovered.add(new Endpoint(id, access, node, level, pos, side));
            }
        }
        discovered.sort(
                Comparator.comparing(Endpoint::identity)
                        .thenComparing(e -> e.direction().ordinal()));
        endpoints = List.copyOf(discovered);
        adapters.add(new VanillaMachines());
        if (net.neoforged.fml.ModList.get().isLoaded("modern_industrialization"))
            adapters.add(new dev.aesi.compat.ModernIndustrializationAdapter());
        if (net.neoforged.fml.ModList.get().isLoaded("appmek"))
            adapters.add(new dev.aesi.compat.MekanismAdapter());
        if (net.neoforged.fml.ModList.get().isLoaded("ic2"))
            adapters.add(new dev.aesi.compat.IndustrialCraftAdapter());
        if (net.neoforged.fml.ModList.get().isLoaded("industrialforegoing"))
            adapters.add(new dev.aesi.compat.IndustrialForegoingAdapter());
        if (net.neoforged.fml.ModList.get().isLoaded("enderio"))
            adapters.add(new dev.aesi.compat.EnderIOAdapter());
        if (net.neoforged.fml.ModList.get().isLoaded("oritech"))
            adapters.add(new dev.aesi.compat.OritechAdapter());
        ServiceLoader.load(MachineAdapter.class).forEach(adapters::add);
    }

    private static void addDirect(
            List<Endpoint> entries,
            Set<String> seen,
            IGridNode node,
            ServerLevel level,
            BlockPos source,
            Direction side) {
        var pos = source.relative(side);
        if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) == null) return;
        String id = level.dimension().identifier() + "/" + pos.asLong(),
                endpoint = id + "/" + source.asLong() + "/" + side;
        if (seen.add(endpoint)) entries.add(new Endpoint(id, null, node, level, pos, side));
    }

    private boolean controllable(Endpoint endpoint, Blueprint recipe) {
        if (!recipe.pattern().supportsPushInputsToExternalInventory())
            return endpoint.provider() != null;
        var outputs = returning.getOrDefault(endpoint.identity(), List.of());
        for (var output : recipe.pattern().getOutputs())
            if (outputs.stream()
                            .noneMatch(
                                    n ->
                                            n.isActive()
                                                    && n.getGrid() == endpoint.node().getGrid()
                                                    && n.getOwner() instanceof ImportBusPart bus
                                                    && (bus.getConfig().keySet().isEmpty()
                                                            || bus.getConfig()
                                                                    .keySet()
                                                                    .contains(output.what())))
                    && adapters.stream()
                            .noneMatch(
                                    a ->
                                            a.supports(endpoint.entity(), recipe)
                                                    && a.canRecover(
                                                            endpoint.entity(),
                                                            recipe,
                                                            output.what()))) return false;
        if (endpoint.node().getOwner() instanceof ExportBusPart bus) {
            if (bus.getRSMode() == appeng.api.config.RedstoneMode.SIGNAL_PULSE) return false;
            if (!bus.getConfig().keySet().isEmpty())
                for (var input : recipe.pattern().getInputs())
                    for (var option : input.getPossibleInputs())
                        if (!bus.getConfig().keySet().contains(option.what())) return false;
        }
        if (endpoint.entity() instanceof AbstractFurnaceBlockEntity
                && endpoint.direction() != Direction.DOWN) return false;
        for (var input : recipe.pattern().getInputs())
            for (var option : input.getPossibleInputs())
                if (!ResourceTransfer.hasHandler(
                        endpoint.level(),
                        endpoint.machine(),
                        endpoint.direction().getOpposite(),
                        option.what())) return false;
        return true;
    }

    public List<Endpoint> endpoints(Blueprint recipe) {
        return endpoints.stream()
                .filter(
                        e ->
                                e.entity() != null
                                        && controllable(e, recipe)
                                        && adapters.stream()
                                                .anyMatch(a -> a.supports(e.entity(), recipe)))
                .toList();
    }

    public String unavailableReason(Blueprint recipe) {
        if (!endpoints(recipe).isEmpty())
            return "Recognized eligible endpoints: " + endpoints(recipe).size();
        boolean recognized =
                endpoints.stream()
                        .anyMatch(
                                e ->
                                        e.entity() != null
                                                && adapters.stream()
                                                        .anyMatch(
                                                                a ->
                                                                        a.supports(
                                                                                e.entity(),
                                                                                recipe)));
        return recognized
                ? "Matching machine lacks a verified input/output path"
                : "No active matching machine or sufficient tier for " + recipe.family();
    }

    public List<Slot> slots(Blueprint recipe) {
        var result = new LinkedHashMap<String, Slot>();
        for (var endpoint : endpoints(recipe)) {
            long queue =
                    endpoint.provider() != null && endpoint.provider().isBusy()
                            ? recipe.batchTicks()
                            : 0;
            for (var adapter : adapters)
                if (adapter.supports(endpoint.entity(), recipe))
                    queue = Math.max(queue, adapter.queueTicks(endpoint.entity(), recipe));
            boolean availableNow = ready(endpoint, recipe, endpoint.node().getGrid());
            if (!availableNow && queue == 0) queue = Math.max(20, recipe.batchTicks());
            result.putIfAbsent(
                    endpoint.identity(),
                    new Slot(
                            endpoint.identity(),
                            queue,
                            recipe.batchTicks(),
                            recipe.timingKnown() && availableNow));
        }
        return List.copyOf(result.values());
    }

    public boolean ready(Endpoint endpoint, Blueprint recipe, IGrid grid) {
        var machine = endpoint.entity();
        if (machine == null
                || !endpoint.node().isActive()
                || endpoint.node().getGrid() != grid
                || !controllable(endpoint, recipe)) return false;
        if (recipe.pattern().supportsPushInputsToExternalInventory())
            for (var output : recipe.pattern().getOutputs()) {
                boolean accepting =
                        returning.getOrDefault(endpoint.identity(), List.of()).stream()
                                .anyMatch(
                                        n ->
                                                n.isActive()
                                                        && n.getGrid() == grid
                                                        && n.getOwner() instanceof ImportBusPart bus
                                                        && redstoneAllows(bus)
                                                        && (bus.getConfig().keySet().isEmpty()
                                                                || bus.getConfig()
                                                                        .keySet()
                                                                        .contains(output.what())));
                if (!accepting
                        && (hasReturnBus(endpoint, output.what())
                                || adapters.stream()
                                        .noneMatch(
                                                a ->
                                                        a.supports(machine, recipe)
                                                                && a.canRecover(
                                                                        machine,
                                                                        recipe,
                                                                        output.what()))))
                    return false;
            }
        if (endpoint.provider() != null
                && (endpoint.provider().getGrid() != grid
                        || endpoint.provider().isBusy()
                        || endpoint.provider().getCraftingLockedReason() != LockCraftingMode.NONE))
            return false;
        if (endpoint.node().getOwner() instanceof ExportBusPart bus) {
            var mode = bus.getRSMode();
            boolean powered = bus.getHost().hasRedstone();
            if (mode == appeng.api.config.RedstoneMode.SIGNAL_PULSE
                    || mode == appeng.api.config.RedstoneMode.HIGH_SIGNAL && !powered
                    || mode == appeng.api.config.RedstoneMode.LOW_SIGNAL && powered) return false;
            if (!bus.getConfig().keySet().isEmpty())
                for (var input : recipe.pattern().getInputs())
                    for (var option : input.getPossibleInputs())
                        if (!bus.getConfig().keySet().contains(option.what())) return false;
        }
        return adapters.stream()
                .anyMatch(a -> a.supports(machine, recipe) && a.ready(machine, recipe));
    }

    private boolean hasReturnBus(Endpoint endpoint, appeng.api.stacks.AEKey key) {
        return returning.getOrDefault(endpoint.identity(), List.of()).stream()
                .anyMatch(
                        n ->
                                n.getOwner() instanceof ImportBusPart bus
                                        && (bus.getConfig().keySet().isEmpty()
                                                || bus.getConfig().keySet().contains(key)));
    }

    public void recover(
            appeng.helpers.patternprovider.PatternProviderReturnInventory buffer, IGrid grid) {
        var seen = new HashSet<String>();
        var destinations = new ArrayList<>(endpoints);
        // Multiple connections share one machine. Return through a provider waiting
        // for its result before choosing another provider or the controller buffer.
        destinations.sort(
                Comparator.comparingInt(
                        e ->
                                e.provider() == null
                                        ? 2
                                        : e.provider().getCraftingLockedReason()
                                                        == LockCraftingMode.LOCK_UNTIL_RESULT
                                                ? 0
                                                : 1));
        for (var endpoint : destinations) {
            var machine = endpoint.entity();
            if (machine == null
                    || !endpoint.node().isActive()
                    || endpoint.node().getGrid() != grid
                    || !seen.add(endpoint.identity())) continue;
            for (var adapter : adapters)
                for (var output : adapter.outputs(machine)) {
                    if (hasReturnBus(endpoint, output.key()))
                        continue; // Preserve explicitly configured import-bus redstone gates.
                    var destination =
                            endpoint.provider() == null
                                    ? buffer
                                    : endpoint.provider().aesiReturnBuffer();
                    OutputRecovery.recover(output, destination);
                }
        }
    }

    private static boolean redstoneAllows(appeng.parts.automation.IOBusPart bus) {
        boolean powered = bus.getHost().hasRedstone();
        return switch (bus.getRSMode()) {
            case IGNORE -> true;
            case HIGH_SIGNAL -> powered;
            case LOW_SIGNAL -> !powered;
            case SIGNAL_PULSE -> false;
        };
    }

    private static final class VanillaMachines implements MachineAdapter {
        public boolean canRecover(
                BlockEntity machine, Blueprint recipe, appeng.api.stacks.AEKey output) {
            return machine instanceof AbstractFurnaceBlockEntity
                    && output instanceof appeng.api.stacks.AEItemKey;
        }

        public java.util.List<OutputRecovery.Port> outputs(BlockEntity machine) {
            if (!(machine instanceof AbstractFurnaceBlockEntity furnace)
                    || furnace.getLevel() == null) return List.of();
            var handler =
                    furnace.getLevel()
                            .getCapability(
                                    net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                                    furnace.getBlockPos(),
                                    Direction.DOWN);
            // Vanilla views expose global slots or remap the sided {result, fuel} slots.
            if (handler == null || handler.size() == 0) return List.of();
            int slot =
                    handler.size() == furnace.getContainerSize() ? 2 : handler.size() == 2 ? 0 : -1;
            if (slot < 0 || furnace.getItem(2).isEmpty()) return List.of();
            var resource = handler.getResource(slot);
            if (!resource.equals(
                    net.neoforged.neoforge.transfer.item.ItemResource.of(furnace.getItem(2))))
                return List.of();
            return List.of(
                    new OutputRecovery.Port(
                            appeng.api.stacks.AEItemKey.of(resource),
                            handler.getAmountAsLong(slot),
                            (amount, tx) -> handler.extract(slot, resource, amount, tx)));
        }

        public boolean supports(BlockEntity machine, Blueprint recipe) {
            if (!recipe.family().equals("crafting")) {
                if (!(machine instanceof AbstractFurnaceBlockEntity)
                        || !Set.of("smelting", "blasting", "smoking").contains(recipe.family()))
                    return false;
                if (!(machine.getLevel() instanceof ServerLevel level)) return false;
                var holder =
                        level.getServer()
                                .getRecipeManager()
                                .byKey(
                                        net.minecraft.resources.ResourceKey.create(
                                                net.minecraft.core.registries.Registries.RECIPE,
                                                net.minecraft.resources.Identifier.parse(
                                                        recipe.id())))
                                .orElse(null);
                if (holder == null
                        || !VirtualPatterns.matchesCurrentRecipe(level, holder.value(), recipe))
                    return false;
                if (holder.value()
                        instanceof
                        net.minecraft.world.item.crafting.AbstractCookingRecipe cooking) {
                    var input = recipe.pattern().getInputs()[0].getPossibleInputs()[0].what();
                    for (var other : level.getServer().getRecipeManager().getRecipes())
                        if (other.value()
                                        instanceof
                                        net.minecraft.world.item.crafting.AbstractCookingRecipe
                                                candidate
                                && candidate.getType() == cooking.getType()
                                && !other.id().equals(holder.id())
                                && input instanceof appeng.api.stacks.AEItemKey key
                                && candidate.input().test(key.toStack())) return false;
                }
            }
            return switch (recipe.family()) {
                case "crafting" ->
                        appeng.api.implementations.blockentities.ICraftingMachine.of(machine, null)
                                != null;
                case "smelting" -> machine instanceof FurnaceBlockEntity;
                case "blasting" -> machine instanceof BlastFurnaceBlockEntity;
                case "smoking" -> machine instanceof SmokerBlockEntity;
                default -> false;
            };
        }

        public boolean ready(BlockEntity machine, Blueprint recipe) {
            if (machine instanceof MolecularAssemblerBlockEntity assembler)
                return assembler.acceptsPlans() && assembler.isPowered();
            if (recipe.family().equals("crafting")) {
                var crafter =
                        appeng.api.implementations.blockentities.ICraftingMachine.of(machine, null);
                return crafter != null && crafter.acceptsPlans();
            }
            if (machine instanceof AbstractFurnaceBlockEntity furnace) {
                var output = furnace.getItem(2);
                var expected = recipe.pattern().getPrimaryOutput();
                boolean outputSpace =
                        output.isEmpty()
                                || (expected.what() instanceof appeng.api.stacks.AEItemKey key
                                        && key.matches(output)
                                        && output.getCount() + expected.amount()
                                                <= output.getMaxStackSize());
                return outputSpace
                        && furnace.getItem(0).isEmpty()
                        && (furnace.getBlockState()
                                        .getValue(
                                                net.minecraft.world.level.block.state.properties
                                                        .BlockStateProperties.LIT)
                                || !furnace.getItem(1).isEmpty());
            }
            return false;
        }

        public long queueTicks(BlockEntity machine, Blueprint recipe) {
            if (machine instanceof MolecularAssemblerBlockEntity a && !a.acceptsPlans())
                return recipe.batchTicks();
            if (machine instanceof AbstractFurnaceBlockEntity f && !f.getItem(0).isEmpty())
                return recipe.batchTicks();
            return 0;
        }
    }
}

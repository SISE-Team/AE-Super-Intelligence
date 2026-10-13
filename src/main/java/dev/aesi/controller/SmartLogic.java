// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.*;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.*;
import appeng.helpers.patternprovider.*;

import dev.aesi.bridge.*;
import dev.aesi.item.SmartPatternItem;
import dev.aesi.planner.*;
import dev.aesi.planner.Model.*;
import dev.aesi.recipe.*;

import net.minecraft.server.level.ServerLevel;

import java.util.*;

public final class SmartLogic extends PatternProviderLogic {
    private final IManagedGridNode node;
    private final PatternProviderLogicHost owner;
    private volatile List<IPatternDetails> offered = List.of();
    private Map<AEItemKey, Blueprint> blueprints = Map.of();
    private MachineRegistry machines;
    private boolean dirty = true;
    private long nextRefresh;
    private int cursor;
    private List<String> diagnostics = List.of();
    private final Set<AEKey> rememberedComponents = new LinkedHashSet<>();

    public SmartLogic(IManagedGridNode node, PatternProviderLogicHost owner) {
        super(node, owner);
        this.node = node;
        this.owner = owner;
        ((appeng.util.inv.AppEngInternalInventory) getPatternInv())
                .setFilter(
                        new appeng.util.inv.filter.IAEItemFilter() {
                            @Override
                            public boolean allowInsert(
                                    appeng.api.inventories.InternalInventory inv,
                                    int slot,
                                    net.minecraft.world.item.ItemStack stack) {
                                return stack.getItem() instanceof SmartPatternItem;
                            }
                        });
    }

    @Override
    public void updatePatterns() {
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public void tick() {
        if (!(owner.getBlockEntity().getLevel() instanceof ServerLevel level)) return;
        if (!node.isActive()) {
            NetworkRuntime.remove(this);
            return;
        }
        if (dirty || level.getGameTime() >= nextRefresh) {
            rebuild(level);
            dirty = false;
            nextRefresh = level.getGameTime() + 100;
        }
        if (machines != null && level.getGameTime() % 5 == 0)
            machines.recover(getReturnInv(), getGrid());
    }

    public void rebuild(ServerLevel level) {
        var grid = getGrid();
        if (grid == null) return;
        machines = new MachineRegistry(grid);
        var components = new LinkedHashSet<AEKey>(rememberedComponents);
        for (var entry : grid.getStorageService().getInventory().getAvailableStacks())
            if (entry.getKey().hasComponents() && components.size() < 256)
                components.add(entry.getKey());
        // A CPU withdraws the complete job's inputs before later batches are dispatched.
        // Keep learned identities through that withdrawal and through world saves.
        rememberedComponents.addAll(components);
        var catalog = RecipeCatalog.get(level, components);
        var goals = new LinkedHashMap<AEKey, Strategy>();
        for (var seed : getPatternInv()) {
            if (!(seed.getItem() instanceof SmartPatternItem)) continue;
            var target = SmartPatternItem.target(seed);
            if (target != null) goals.put(target, SmartPatternItem.strategy(seed));
        }
        var generated = new LinkedHashMap<AEItemKey, Blueprint>();
        var recipeModels = new LinkedHashMap<String, Recipe<AEKey>>();
        var patterns = new LinkedHashMap<String, IPatternDetails>();
        var reasons = new LinkedHashSet<String>();
        if (getConfigManager().getSetting(appeng.api.config.Settings.LOCK_CRAFTING_MODE)
                == LockCraftingMode.LOCK_UNTIL_RESULT)
            reasons.add(
                    "Controller does not receive machine output directly; configure result locking"
                        + " on the machine's native provider.");
        // Ordinary providers stay in place; the pure planner only references their native
        // definitions.
        for (var member : grid.getNodes()) {
            if (!member.isActive()) continue;
            var service = member.getService(ICraftingProvider.class);
            if (service == null || service instanceof SmartLogic) continue;
            for (var pattern : service.getAvailablePatterns()) {
                String id = "native/" + VirtualPatterns.identity(level, pattern);
                var model =
                        NetworkRuntime.normalize(
                                id,
                                pattern,
                                List.of(
                                        new Slot(
                                                "native/" + System.identityHashCode(service),
                                                service.isBusy() ? 20 : 0,
                                                20,
                                                false)),
                                components,
                                level);
                if (model != null) {
                    recipeModels.putIfAbsent(id, model);
                    patterns.putIfAbsent(id, pattern);
                }
            }
        }
        var queue = new ArrayDeque<AEKey>(goals.keySet());
        var visited = new HashSet<AEKey>();
        while (!queue.isEmpty() && visited.size() < 2048 && generated.size() < 4096) {
            var output = queue.removeFirst();
            if (!visited.add(output)) continue;
            for (var blueprint : catalog.forOutput(output)) {
                var slots = machines.slots(blueprint);
                if (slots.isEmpty()) {
                    if (reasons.size() < 100)
                        reasons.add(blueprint.id() + ": " + machines.unavailableReason(blueprint));
                    continue;
                }
                var definition = blueprint.pattern().getDefinition();
                String id = "virtual/" + VirtualPatterns.identity(level, blueprint.pattern());
                if (generated.containsKey(definition)) continue;
                var model =
                        NetworkRuntime.normalize(id, blueprint.pattern(), slots, components, level);
                if (model == null) continue;
                generated.put(definition, blueprint);
                recipeModels.put(id, model);
                patterns.put(id, blueprint.pattern());
                for (var input : blueprint.pattern().getInputs())
                    for (var alternative : input.getPossibleInputs())
                        queue.addLast(alternative.what());
            }
            // Expand dependencies of ordinary patterns as well.
            for (var recipe : recipeModels.values())
                if (recipe.output().key().equals(output))
                    for (var input : recipe.inputs())
                        for (var alternative : input.alternatives())
                            if (!visited.contains(alternative.key()))
                                queue.addLast(alternative.key());
        }
        if (!queue.isEmpty()) reasons.add("Dependency index budget reached");
        blueprints = Map.copyOf(generated);
        var updated = generated.values().stream().map(Blueprint::pattern).toList();
        boolean changed = !updated.equals(offered);
        offered = updated;
        diagnostics = List.copyOf(reasons);
        NetworkRuntime.publish(
                this,
                grid,
                new NetworkRuntime.Bundle(List.copyOf(recipeModels.values()), patterns, goals),
                getPriority(),
                level.dimension().identifier()
                        + "/"
                        + owner.getBlockEntity().getBlockPos().asLong());
        if (changed) ICraftingProvider.requestUpdate(node);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return offered == null ? List.of() : offered;
    }

    @Override
    public void writeToNBT(net.minecraft.world.level.storage.ValueOutput output) {
        super.writeToNBT(output);
        output.store(
                "aesi_component_inputs",
                GenericStack.CODEC.listOf(),
                rememberedComponents.stream().map(k -> new GenericStack(k, 1)).toList());
    }

    @Override
    public void readFromNBT(net.minecraft.world.level.storage.ValueInput input) {
        super.readFromNBT(input);
        rememberedComponents.clear();
        input.read("aesi_component_inputs", GenericStack.CODEC.listOf()).orElse(List.of()).stream()
                .limit(256)
                .forEach(s -> rememberedComponents.add(s.what()));
        dirty = true;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        if (!node.isActive()
                || machines == null
                || getCraftingLockedReason() != LockCraftingMode.NONE) return false;
        if (getConfigManager().getSetting(appeng.api.config.Settings.LOCK_CRAFTING_MODE)
                == LockCraftingMode.LOCK_UNTIL_RESULT) return false;
        var recipe = blueprints.get(pattern.getDefinition());
        if (recipe == null || !recipe.pattern().equals(pattern)) return false;
        var endpoints = machines.endpoints(recipe);
        if (endpoints.isEmpty()) return false;
        // Reallocate only between machines capable of the exact same stable input/output contract.
        int start = Math.floorMod(cursor, endpoints.size());
        for (int i = 0; i < endpoints.size(); i++) {
            var endpoint = endpoints.get((start + i) % endpoints.size());
            if (!machines.ready(endpoint, recipe, getGrid())
                    || !NetworkRuntime.claim(endpoint.identity())) continue;
            try {
                boolean accepted =
                        endpoint.provider() == null
                                ? TransactionalDispatch.push(endpoint, inputs)
                                : DispatchBridge.push(
                                        endpoint.provider(), endpoint.direction(), pattern, inputs);
                if (accepted) {
                    cursor = start + i + 1;
                    ((ProviderAccess) (Object) this).aesiOnPushSuccess(pattern);
                    return true;
                }
            } finally {
                NetworkRuntime.release(endpoint.identity());
            }
        }
        return false;
    }
}

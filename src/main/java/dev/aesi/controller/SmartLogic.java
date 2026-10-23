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
    public static final int BASE_CAPACITY = 9, DISK_SLOTS = 16, ARRAY_SLOTS = 6,
            MAX_CAPACITY = BASE_CAPACITY + ARRAY_SLOTS * dev.aesi.multiblock.DiskArrayEntity.MAX_CAPACITY;
    private final PatternDiskInventory disks;
    private final MountedArrayInventory arrays;
    private final ComputeInventory compute;
    private Set<String> lastModules = Set.of();
    private Set<String> missingModules = Set.of();
    private volatile boolean planningPulse;
    private long planningUntil, workingUntil;
    private String lastBays = "";
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
        super(node, owner, BASE_CAPACITY);
        this.node = node;
        this.owner = owner;
        ((appeng.util.inv.AppEngInternalInventory) super.getPatternInv())
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
        disks = new PatternDiskInventory(this, super.getPatternInv());
        arrays = new MountedArrayInventory((SmartController)owner.getBlockEntity(),super.getPatternInv());
        compute = new ComputeInventory((SmartController)owner.getBlockEntity());
    }

    public ComputeInventory computeInventory() { return compute; }
    public Set<String> installedModules() { return compute.modules(); }
    public Set<String> missingModules() { return missingModules; }
    public void modulesChanged() { structureChanged(); }
    public void notePlanning() { planningPulse = true; }
    public int runtimeMode() {
        if (!operational()) return 0;
        long now=owner.getBlockEntity().getLevel().getGameTime();
        if(now<workingUntil)return 3;
        if(now<planningUntil)return 2;
        return missingModules.isEmpty()||!offered.isEmpty()?1:4;
    }

    public appeng.util.inv.AppEngInternalInventory diskInventory() { return disks.drives(); }
    public int diskMask() { return arrays == null ? 0 : arrays.mask(); }
    public int diskCount() { return Integer.bitCount(diskMask()); }
    /** Usable slots: the base nine plus the drive capacity of each mounted array. */
    public int patternCapacity() {
        int sum = BASE_CAPACITY;
        if (arrays != null) for (int bay = 0; bay < ARRAY_SLOTS; bay++) sum += arrays.capacity(bay);
        return sum;
    }
    /** Capacity of the array mounted in a bay, 0 when absent or without drives. */
    public int bayCapacity(int bay) { return arrays == null ? 0 : arrays.capacity(bay); }
    /** First controller slot of a bay; bays have a fixed stride so indices never shift. */
    public static int bayStart(int bay) { return BASE_CAPACITY + bay * dev.aesi.multiblock.DiskArrayEntity.MAX_CAPACITY; }
    /** 24 characters, four per bay: '-' for an absent bay, else each drive slot's tier digit. */
    public String bayTiers() { return arrays == null ? "-".repeat(ARRAY_SLOTS * dev.aesi.multiblock.DiskArrayEntity.DRIVE_SLOTS) : arrays.tiers(); }
    @Override public appeng.api.inventories.InternalInventory getPatternInv() {
        return arrays == null ? super.getPatternInv() : arrays.patterns();
    }

    @Override public void onChangeInventory(appeng.util.inv.AppEngInternalInventory inv, int slot) {
        super.onChangeInventory(inv, slot);
        dirty = true;
    }

    @Override public void addDrops(List<net.minecraft.world.item.ItemStack> drops) {
        super.addDrops(drops);
        for (var stack : disks.drives()) drops.add(stack.copy());
    }

    @Override public void clearContent() {
        super.clearContent();
        disks.drives().clear();
    }

    @Override
    public void updatePatterns() {
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean operational() {
        return owner != null && owner.getBlockEntity() instanceof SmartController controller
                && controller.networkReady();
    }

    public void structureChanged() {
        dirty = true;
        NetworkRuntime.remove(this);
        if (offered != null && !offered.isEmpty()) {
            offered = List.of();
            ICraftingProvider.requestUpdate(node);
        }
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public void tick() {
        if (!(owner.getBlockEntity().getLevel() instanceof ServerLevel level)) return;
        if(planningPulse){planningUntil=level.getGameTime()+40;planningPulse=false;}
        var modules=installedModules();
        if(!lastModules.equals(modules)){lastModules=modules;modulesChanged();}
        var bays=bayTiers();
        if(!lastBays.equals(bays)){lastBays=bays;structureChanged();}
        if (!operational()) {
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
        if (!operational()) return;
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
        for (int slot = 0; slot < getPatternInv().size(); slot++) {
            var seed = getPatternInv().getStackInSlot(slot);
            if (!(seed.getItem() instanceof SmartPatternItem)) continue;
            var target = SmartPatternItem.target(seed);
            if (target != null) goals.put(target, SmartPatternItem.strategy(seed));
        }
        var generated = new LinkedHashMap<AEItemKey, Blueprint>();
        var installed = dev.aesi.recipe.LearningDatasets.capabilities(installedModules());
        var locked = new TreeSet<String>();
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
                if(!ModuleAccess.allows(installed,blueprint.family(),blueprint.requiredEu())) {
                    var missing=dev.aesi.recipe.LearningDatasets.missing(blueprint.family(),blueprint.requiredEu());
                    locked.add(missing);
                    if(reasons.size()<100)reasons.add(blueprint.id()+": requires process dataset "+missing);
                    continue;
                }
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
        missingModules = Set.copyOf(locked);
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
        return offered == null || !operational() ? List.of() : offered;
    }

    @Override
    public void writeToNBT(net.minecraft.world.level.storage.ValueOutput output) {
        super.writeToNBT(output);
        disks.drives().writeToNBT(output, "aesi_disks");
        output.store(
                "aesi_component_inputs",
                GenericStack.CODEC.listOf(),
                rememberedComponents.stream().map(k -> new GenericStack(k, 1)).toList());
    }

    @Override
    public void readFromNBT(net.minecraft.world.level.storage.ValueInput input) {
        super.readFromNBT(input);
        disks.drives().clear();
        disks.drives().readFromNBT(input, "aesi_disks");
        disks.reload();
        rememberedComponents.clear();
        input.read("aesi_component_inputs", GenericStack.CODEC.listOf()).orElse(List.of()).stream()
                .limit(256)
                .forEach(s -> rememberedComponents.add(s.what()));
        dirty = true;
    }

    @Override
    public boolean isBusy() {
        return !operational();
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        if (!operational()
                || !(owner.getBlockEntity() instanceof SmartController controller) || !controller.canDispatch()
                || machines == null
                || getCraftingLockedReason() != LockCraftingMode.NONE) return false;
        if (getConfigManager().getSetting(appeng.api.config.Settings.LOCK_CRAFTING_MODE)
                == LockCraftingMode.LOCK_UNTIL_RESULT) return false;
        var recipe = blueprints.get(pattern.getDefinition());
        if (recipe == null || !recipe.pattern().equals(pattern)) return false;
        // Recheck the physical inventory at dispatch. An old CPU plan never bypasses a removed component.
        if (!ModuleAccess.allows(dev.aesi.recipe.LearningDatasets.capabilities(installedModules()),recipe.family(),recipe.requiredEu())) return false;
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
                    workingUntil=owner.getBlockEntity().getLevel().getGameTime()+40;
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

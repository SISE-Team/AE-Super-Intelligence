// SPDX-License-Identifier: MIT
package dev.aesi;

import appeng.api.AECapabilities;
import appeng.api.crafting.*;
import appeng.api.stacks.AEItemKey;
import appeng.block.crafting.PatternProviderBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;

import dev.aesi.controller.*;
import dev.aesi.item.DriveTiers;
import dev.aesi.item.PatternDriveItem;
import dev.aesi.item.SmartPatternItem;
import dev.aesi.multiblock.*;
import dev.aesi.recipe.RecipeCatalog;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.*;

@Mod(Aesi.ID)
public final class Aesi {
    public static final String ID = "aesi";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredBlock<ControllerBlock> CONTROLLER =
            BLOCKS.registerBlock("smart_controller", ControllerBlock::new);
    public static final DeferredItem<BlockItem> CONTROLLER_ITEM =
            workstationItem(CONTROLLER);
    public static final DeferredBlock<WorkstationPartBlock> STRUCTURAL_CASING =
            BLOCKS.registerBlock("structural_casing", WorkstationPartBlock::new);
    public static final DeferredBlock<WorkstationPartBlock> ARRAY_RACK =
            BLOCKS.registerBlock("array_rack", WorkstationPartBlock::new);
    public static final DeferredBlock<ComputingArrayBlock> STRATEGY_ARRAY =
            BLOCKS.registerBlock("strategy_array", ComputingArrayBlock::new);
    public static final DeferredItem<dev.aesi.item.ComputingArrayItem> COMPUTING_ARRAY_ITEM =
            ITEMS.registerItem("strategy_array", dev.aesi.item.ComputingArrayItem::new, p -> p.stacksTo(1).useBlockDescriptionPrefix());
    public static final DeferredBlock<ComputingArrayBlock> COMPUTE_SERVER_BLOCK =
            BLOCKS.registerBlock("compute_server", ComputingArrayBlock::new);
    public static final DeferredItem<dev.aesi.item.ComputeServerItem> COMPUTE_SERVER =
            ITEMS.registerItem("compute_server", dev.aesi.item.ComputeServerItem::new, p -> p.stacksTo(1).useBlockDescriptionPrefix());
    public static final DeferredBlock<WorkstationPartBlock> DATA_BUS =
            BLOCKS.registerBlock("data_bus", WorkstationPartBlock::new);
    public static final DeferredBlock<WorkstationPartBlock> PLANNING_CORE =
            BLOCKS.registerBlock("planning_core", WorkstationPartBlock::new);
    public static final DeferredBlock<NetworkPortBlock> NETWORK_PORT =
            BLOCKS.registerBlock("network_port", NetworkPortBlock::new);
    public static final DeferredBlock<WorkstationPartBlock> COOLING_UNIT =
            BLOCKS.registerBlock("cooling_unit", WorkstationPartBlock::new);
    public static final DeferredBlock<ServerTailBlock> SERVER_TAIL = BLOCKS.registerBlock("server_tail",ServerTailBlock::new);
    public static final DeferredBlock<DiskArrayBlock> DISK_ARRAY = BLOCKS.registerBlock("disk_array", DiskArrayBlock::new);
    public static final java.util.List<DeferredItem<BlockItem>> PART_ITEMS = java.util.List.of(
            workstationItem(STRUCTURAL_CASING), workstationItem(ARRAY_RACK),
            workstationItem(DATA_BUS), workstationItem(PLANNING_CORE),
            workstationItem(NETWORK_PORT), workstationItem(COOLING_UNIT));
    public static final DeferredItem<SmartPatternItem> SMART_PATTERN =
            ITEMS.registerItem(
                    "smart_pattern", SmartPatternItem::new, properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> BLANK_MODULE = ITEMS.registerSimpleItem("blank_module");
    public static final DeferredItem<dev.aesi.item.ModuleUpgradeItem> MODULE_UPGRADE = ITEMS.registerItem(
            "module_upgrade", dev.aesi.item.ModuleUpgradeItem::new, p -> p.stacksTo(1));
    public static final java.util.Map<String,DeferredItem<dev.aesi.item.ModuleUpgradeItem>> DATASET_ITEMS = registerDatasets();
    public static final java.util.Map<String,DeferredItem<Item>> DATASET_DRAFTS = registerDatasetDrafts();
    private static java.util.Map<String,DeferredItem<dev.aesi.item.ModuleUpgradeItem>> registerDatasets() {
        var items=new java.util.LinkedHashMap<String,DeferredItem<dev.aesi.item.ModuleUpgradeItem>>();
        for(var entry:dev.aesi.recipe.LearningDatasets.all())items.put(entry.id(),ITEMS.registerItem("dataset_"+entry.id(),
                p->new dev.aesi.item.ModuleUpgradeItem(p,entry.id()),p->p.stacksTo(1)));
        return java.util.Collections.unmodifiableMap(items);
    }
    private static java.util.Map<String,DeferredItem<Item>> registerDatasetDrafts() {
        var items=new java.util.LinkedHashMap<String,DeferredItem<Item>>();
        for(var entry:dev.aesi.recipe.LearningDatasets.all())items.put(entry.id(),ITEMS.registerSimpleItem("dataset_"+entry.id()+"_unwritten",p->p.stacksTo(1)));
        return java.util.Collections.unmodifiableMap(items);
    }
    public static final DeferredItem<dev.aesi.item.PatternDiskItem> PATTERN_DISK = ITEMS.registerItem(
            "pattern_disk", dev.aesi.item.PatternDiskItem::new, properties -> properties.stacksTo(1));
    public static final java.util.List<DeferredItem<PatternDriveItem>> PATTERN_DRIVES =
            java.util.stream.IntStream.rangeClosed(1, DriveTiers.TIERS).mapToObj(tier -> ITEMS.registerItem(
                    "pattern_drive_" + tier, properties -> new PatternDriveItem(properties, tier),
                    properties -> properties.stacksTo(1))).toList();
    public static final java.util.List<DeferredItem<Item>> EXPANSION_CHIPS =
            java.util.stream.IntStream.rangeClosed(2, DriveTiers.TIERS)
                    .mapToObj(tier -> ITEMS.registerSimpleItem("expansion_chip_" + tier)).toList();
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<PatternDriveMenu>> PATTERN_DRIVE_MENU =
            MENUS.register("pattern_drive", () -> appeng.menu.implementations.MenuTypeBuilder
                    .create(PatternDriveMenu::new, appeng.api.implementations.menuobjects.ItemMenuHost.class)
                    .withMenuTitle(host -> host.getItemStack().getHoverName())
                    .buildUnregistered(id("pattern_drive")));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<ComputeArrayMenu>> COMPUTE_ARRAY_MENU =
            MENUS.register("compute_array", () -> appeng.menu.implementations.MenuTypeBuilder
                    .create(ComputeArrayMenu::new, appeng.api.implementations.menuobjects.ItemMenuHost.class)
                    .withMenuTitle(host -> host.getItemStack().getHoverName()).buildUnregistered(id("compute_array")));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<SmartPatternMenu>> SMART_PATTERN_MENU =
            MENUS.register("smart_pattern", () -> appeng.menu.implementations.MenuTypeBuilder
                    .create(SmartPatternMenu::new, appeng.api.implementations.menuobjects.ItemMenuHost.class)
                    .withMenuTitle(host -> host.getItemStack().getHoverName()).buildUnregistered(id("smart_pattern")));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<DriveArrayMenu>> DRIVE_ARRAY_MENU =
            MENUS.register("drive_array", () -> appeng.menu.implementations.MenuTypeBuilder
                    .create(DriveArrayMenu::new, appeng.api.implementations.menuobjects.ItemMenuHost.class)
                    .withMenuTitle(host -> host.getItemStack().getHoverName())
                    .buildUnregistered(id("drive_array")));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<WorkstationMenu>> WORKSTATION_MENU =
            MENUS.register("workstation", () -> appeng.menu.implementations.MenuTypeBuilder
                    .create(WorkstationMenu::new, SmartController.class)
                    .withMenuTitle(host -> net.minecraft.network.chat.Component.translatable("block.aesi.smart_controller"))
                    .buildUnregistered(id("workstation")));
    public static final DeferredHolder<
                    BlockEntityType<?>, BlockEntityType<PatternProviderBlockEntity>>
            CONTROLLER_ENTITY =
                    ENTITIES.register(
                            "smart_controller",
                            () -> {
                                var type =
                                        new BlockEntityType<PatternProviderBlockEntity>(
                                                (pos, state) -> new SmartController(pos, state),
                                                CONTROLLER.get());
                                AEBaseBlockEntity.registerBlockEntityItem(
                                        type, CONTROLLER_ITEM.get());
                                CONTROLLER
                                        .get()
                                        .setBlockEntity(
                                                PatternProviderBlockEntity.class,
                                                type,
                                                null,
                                                (level, pos, state, be) ->
                                                        ((SmartController) be).serverTick());
                                return type;
                            });

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkPortEntity>> NETWORK_PORT_ENTITY =
            ENTITIES.register("network_port", () -> {
                var type = new BlockEntityType<NetworkPortEntity>(NetworkPortEntity::new, NETWORK_PORT.get());
                AEBaseBlockEntity.registerBlockEntityItem(type, NETWORK_PORT.get().asItem());
                NETWORK_PORT.get().setBlockEntity(NetworkPortEntity.class, type, null,
                        (level, pos, state, be) -> be.serverTick());
                return type;
            });
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DiskArrayEntity>> DISK_ARRAY_ENTITY =
            ENTITIES.register("disk_array", () -> {
                var type=new BlockEntityType<DiskArrayEntity>(DiskArrayEntity::new,DISK_ARRAY.get());
                AEBaseBlockEntity.registerBlockEntityItem(type,PATTERN_DISK.get());
                DISK_ARRAY.get().setBlockEntity(DiskArrayEntity.class,type,null,null);
                return type;
            });

    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ComputingArrayEntity>> COMPUTING_ARRAY_ENTITY =
            ENTITIES.register("computing_array", () -> {
                var type=new BlockEntityType<ComputingArrayEntity>(ComputingArrayEntity::new,STRATEGY_ARRAY.get(),COMPUTE_SERVER_BLOCK.get());
                AEBaseBlockEntity.registerBlockEntityItem(type,COMPUTING_ARRAY_ITEM.get());
                STRATEGY_ARRAY.get().setBlockEntity(ComputingArrayEntity.class,type,null,null);
                COMPUTE_SERVER_BLOCK.get().setBlockEntity(ComputingArrayEntity.class,type,null,null);
                return type;
            });

    public Aesi(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);

        bus.addListener(
                (RegisterCapabilitiesEvent event) -> {
                    event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                            NETWORK_PORT_ENTITY.get(), (be, ctx) -> be);
                    event.registerBlockEntity(
                            AECapabilities.IN_WORLD_GRID_NODE_HOST,
                            CONTROLLER_ENTITY.get(),
                            (be, ctx) -> be);
                    event.registerBlockEntity(
                            AECapabilities.GENERIC_INTERNAL_INV,
                            CONTROLLER_ENTITY.get(),
                            (be, side) -> be.getLogic().getReturnInv());
                });
        bus.addListener(
                (BuildCreativeModeTabContentsEvent event) -> {
                    if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
                        event.accept(CONTROLLER_ITEM);
                        PART_ITEMS.forEach(event::accept);
                        event.accept(COMPUTE_SERVER);
                        event.accept(SMART_PATTERN);
                        event.accept(PATTERN_DISK);
                        PATTERN_DRIVES.forEach(event::accept);
                        EXPANSION_CHIPS.forEach(event::accept);
                        event.accept(BLANK_MODULE);
                        for(var dataset:dev.aesi.recipe.LearningDatasets.all())if(dataset.loaded()) {
                            event.accept(DATASET_ITEMS.get(dataset.id()));event.accept(DATASET_DRAFTS.get(dataset.id()));
                        }
                    }
                });
        // Seeds are permitted in the existing provider menu but never offered as executable
        // patterns.
        PatternDetailsHelper.registerDecoder(
                new IPatternDetailsDecoder() {
                    public boolean isEncodedPattern(ItemStack stack) {
                        return stack.is(SMART_PATTERN.get());
                    }

                    public IPatternDetails decodePattern(AEItemKey key, Level level) {
                        return null;
                    }
                });
        NeoForge.EVENT_BUS.addListener(
                (OnDatapackSyncEvent event) -> {
                    if (event.getPlayer() == null) RecipeCatalog.invalidate();
                });
        NeoForge.EVENT_BUS.addListener(
                (ServerStoppedEvent event) -> {
                    RecipeCatalog.invalidate();
                    NetworkRuntime.clear();
                });
        NeoForge.EVENT_BUS.addListener(AesiCommands::register);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    private static DeferredItem<BlockItem> workstationItem(DeferredBlock<?> block) {
        return ITEMS.registerSimpleBlockItem(block, properties -> properties.component(
                net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        net.minecraft.network.chat.Component.translatable(block == NETWORK_PORT
                                ? "tooltip.aesi.network_port" : "tooltip.aesi.workstation")))));
    }
}

// SPDX-License-Identifier: MIT
package dev.aesi;

import appeng.api.AECapabilities;
import appeng.api.crafting.*;
import appeng.api.stacks.AEItemKey;
import appeng.block.crafting.PatternProviderBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;

import dev.aesi.controller.*;
import dev.aesi.item.SmartPatternItem;
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
    public static final DeferredBlock<PatternProviderBlock> CONTROLLER =
            BLOCKS.registerBlock("smart_controller", PatternProviderBlock::new);
    public static final DeferredItem<BlockItem> CONTROLLER_ITEM =
            ITEMS.registerSimpleBlockItem(CONTROLLER);
    public static final DeferredItem<SmartPatternItem> SMART_PATTERN =
            ITEMS.registerItem(
                    "smart_pattern", SmartPatternItem::new, properties -> properties.stacksTo(1));
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

    public Aesi(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(
                (RegisterCapabilitiesEvent event) -> {
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
                        event.accept(SMART_PATTERN);
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
}

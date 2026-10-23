// SPDX-License-Identifier: MIT
package dev.aesi.item;

import appeng.api.stacks.*;

import dev.aesi.planner.Strategy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

public final class SmartPatternItem extends Item implements appeng.api.implementations.menuobjects.IMenuItem {
    public SmartPatternItem(Properties properties) {
        super(properties);
    }

    public static AEKey target(ItemStack stack) {
        var contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return null;
        var target = contents.nonEmptyItems().iterator();
        return target.hasNext() ? GenericStack.fromItemStack(target.next().create()).what() : null;
    }

    public static Strategy strategy(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        try {
            return Strategy.valueOf(data.getString("aesi_strategy").orElse("BALANCED"));
        } catch (IllegalArgumentException invalid) {
            return Strategy.BALANCED;
        }
    }

    public static void encode(ItemStack stack, ItemStack target, Strategy strategy) {
        stack.set(
                DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(target.copyWithCount(1))));
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString("aesi_strategy", strategy.name());
        tag.putString("aesi_schema", "1");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void encode(ItemStack stack, AEKey target, Strategy strategy) {
        encode(stack, GenericStack.wrapInItemStack(target, 1), strategy);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown() && target(stack) != null) {
                encode(stack, target(stack), strategy(stack).next());
                player.sendOverlayMessage(
                        Component.translatable(
                                "message.aesi.strategy",
                                Component.translatable(
                                        "strategy.aesi."
                                                + strategy(stack)
                                                        .name()
                                                        .toLowerCase(java.util.Locale.ROOT))));
            } else {
                var sample =
                        player.getItemInHand(
                                hand == InteractionHand.MAIN_HAND
                                        ? InteractionHand.OFF_HAND
                                        : InteractionHand.MAIN_HAND);
                if (!sample.isEmpty() && !(sample.getItem() instanceof SmartPatternItem)) {
                    var contained =
                            appeng.api.behaviors.ContainerItemStrategies.getContainedStack(sample);
                    var resource =
                            contained != null ? contained : GenericStack.fromItemStack(sample);
                    encode(stack, resource.what(), strategy(stack));
                    player.sendOverlayMessage(
                            Component.translatable(
                                    "message.aesi.encoded", resource.what().getDisplayName()));
                } else appeng.menu.MenuOpener.open(dev.aesi.Aesi.SMART_PATTERN_MENU.get(),player,
                        appeng.menu.locator.MenuLocators.forHand(player,hand));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override public appeng.api.implementations.menuobjects.ItemMenuHost<?> getMenuHost(Player player,
            appeng.menu.locator.ItemMenuHostLocator locator,net.minecraft.world.phys.BlockHitResult hit) {
        return new appeng.api.implementations.menuobjects.ItemMenuHost<>(this,player,locator);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag flag) {
        var target = target(stack);
        builder.accept(
                Component.translatable(
                        target == null ? "tooltip.aesi.blank" : "tooltip.aesi.target",
                        target == null ? Component.empty() : target.getDisplayName()));
        builder.accept(
                Component.translatable(
                        "tooltip.aesi.strategy",
                        Component.translatable(
                                "strategy.aesi."
                                        + strategy(stack)
                                                .name()
                                                .toLowerCase(java.util.Locale.ROOT))));
        builder.accept(Component.translatable("tooltip.aesi.native"));
    }
}

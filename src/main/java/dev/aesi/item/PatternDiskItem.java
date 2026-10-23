// SPDX-License-Identifier: MIT
package dev.aesi.item;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.ArrayList;
import java.util.function.Consumer;
import dev.aesi.multiblock.DiskArrayBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;

public final class PatternDiskItem extends ServerBlockItem implements IMenuItem {
    /** Tier given to the drive that receives patterns stored by arrays from before drives existed (24 fit in 32). */
    public static final int LEGACY_TIER = 3;
    // Keep the existing item registry ID so old portable patterns are not lost.
    public PatternDiskItem(Properties properties) { super(dev.aesi.Aesi.DISK_ARRAY.get(), properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        var selection=dev.aesi.multiblock.RackServers.select(context.getLevel(),
                new BlockHitResult(context.getClickLocation(),context.getClickedFace(),context.getClickedPos(),false));
        if(selection!=null)return dev.aesi.multiblock.RackServers.install(context,false);
        if(dev.aesi.multiblock.ArrayMounts.empty(context.getLevel().getBlockState(context.getClickedPos())))
            return DiskArrayBlock.install(context.getLevel(),context.getClickedPos(),context.getItemInHand(),context.getPlayer());
        // A mounted array has no item to open; drives are changed only after it is pulled out.
        if(context.getLevel().getBlockState(context.getClickedPos()).is(dev.aesi.Aesi.DISK_ARRAY.get())){
            if(!context.getLevel().isClientSide()&&context.getPlayer()!=null)
                context.getPlayer().sendOverlayMessage(Component.translatable("message.aesi.array_mounted"));
            return InteractionResult.SUCCESS;
        }
        if(dev.aesi.multiblock.Workstation.formed(context.getLevel().getBlockState(context.getClickedPos())))
            return InteractionResult.FAIL;
        return super.useOn(context);
    }
    @Override public InteractionResult place(BlockPlaceContext context) {
        if(dev.aesi.multiblock.ArrayMounts.empty(context.getLevel().getBlockState(context.getClickedPos())))
            return DiskArrayBlock.install(context.getLevel(),context.getClickedPos(),context.getItemInHand(),context.getPlayer());
        return super.place(context);
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            var stack = player.getItemInHand(hand);
            normalize(stack);
            MenuOpener.open(dev.aesi.Aesi.DRIVE_ARRAY_MENU.get(), player, MenuLocators.forHand(player, hand));
        }
        player.swing(hand);
        return InteractionResult.SUCCESS;
    }
    @Override public ItemMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator, BlockHitResult hit) {
        return new ItemMenuHost<>(this, player, locator);
    }

    /** Rewrites a legacy array stack in place so its container holds only drives. */
    public static void normalize(ItemStack stack) {
        var contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        var normalized = normalize(contents);
        if (!normalized.equals(contents)) stack.set(DataComponents.CONTAINER, normalized);
    }

    /**
     * Returns contents holding at most {@link DriveTiers#SLOTS} drives. Patterns stored directly by an old array
     * move, keeping their slot positions, onto a new tier-3 drive in the first free drive slot.
     */
    public static ItemContainerContents normalize(ItemContainerContents contents) {
        var patterns = NonNullList.withSize(contents.getSlots(), ItemStack.EMPTY);
        var slots = NonNullList.withSize(DriveTiers.SLOTS, ItemStack.EMPTY);
        var loose = new ArrayList<ItemStack>();
        boolean legacy = false;
        for (int i = 0; i < contents.getSlots(); i++) {
            var stack = contents.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            // Drives already in a valid slot stay there; others take the next free slot.
            if (stack.getItem() instanceof PatternDriveItem) { if (i < DriveTiers.SLOTS) slots.set(i, stack); else loose.add(stack); }
            else { patterns.set(i, stack); legacy = true; }
        }
        if (!legacy && loose.isEmpty()) return contents;
        for (var drive : loose) if (!place(slots, drive)) return contents;
        var base = ItemContainerContents.fromItems(slots);
        if (!legacy) return base;
        var migrated = migrate(base, ItemContainerContents.fromItems(patterns));
        return migrated == null ? contents : migrated;
    }

    /**
     * Adds legacy patterns, keeping their positions, as a new tier-3 drive in the first free slot of {@code drives}.
     * Returns null when every drive slot is taken, which old arrays (patterns only, no drives) never are.
     */
    public static ItemContainerContents migrate(ItemContainerContents drives, ItemContainerContents legacyPatterns) {
        var slots = NonNullList.withSize(DriveTiers.SLOTS, ItemStack.EMPTY);
        drives.copyInto(slots);
        int cap = DriveTiers.capacity(LEGACY_TIER);
        var onDrive = NonNullList.withSize(cap, ItemStack.EMPTY);
        var overflow = new ArrayList<ItemStack>();
        for (int i = 0; i < legacyPatterns.getSlots(); i++) {
            var stack = legacyPatterns.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (i < cap) onDrive.set(i, stack); else overflow.add(stack);
        }
        for (var stack : overflow) for (int i = 0; i < cap; i++) if (onDrive.get(i).isEmpty()) { onDrive.set(i, stack); break; }
        var drive = PatternDriveItem.forTier(LEGACY_TIER);
        drive.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(onDrive));
        return place(slots, drive) ? ItemContainerContents.fromItems(slots) : null;
    }

    private static boolean place(NonNullList<ItemStack> slots, ItemStack drive) {
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).isEmpty()) { slots.set(i, drive); return true; }
        return false;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        var contents = normalize(stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
        int used = 0, capacity = 0;
        for (int i = 0; i < DriveTiers.SLOTS; i++) {
            var drive = i < contents.getSlots() ? contents.getStackInSlot(i) : ItemStack.EMPTY;
            if (drive.isEmpty()) {
                lines.accept(Component.translatable("tooltip.aesi.array_slot_empty", i + 1));
                continue;
            }
            int driveUsed = PatternDriveItem.used(drive), driveCap = PatternDriveItem.capacity(drive);
            used += driveUsed; capacity += driveCap;
            lines.accept(Component.translatable("tooltip.aesi.array_slot", i + 1, drive.getHoverName(), driveUsed, driveCap));
        }
        lines.accept(Component.translatable("tooltip.aesi.pattern_disk", used, capacity));
        lines.accept(Component.translatable("tooltip.aesi.disk_portable"));
        lines.accept(Component.translatable("message.aesi.server_mount"));
    }
}

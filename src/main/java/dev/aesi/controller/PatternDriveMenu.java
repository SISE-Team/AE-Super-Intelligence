// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.inventories.InternalInventory;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.ClientActionKey;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;
import dev.aesi.Aesi;
import dev.aesi.item.DriveTiers;
import dev.aesi.item.PatternDriveItem;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Lists the patterns on the held drive, 36 per page. */
public final class PatternDriveMenu extends HeldItemMenu {
    public static final SlotSemantic PATTERNS = SlotSemantics.register("AESI_DRIVE_PATTERNS", false, 100);
    public static final int PAGE_SIZE = 36;
    private static final ClientActionKey<Integer> PAGE_ACTION = new ClientActionKey<>("aesi_drive_page");
    private final List<AppEngSlot> patterns = new ArrayList<>();
    @GuiSync(30) public int capacity;
    @GuiSync(31) public int used;
    @GuiSync(32) public int page;

    public PatternDriveMenu(int id, Inventory playerInventory, ItemMenuHost<?> host) {
        super(Aesi.PATTERN_DRIVE_MENU.get(), id, playerInventory, host);
        capacity = PatternDriveItem.capacity(host.getItemStack());
        registerClientAction(PAGE_ACTION, ByteBufCodecs.VAR_INT, this::setPage);
        InternalInventory drive = contents(DriveTiers.capacity(DriveTiers.TIERS), PatternDriveItem::accepts);
        InternalInventory window = isClientSide() ? new appeng.util.inv.AppEngInternalInventory(PAGE_SIZE)
                : new PageWindow(drive, PAGE_SIZE, this::driveIndex);
        for (int i = 0; i < PAGE_SIZE; i++) {
            final int slot = i;
            var s = new AppEngSlot(window, i) {
                @Override public boolean isActive() { return super.isActive() && slotAvailable(slot); }
                @Override public boolean mayPlace(ItemStack stack) { return slotAvailable(slot) && PatternDriveItem.accepts(stack) && super.mayPlace(stack); }
                @Override public boolean mayPickup(Player player) { return slotAvailable(slot) && super.mayPickup(player); }
            };
            patterns.add(s);
            addSlot(s, PATTERNS);
        }
        createPlayerInventorySlots(playerInventory);
    }

    /** Index on the drive shown by window slot {@code slot}, or -1. */
    public int driveIndex(int slot) {
        int at = page * PAGE_SIZE + slot;
        return slot >= 0 && slot < PAGE_SIZE && at < capacity ? at : -1;
    }
    public boolean slotAvailable(int slot) { return driveIndex(slot) >= 0; }
    public int pageCount() { return DriveTiers.pages(capacity, PAGE_SIZE); }
    public List<AppEngSlot> patternSlots() { return List.copyOf(patterns); }

    public void setPage(int requested) {
        if (requested < 0 || requested >= pageCount()) return;
        if (isClientSide()) sendClientAction(PAGE_ACTION, requested);
        else { page = requested; broadcastChanges(); }
    }

    @Override public void broadcastChanges() {
        if (isServerSide() && host.isValid()) {
            var stack = host.getItemStack();
            capacity = PatternDriveItem.capacity(stack);
            used = PatternDriveItem.used(stack);
            page = DriveTiers.clampPage(page, capacity, PAGE_SIZE);
        }
        super.broadcastChanges();
    }
}

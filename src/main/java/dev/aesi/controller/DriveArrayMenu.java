// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;
import dev.aesi.Aesi;
import dev.aesi.item.DriveTiers;
import dev.aesi.item.PatternDriveItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.ArrayList;
import java.util.List;

/** The four drive slots of the held disk array. */
public final class DriveArrayMenu extends HeldItemMenu {
    public static final SlotSemantic DRIVES = SlotSemantics.register("AESI_ARRAY_DRIVES", false, 100);
    private final List<AppEngSlot> drives = new ArrayList<>();
    @GuiSync(30) public int capacity;
    @GuiSync(31) public int used;

    public DriveArrayMenu(int id, Inventory playerInventory, ItemMenuHost<?> host) {
        super(Aesi.DRIVE_ARRAY_MENU.get(), id, playerInventory, host);
        var inventory = contents(DriveTiers.SLOTS, stack -> stack.getItem() instanceof PatternDriveItem);
        for (int i = 0; i < DriveTiers.SLOTS; i++) {
            var slot = new AppEngSlot(inventory, i);
            drives.add(slot);
            addSlot(slot, DRIVES);
        }
        createPlayerInventorySlots(playerInventory);
    }
    public List<AppEngSlot> driveSlots() { return List.copyOf(drives); }

    @Override public void broadcastChanges() {
        if (isServerSide() && host.isValid()) {
            var contents = host.getItemStack().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            int cap = 0, count = 0;
            for (int i = 0; i < Math.min(DriveTiers.SLOTS, contents.getSlots()); i++) {
                var drive = contents.getStackInSlot(i);
                cap += PatternDriveItem.capacity(drive);
                count += PatternDriveItem.used(drive);
            }
            capacity = cap;
            used = count;
        }
        super.broadcastChanges();
    }
}

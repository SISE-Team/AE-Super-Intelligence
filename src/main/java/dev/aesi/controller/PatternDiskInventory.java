// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import dev.aesi.Aesi;
import dev.aesi.item.SmartPatternItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Each physical disk owns its row of patterns. Banks are decoded views, never separately saved or dropped. */
public final class PatternDiskInventory {
    private final AppEngInternalInventory drives;
    private final AppEngInternalInventory[] banks = new AppEngInternalInventory[SmartLogic.DISK_SLOTS];
    private final InternalInventory patterns;
    private final SmartLogic logic;
    private boolean writingBank;

    public PatternDiskInventory(SmartLogic logic, InternalInventory internalPatterns) {
        this.logic = logic;
        drives = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override public void saveChangedInventory(AppEngInternalInventory inv) {
                // A bank commit already has the authoritative cached state. Reloading
                // other banks here would erase their changes in a shared transaction.
                if (!writingBank) reload();
                changed();
            }
            @Override public boolean isClientSide() { return logic.isClientSide(); }
        }, SmartLogic.DISK_SLOTS, 1);
        // Client disk-slot synchronization must also populate the corresponding row.
        drives.setEnableClientEvents(true);
        drives.setFilter(new IAEItemFilter() {
            @Override public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return false; // Old internal modules may only be withdrawn and mounted as blocks.
            }
        });
        for (int i = 0; i < banks.length; i++) {
            final int bay = i;
            banks[i] = new AppEngInternalInventory(new InternalInventoryHost() {
                @Override public void saveChangedInventory(AppEngInternalInventory inv) {
                    var disk = drives.getStackInSlot(bay);
                    if (disk.is(Aesi.PATTERN_DISK.get())) {
                        var updated = disk.copy();
                        updated.set(DataComponents.CONTAINER, inv.toItemContainerContents());
                        writingBank = true;
                        try { drives.setItemDirect(bay, updated); }
                        finally { writingBank = false; }
                    }
                }
                @Override public boolean isClientSide() { return logic.isClientSide(); }
            }, SmartLogic.BASE_CAPACITY, 1);
            banks[i].setFilter(new IAEItemFilter() {
                @Override public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                    return drives.getStackInSlot(bay).is(Aesi.PATTERN_DISK.get())
                            && stack.getItem() instanceof SmartPatternItem;
                }
            });
        }
        var inventories = new InternalInventory[banks.length + 1];
        inventories[0] = internalPatterns;
        System.arraycopy(banks, 0, inventories, 1, banks.length);
        patterns = new CombinedInternalInventory(inventories);
    }

    private void changed() {
        logic.markDirty();
        if (!logic.isClientSide()) logic.saveChangedInventory(drives);
    }

    public AppEngInternalInventory drives() { return drives; }
    public InternalInventory patterns() { return patterns; }

    public int mask() {
        int mask = 0;
        for (int i = 0; i < banks.length; i++) if (drives.getStackInSlot(i).is(Aesi.PATTERN_DISK.get())) mask |= 1 << i;
        return mask;
    }

    public void reload() {
        for (int i = 0; i < banks.length; i++) {
            if (banks[i] == null) continue;
            var disk = drives.getStackInSlot(i);
            banks[i].fromItemContainerContents(disk.is(Aesi.PATTERN_DISK.get())
                    ? disk.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                    : ItemContainerContents.EMPTY);
        }
    }
}

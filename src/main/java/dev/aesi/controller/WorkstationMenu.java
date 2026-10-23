// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.guisync.ClientActionKey;
import appeng.menu.slot.AppEngSlot;
import dev.aesi.item.DriveTiers;
import dev.aesi.multiblock.DiskArrayEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.ArrayList;

/**
 * One page of 45 slots over the controller's pattern view. Usable slots (base nine, then each mounted
 * array's drive capacity in bay order) are packed, so page {@code p} slot {@code i} is usable slot {@code p*45+i}.
 * Both sides derive the mapping from the synchronized page and drive tiers.
 */
public final class WorkstationMenu extends AEBaseMenu {
    public static final SlotSemantic DISK = SlotSemantics.register("AESI_DISK", false, 100);
    private static final SlotSemantic[] DISK_COLUMNS = { DISK,
            SlotSemantics.register("AESI_DISK_1", false, 100),
            SlotSemantics.register("AESI_DISK_2", false, 100),
            SlotSemantics.register("AESI_DISK_3", false, 100) };
    public static final SlotSemantic PATTERN_PAGE = SlotSemantics.register("AESI_PATTERN_PAGE_0", false, 100);
    public static final int PAGE_SIZE = 45;
    public static final int MAX_PAGES = DriveTiers.pages(SmartLogic.MAX_CAPACITY, PAGE_SIZE);
    private static final int BAY_CHARS = SmartLogic.ARRAY_SLOTS * DiskArrayEntity.DRIVE_SLOTS;
    private static final ClientActionKey<Integer> PAGE_ACTION = new ClientActionKey<>("aesi_page");
    private static final ClientActionKey<Integer> TAB_ACTION = new ClientActionKey<>("aesi_tab");
    private static final SlotSemantic[] MODULE_ROWS = {
            SlotSemantics.register("AESI_MODULE_ROW_0",false,100),
            SlotSemantics.register("AESI_MODULE_ROW_1",false,100),
            SlotSemantics.register("AESI_MODULE_ROW_2",false,100)};
    private final SmartLogic logic;
    private final List<AppEngSlot> patterns = new ArrayList<>();
    private final List<AppEngSlot> drives = new ArrayList<>();
    private final List<AppEngSlot> modules = new ArrayList<>();
    @GuiSync(20) public int capacity = SmartLogic.BASE_CAPACITY;
    @GuiSync(21) public int diskCount;
    @GuiSync(22) public int diskMask;
    @GuiSync(23) public int page;
    @GuiSync(24) public int legacyMask;
    @GuiSync(25) public String bays = "-".repeat(BAY_CHARS);
    @GuiSync(26) public int tab;
    @GuiSync(27) public int computeMask;
    @GuiSync(28) public int moduleCount;
    @GuiSync(29) public String missingModules = "";
    @GuiSync(30) public int activity;
    @GuiSync(31) public int bladeMask;

    public WorkstationMenu(MenuType<?> type, int id, Inventory playerInventory, SmartController host) {
        super(type, id, playerInventory, host);
        logic = (SmartLogic) host.getLogic();
        registerClientAction(PAGE_ACTION, ByteBufCodecs.VAR_INT, this::setPage);
        registerClientAction(TAB_ACTION,ByteBufCodecs.VAR_INT,this::setTab);
        for (int i = 0; i < SmartLogic.DISK_SLOTS; i++) {
            final int bay = i;
            var slot = new AppEngSlot(logic.diskInventory(), i) {
                @Override public boolean isActive(){return super.isActive()&&(legacyMask&(1<<bay))!=0;}
                @Override public boolean mayPlace(ItemStack stack){return false;}
            };
            slot.setEmptyTooltip(() -> List.of(Component.translatable("gui.aesi.disk_bay", bay + 1),
                    Component.translatable("gui.aesi.legacy_arrays")));
            drives.add(slot);
            addSlot(slot, DISK_COLUMNS[i % 4]);
        }
        // The client mirrors exactly the 45 visible stacks; a client-side remap could show stale cached stacks.
        appeng.api.inventories.InternalInventory window = isClientSide()
                ? new appeng.util.inv.AppEngInternalInventory(PAGE_SIZE)
                : new PageWindow(logic.getPatternInv(), PAGE_SIZE, this::patternIndex);
        for (int i = 0; i < PAGE_SIZE; i++) {
            final int windowSlot = i;
            var slot = new AppEngSlot(window, i) {
                @Override public boolean isActive() { return super.isActive() && tab==0; }
                @Override public boolean mayPlace(ItemStack stack) {
                    return slotAvailable(windowSlot) && super.mayPlace(stack);
                }
                @Override public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
                    return slotAvailable(windowSlot) && super.mayPickup(player);
                }
            };
            slot.setEmptyTooltip(() -> List.of(Component.translatable(
                    slotAvailable(windowSlot) ? "gui.aesi.pattern_slot" : "gui.aesi.locked_slot")));
            patterns.add(slot);
            addSlot(slot, PATTERN_PAGE);
        }
        appeng.api.inventories.InternalInventory compute = isClientSide()
                ? new appeng.util.inv.AppEngInternalInventory(null,ComputeInventory.SIZE,1) : logic.computeInventory();
        for(int i=0;i<ComputeInventory.SIZE;i++) {
            final int bay=i/4;
            final int blade=i;
            var slot=new AppEngSlot(compute,i) {
                @Override public boolean isActive(){return super.isActive()&&tab==1;}
                @Override public boolean mayPlace(ItemStack stack){return (bladeMask&(1<<blade))!=0
                        &&!dev.aesi.item.ModuleUpgradeItem.module(stack).isEmpty()&&super.mayPlace(stack);}
                @Override public boolean mayPickup(net.minecraft.world.entity.player.Player player){return (bladeMask&(1<<blade))!=0&&super.mayPickup(player);}
            };
            slot.setEmptyTooltip(()->List.of(Component.translatable("tooltip.aesi.module_install")));
            modules.add(slot);addSlot(slot,MODULE_ROWS[bay]);
        }
        var returns = logic.getReturnInv().createMenuWrapper();
        for (int i = 0; i < Math.min(9, returns.size()); i++)
            addSlot(new AppEngSlot(returns, i), SlotSemantics.STORAGE);
        createPlayerInventorySlots(playerInventory);
    }

    /** Capacity of a bay according to a tier string: 0 when the bay is absent. */
    public static int bayCapacity(String bays, int bay) {
        int sum = 0;
        for (int slot = 0; slot < DiskArrayEntity.DRIVE_SLOTS; slot++) {
            int at = bay * DiskArrayEntity.DRIVE_SLOTS + slot;
            char c = at < bays.length() ? bays.charAt(at) : '-';
            if (c >= '0' && c <= '9') sum += DriveTiers.capacity(c - '0');
        }
        return sum;
    }
    /** Controller slot of the {@code packed}-th usable slot, or -1 past the capacity. */
    public static int controllerIndex(String bays, int packed) {
        if (packed < 0) return -1;
        if (packed < SmartLogic.BASE_CAPACITY) return packed;
        packed -= SmartLogic.BASE_CAPACITY;
        for (int bay = 0; bay < SmartLogic.ARRAY_SLOTS; bay++) {
            int cap = bayCapacity(bays, bay);
            if (packed < cap) return SmartLogic.bayStart(bay) + packed;
            packed -= cap;
        }
        return -1;
    }

    /** Controller slot shown by window slot {@code slot} on the current page, or -1. */
    public int patternIndex(int slot) {
        return slot < 0 || slot >= PAGE_SIZE ? -1 : controllerIndex(bays, page * PAGE_SIZE + slot);
    }
    public boolean slotAvailable(int slot) { return patternIndex(slot) >= 0; }
    public int pageCount() { return DriveTiers.pages(capacity, PAGE_SIZE); }

    public List<AppEngSlot> patternSlots() { return List.copyOf(patterns); }
    public List<AppEngSlot> diskSlots() { return List.copyOf(drives); }
    public List<AppEngSlot> moduleSlots() { return List.copyOf(modules); }
    public void setTab(int value) {
        if(value<0||value>1)return;
        if(isClientSide())sendClientAction(TAB_ACTION,value);
        else {tab=value;broadcastChanges();}
    }

    public void setPage(int requested) {
        if (requested < 0 || requested >= pageCount()) return;
        if (isClientSide()) sendClientAction(PAGE_ACTION, requested);
        else {
            page = requested;
            broadcastChanges();
        }
    }

    @Override public void broadcastChanges() {
        if (isServerSide()) {
            capacity = logic.patternCapacity();
            diskCount = logic.diskCount();
            diskMask = logic.diskMask();
            bays = logic.bayTiers();
            computeMask=logic.computeInventory().mask();
            bladeMask=logic.computeInventory().bladeMask();
            moduleCount=logic.installedModules().size();
            missingModules=logic.missingModules().stream().sorted().limit(12).collect(java.util.stream.Collectors.joining(", "));
            activity=logic.runtimeMode();
            // Removing an array while the last page is open must not leave a page past the end.
            page = DriveTiers.clampPage(page, capacity, PAGE_SIZE);
            legacyMask=0;
            for(int i=0;i<SmartLogic.DISK_SLOTS;i++)if(!logic.diskInventory().getStackInSlot(i).isEmpty())legacyMask|=1<<i;
        }
        super.broadcastChanges();
    }
}

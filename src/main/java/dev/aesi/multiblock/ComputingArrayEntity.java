// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.api.inventories.InternalInventory;
import appeng.util.inv.*;
import appeng.util.inv.filter.IAEItemFilter;
import dev.aesi.Aesi;
import dev.aesi.controller.SmartLogic;
import dev.aesi.item.ModuleUpgradeItem;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** A rack column owns up to four removable 2U servers; each server carries one dataset. */
public final class ComputingArrayEntity extends AEBaseBlockEntity implements InternalInventoryHost {
    public static final int SLOTS = 4;
    public static final java.util.List<Integer> SEGMENTS = java.util.List.of(4, 8, 12);
    private final AppEngInternalInventory upgrades = new AppEngInternalInventory(this, SLOTS, 1);
    private int blades=15; // Older array stacks represented four permanently fitted blades.
    public ComputingArrayEntity(BlockPos pos, BlockState state) {
        super(Aesi.COMPUTING_ARRAY_ENTITY.get(), pos, state);
        blades=defaultBlades();
        upgrades.setFilter(new IAEItemFilter() {
            @Override public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return hasBlade(slot) && !ModuleUpgradeItem.module(stack).isEmpty();
            }
        });
    }
    private int defaultBlades(){return getBlockState().is(Aesi.COMPUTE_SERVER_BLOCK.get())?1:15;}
    @Override protected net.minecraft.world.item.Item getItemFromBlockEntity(){
        return getBlockState().is(Aesi.COMPUTE_SERVER_BLOCK.get())?Aesi.COMPUTE_SERVER.get():Aesi.COMPUTING_ARRAY_ITEM.get();
    }
    public AppEngInternalInventory upgrades() { return upgrades; }
    public int bladeMask(){return blades;}
    public boolean hasBlade(int slot){return slot>=0&&slot<SLOTS&&(blades&(1<<slot))!=0;}
    public void emptyChassis(){blades=0;upgrades.clear();saveChangedInventory(upgrades);}
    public void insertBlade(int slot,ItemStack item){
        if(hasBlade(slot))throw new IllegalStateException("Occupied blade slot");
        blades|=1<<slot;
        var contents=item.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);
        upgrades.setItemDirect(slot,contents.getSlots()==0?ItemStack.EMPTY:contents.getStackInSlot(0).copy());
        saveChangedInventory(upgrades);
    }
    public ItemStack blade(int slot){
        if(!hasBlade(slot))return ItemStack.EMPTY;
        var item=Aesi.COMPUTE_SERVER.get().getDefaultInstance();
        if(!upgrades.getStackInSlot(slot).isEmpty())item.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(java.util.List.of(upgrades.getStackInSlot(slot).copy())));
        return item;
    }
    public ItemStack removeBlade(int slot){
        var item=blade(slot);if(item.isEmpty())return item;
        blades&=~(1<<slot);upgrades.setItemDirect(slot,ItemStack.EMPTY);saveChangedInventory(upgrades);return item;
    }
    @Override public void clearRemoved(){super.clearRemoved();scheduleInit();}
    @Override public void onReady(){super.onReady();ServerBody.onReady(level,worldPosition,getBlockState());}
    @Override public void setRemoved() {
        var owner = level == null ? null : Workstation.owner(level, worldPosition, getBlockState());
        super.setRemoved();
        if (owner != null) ((SmartLogic)owner.getLogic()).modulesChanged();
    }
    @Override public void saveChangedInventory(AppEngInternalInventory inv) {
        setChanged();
        if(level!=null&&!level.isClientSide())markForUpdate();
        var owner = level == null ? null : Workstation.owner(level, worldPosition, getBlockState());
        if (owner != null) ((SmartLogic) owner.getLogic()).modulesChanged();
    }
    public ItemStack portable() {
        var stack = Aesi.STRATEGY_ARRAY.get().asItem().getDefaultInstance();
        if (!upgrades.isEmpty()) stack.set(DataComponents.CONTAINER, upgrades.toItemContainerContents());
        net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putInt("aesi_blades",blades));
        return stack;
    }
    public void restore(ItemStack stack) {
        if(stack.is(Aesi.COMPUTE_SERVER.get())){emptyChassis();insertBlade(0,stack);return;}
        blades=stack.getOrDefault(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getIntOr("aesi_blades",15)&15;
        upgrades.fromItemContainerContents(stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
        saveChangedInventory(upgrades);
    }
    @Override protected void saveAdditional(ValueOutput out) { super.saveAdditional(out); upgrades.writeToNBT(out,"upgrades");out.putInt("blades",blades); }
    @Override public void loadTag(ValueInput in) { super.loadTag(in);blades=in.getIntOr("blades",defaultBlades())&15;upgrades.readFromNBT(in,"upgrades"); }
    @Override public void clearContent() { blades=0;upgrades.clear(); }
    @Override protected void writeToStream(net.minecraft.network.RegistryFriendlyByteBuf data){super.writeToStream(data);data.writeByte(blades);}
    @Override protected boolean readFromStream(net.minecraft.network.RegistryFriendlyByteBuf data){
        boolean changed=super.readFromStream(data);int next=data.readUnsignedByte();changed|=next!=blades;blades=next;return changed;
    }
    @Override public void exportSettings(appeng.util.SettingsFrom from, DataComponentMap.Builder builder,
            net.minecraft.world.entity.player.Player player) {
        super.exportSettings(from,builder,player);
        if(from==appeng.util.SettingsFrom.DISMANTLE_ITEM) {
            builder.set(DataComponents.CONTAINER,upgrades.toItemContainerContents());
            var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("aesi_blades",blades);
            builder.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
        }
    }
    @Override public void importSettings(appeng.util.SettingsFrom from, DataComponentMap data,
            net.minecraft.world.entity.player.Player player) {
        super.importSettings(from,data,player);
        if(from==appeng.util.SettingsFrom.DISMANTLE_ITEM) {
            blades=data.getOrDefault(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getIntOr("aesi_blades",defaultBlades())&15;
            upgrades.fromItemContainerContents(data.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY));
            saveChangedInventory(upgrades);
        }
    }
}

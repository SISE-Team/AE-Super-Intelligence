// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import appeng.api.inventories.InternalInventory;
import dev.aesi.Aesi;
import dev.aesi.controller.MappedResourceHandler;
import dev.aesi.controller.SmartController;
import dev.aesi.controller.SmartLogic;
import dev.aesi.item.DriveTiers;
import dev.aesi.item.PatternDiskItem;
import dev.aesi.item.PatternDriveItem;
import dev.aesi.item.SmartPatternItem;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import java.util.Arrays;

/**
 * The mounted block owns its drives, and each drive owns its patterns. Removing a controller never drops or clears them.
 * Every drive is decoded into a journaled bank; a bank writes back to its drive on change.
 */
public final class DiskArrayEntity extends AEBaseBlockEntity implements InternalInventoryHost {
    public static final int MOUNTS=6, DRIVE_SLOTS=DriveTiers.SLOTS, MAX_CAPACITY=DriveTiers.ARRAY_MAX;
    private static final int BANK_SIZE=DriveTiers.capacity(DriveTiers.TIERS);
    private final AppEngInternalInventory drives=new AppEngInternalInventory(this,DRIVE_SLOTS,1);
    private final AppEngInternalInventory[] banks=new AppEngInternalInventory[DRIVE_SLOTS];
    private final InternalInventory patterns=new Packed();
    private final int[] tiers=new int[DRIVE_SLOTS], fills=new int[DRIVE_SLOTS];
    private boolean writingBank;

    public DiskArrayEntity(BlockPos pos,BlockState state){
        super(Aesi.DISK_ARRAY_ENTITY.get(),pos,state);
        drives.setFilter(new IAEItemFilter(){
            @Override public boolean allowInsert(InternalInventory inv,int slot,ItemStack stack){return stack.getItem() instanceof PatternDriveItem;}
        });
        for(int i=0;i<DRIVE_SLOTS;i++){
            final int slot=i;
            banks[i]=new AppEngInternalInventory(new InternalInventoryHost(){
                @Override public void saveChangedInventory(AppEngInternalInventory inv){
                    var drive=drives.getStackInSlot(slot);
                    if(!(drive.getItem() instanceof PatternDriveItem))return;
                    var updated=drive.copy();
                    updated.set(DataComponents.CONTAINER,inv.toItemContainerContents());
                    writingBank=true;
                    try{drives.setItemDirect(slot,updated);}
                    finally{writingBank=false;}
                }
                @Override public boolean isClientSide(){return DiskArrayEntity.this.isClientSide();}
            },BANK_SIZE,1);
            banks[i].setFilter(new IAEItemFilter(){
                @Override public boolean allowInsert(InternalInventory inv,int index,ItemStack stack){
                    return index<PatternDriveItem.capacity(drives.getStackInSlot(slot))&&stack.getItem() instanceof SmartPatternItem;
                }
            });
        }
    }

    public AppEngInternalInventory drives(){return drives;}
    /** Patterns of all drives packed in slot order; always {@link #MAX_CAPACITY} long, gated by {@link #capacity()}. */
    public InternalInventory patterns(){return patterns;}
    public int capacity(){
        int sum=0;for(var drive:drives)sum+=PatternDriveItem.capacity(drive);
        return sum;
    }
    public boolean hasDrives(){return !drives.isEmpty();}
    public int visualTier(int slot){return tiers[slot];}
    public int visualFill(int slot){return fills[slot];}

    public static BlockPos position(BlockPos head,Direction facing,int bay){
        return Workstation.position(head,facing,Workstation.STORAGE_SEGMENTS.get(bay));
    }
    public static SmartController findOwner(Level level,BlockPos pos){
        if(level==null)return null;
        for(var facing:Direction.Plane.HORIZONTAL)for(int bay=0;bay<MOUNTS;bay++){
            var head=pos.subtract(position(BlockPos.ZERO,facing,bay));
            if(level.hasChunkAt(head)&&level.getBlockEntity(head) instanceof SmartController c
                    &&c.getBlockState().getValue(Workstation.FACING)==facing&&Workstation.problems(c,false).isEmpty())return c;
        }
        return null;
    }
    public SmartController owner(){
        var c=findOwner(level,worldPosition);
        return c!=null&&c.getBlockState().getValue(Workstation.FACING)==getBlockState().getValue(Workstation.FACING)?c:null;
    }

    public ItemStack portable(){var stack=Aesi.PATTERN_DISK.get().getDefaultInstance();if(hasDrives())stack.set(DataComponents.CONTAINER,drives.toItemContainerContents());return stack;}
    public void restore(ItemStack stack){load(stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY));}
    private void load(ItemContainerContents contents){
        drives.fromItemContainerContents(PatternDiskItem.normalize(contents));
        reload();changed();
    }
    private void reload(){
        for(int i=0;i<DRIVE_SLOTS;i++){
            var drive=drives.getStackInSlot(i);
            banks[i].fromItemContainerContents(drive.getItem() instanceof PatternDriveItem
                    ?drive.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY):ItemContainerContents.EMPTY);
        }
    }
    private void changed(){
        setChanged();
        var c=owner();if(c!=null)((SmartLogic)c.getLogic()).markDirty();
        if(refreshVisual()&&!isClientSide())markForUpdate();
    }
    private boolean refreshVisual(){
        var oldTiers=tiers.clone();var oldFills=fills.clone();
        for(int i=0;i<DRIVE_SLOTS;i++){
            var drive=drives.getStackInSlot(i);
            tiers[i]=PatternDriveItem.tier(drive);
            fills[i]=tiers[i]==0?DriveTiers.EMPTY:DriveTiers.fill(PatternDriveItem.used(drive),DriveTiers.capacity(tiers[i]));
        }
        return !Arrays.equals(oldTiers,tiers)||!Arrays.equals(oldFills,fills);
    }
    // Drive slots changed directly (menus, restore) or through a bank write-back.
    @Override public void saveChangedInventory(AppEngInternalInventory inventory){if(!writingBank)reload();changed();}

    @Override protected void writeToStream(RegistryFriendlyByteBuf data){
        super.writeToStream(data);
        for(int i=0;i<DRIVE_SLOTS;i++){data.writeByte(tiers[i]);data.writeByte(fills[i]);}
    }
    @Override protected boolean readFromStream(RegistryFriendlyByteBuf data){
        boolean changed=super.readFromStream(data);
        for(int i=0;i<DRIVE_SLOTS;i++){
            int tier=data.readByte(),fill=data.readByte();
            changed|=tier!=tiers[i]||fill!=fills[i];tiers[i]=tier;fills[i]=fill;
        }
        return changed;
    }
    @Override protected void saveAdditional(ValueOutput output){super.saveAdditional(output);drives.writeToNBT(output,"drives");}
    @Override public void loadTag(ValueInput input){
        super.loadTag(input);
        drives.readFromNBT(input,"drives");
        // Arrays saved before drives existed kept up to 24 patterns directly; move them, in order, onto a drive.
        var legacy=new AppEngInternalInventory(null,24,1);legacy.readFromNBT(input,"patterns");
        if(!legacy.isEmpty()){
            var migrated=PatternDiskItem.migrate(drives.toItemContainerContents(),legacy.toItemContainerContents());
            if(migrated!=null)drives.fromItemContainerContents(migrated);
        }
        reload();refreshVisual();
    }
    @Override public void exportSettings(appeng.util.SettingsFrom from,DataComponentMap.Builder builder,net.minecraft.world.entity.player.Player player){super.exportSettings(from,builder,player);if(from==appeng.util.SettingsFrom.DISMANTLE_ITEM&&hasDrives())builder.set(DataComponents.CONTAINER,drives.toItemContainerContents());}
    @Override public void importSettings(appeng.util.SettingsFrom from,DataComponentMap input,net.minecraft.world.entity.player.Player player){super.importSettings(from,input,player);if(from==appeng.util.SettingsFrom.DISMANTLE_ITEM)load(input.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY));}
    @Override public void clearContent(){drives.clear();reload();changed();}
    @Override public void clearRemoved(){super.clearRemoved();scheduleInit();}
    @Override public void onReady(){super.onReady();ServerBody.onReady(level,worldPosition,getBlockState());}
    @Override public void setRemoved(){var c=owner();super.setRemoved();if(c!=null)((SmartLogic)c.getLogic()).markDirty();}

    /** Resolves a packed index to (drive slot, index on that drive), or null past the installed capacity. */
    private int[] locate(int index){
        int offset=0;
        for(int i=0;i<DRIVE_SLOTS;i++){
            int cap=PatternDriveItem.capacity(drives.getStackInSlot(i));
            if(index<offset+cap)return new int[]{i,index-offset};
            offset+=cap;
        }
        return null;
    }
    private final class Packed implements InternalInventory {
        public int size(){return MAX_CAPACITY;}
        public int getSlotLimit(int slot){return 1;}
        public ItemStack getStackInSlot(int slot){var at=locate(slot);return at==null?ItemStack.EMPTY:banks[at[0]].getStackInSlot(at[1]);}
        public void setItemDirect(int slot,ItemStack stack){var at=locate(slot);if(at!=null)banks[at[0]].setItemDirect(at[1],stack);}
        public boolean isItemValid(int slot,ItemStack stack){var at=locate(slot);return at!=null&&banks[at[0]].isItemValid(at[1],stack);}
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){var at=locate(slot);return at==null?stack:banks[at[0]].insertItem(at[1],stack,simulate);}
        public ItemStack extractItem(int slot,int amount,boolean simulate){var at=locate(slot);return at==null?ItemStack.EMPTY:banks[at[0]].extractItem(at[1],amount,simulate);}
        private final ResourceHandler<ItemResource> handler=new MappedResourceHandler(MAX_CAPACITY,index->{
            var at=locate(index);return at==null?null:new MappedResourceHandler.Target(banks[at[0]].toResourceHandler(),at[1]);
        });
        public ResourceHandler<ItemResource> toResourceHandler(){return handler;}
    }
}

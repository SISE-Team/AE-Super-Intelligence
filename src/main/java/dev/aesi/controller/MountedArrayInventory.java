// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.*;
import dev.aesi.item.SmartPatternItem;
import dev.aesi.multiblock.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Live views of six block-owned banks. No remote contents are serialized in the head.
 * Each bay has a fixed stride of {@link DiskArrayEntity#MAX_CAPACITY} so slot indices never shift;
 * slots past the mounted array's drive capacity are holes.
 */
final class MountedArrayInventory {
    private final SmartController owner;
    private final InternalInventory patterns;
    MountedArrayInventory(SmartController owner,InternalInventory base){
        this.owner=owner;var banks=new InternalInventory[DiskArrayEntity.MOUNTS+1];banks[0]=base;
        for(int bay=0;bay<DiskArrayEntity.MOUNTS;bay++)banks[bay+1]=new Bank(bay);
        patterns=new CombinedInternalInventory(banks);
    }
    InternalInventory patterns(){return patterns;}
    private DiskArrayEntity array(int bay){
        var level=owner.getLevel();if(level==null)return null;
        var facing=owner.getBlockState().getValue(Workstation.FACING);var pos=DiskArrayEntity.position(owner.getBlockPos(),facing,bay);
        return level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof DiskArrayEntity array&&!array.isRemoved()
                &&array.getBlockState().getValue(Workstation.FACING)==facing?array:null;
    }
    int mask(){int mask=0;for(int bay=0;bay<DiskArrayEntity.MOUNTS;bay++)if(array(bay)!=null)mask|=1<<bay;return mask;}
    int capacity(int bay){var array=array(bay);return array==null?0:array.capacity();}
    /** One character per drive slot of every bay: '-' for an absent bay, otherwise the drive tier digit (0 = empty slot). */
    String tiers(){
        var out=new StringBuilder(DiskArrayEntity.MOUNTS*DiskArrayEntity.DRIVE_SLOTS);
        for(int bay=0;bay<DiskArrayEntity.MOUNTS;bay++){
            var array=array(bay);
            for(int slot=0;slot<DiskArrayEntity.DRIVE_SLOTS;slot++)
                out.append(array==null?'-':(char)('0'+dev.aesi.item.PatternDriveItem.tier(array.drives().getStackInSlot(slot))));
        }
        return out.toString();
    }
    private final class Bank implements InternalInventory {
        private final int bay;
        private final AppEngInternalInventory cache=new AppEngInternalInventory(DiskArrayEntity.MAX_CAPACITY);
        // Resolved per call: the handler stays valid when the array in this bay is removed or replaced.
        private final ResourceHandler<ItemResource> handler=new MappedResourceHandler(DiskArrayEntity.MAX_CAPACITY,
                index->new MappedResourceHandler.Target(live().toResourceHandler(),index));
        Bank(int bay){this.bay=bay;cache.setFilter(new appeng.util.inv.filter.IAEItemFilter(){
            @Override public boolean allowInsert(InternalInventory inv,int slot,ItemStack stack){return owner.isClientSide()&&stack.getItem() instanceof SmartPatternItem;}
        });}
        private InternalInventory live(){if(owner.isClientSide())return cache;var array=array(bay);return array==null?cache:array.patterns();}
        private boolean writable(int slot){
            if(owner.isClientSide())return true;
            var array=array(bay);return array!=null&&slot<array.capacity();
        }
        public int size(){return DiskArrayEntity.MAX_CAPACITY;}
        public int getSlotLimit(int slot){return 1;}
        public ItemStack getStackInSlot(int slot){return live().getStackInSlot(slot);}
        public void setItemDirect(int slot,ItemStack stack){if(writable(slot))live().setItemDirect(slot,stack);}
        public boolean isItemValid(int slot,ItemStack stack){return writable(slot)&&stack.getItem() instanceof SmartPatternItem;}
        public ResourceHandler<ItemResource> toResourceHandler(){return handler;}
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return isItemValid(slot,stack)?live().insertItem(slot,stack,simulate):stack;}
        public ItemStack extractItem(int slot,int amount,boolean simulate){return live().extractItem(slot,amount,simulate);}
    }
}

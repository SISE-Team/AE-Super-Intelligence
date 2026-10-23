// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.AppEngInternalInventory;
import dev.aesi.item.ModuleUpgradeItem;
import dev.aesi.multiblock.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import java.util.*;

/** Live fixed slots; missing/unloaded arrays reject writes and do not retain a shadow inventory. */
public final class ComputeInventory implements InternalInventory {
    public static final int SIZE=ComputingArrayEntity.SEGMENTS.size()*ComputingArrayEntity.SLOTS;
    private final SmartController owner;
    private final AppEngInternalInventory empty=new AppEngInternalInventory(0);
    public ComputeInventory(SmartController owner) {this.owner=owner;}
    public ComputingArrayEntity array(int bay) {
        if(bay<0||bay>=ComputingArrayEntity.SEGMENTS.size()||owner.getLevel()==null)return null;
        var level=owner.getLevel();var facing=owner.getBlockState().getValue(Workstation.FACING);
        var pos=Workstation.position(owner.getBlockPos(),facing,ComputingArrayEntity.SEGMENTS.get(bay));
        if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof ComputingArrayEntity array)||array.isRemoved())return null;
        var state=array.getBlockState();
        return Workstation.formed(state)&&!Workstation.ownerPosition(pos,state).equals(owner.getBlockPos())?null:array;
    }
    public int mask() {int mask=0;for(int i=0;i<3;i++)if(array(i)!=null)mask|=1<<i;return mask;}
    public int bladeMask(){int mask=0;for(int i=0;i<3;i++){var a=array(i);if(a!=null)mask|=a.bladeMask()<<(i*4);}return mask;}
    public Set<String> modules() {
        var result=new TreeSet<String>();
        for(int i=0;i<SIZE;i++){var module=ModuleUpgradeItem.module(getStackInSlot(i));if(!module.isEmpty())result.add(module);}
        return Set.copyOf(result);
    }
    public int size(){return SIZE;}
    public int getSlotLimit(int slot){return 1;}
    private InternalInventory inventory(int slot){var array=slot<0||slot>=SIZE?null:array(slot/4);return array==null?empty:array.upgrades();}
    public ItemStack getStackInSlot(int slot){var inv=inventory(slot);return inv.size()==0||(bladeMask()&(1<<slot))==0?ItemStack.EMPTY:inv.getStackInSlot(slot%4);}
    public void setItemDirect(int slot,ItemStack stack){var inv=inventory(slot);if(inv.size()>0)inv.setItemDirect(slot%4,stack);}
    public boolean isItemValid(int slot,ItemStack stack){var inv=inventory(slot);return inv.size()>0&&inv.isItemValid(slot%4,stack);}
    public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return isItemValid(slot,stack)?inventory(slot).insertItem(slot%4,stack,simulate):stack;}
    public ItemStack extractItem(int slot,int amount,boolean simulate){var inv=inventory(slot);return inv.size()==0?ItemStack.EMPTY:inv.extractItem(slot%4,amount,simulate);}
    private final ResourceHandler<ItemResource> handler=new MappedResourceHandler(SIZE,index->{
        var inv=inventory(index);return inv.size()==0?null:new MappedResourceHandler.Target(inv.toResourceHandler(),index%4);
    });
    public ResourceHandler<ItemResource> toResourceHandler(){return handler;}
}

// SPDX-License-Identifier: MIT
package dev.aesi.item;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Live view of a stack's container. Every read decodes the current stack and every write replaces it, so nothing is cached. */
public final class ItemStackInventory implements InternalInventory {
    private final Supplier<ItemStack> stack;
    private final int size;
    private final Predicate<ItemStack> filter;
    private final BooleanSupplier writable;
    public ItemStackInventory(Supplier<ItemStack> stack,int size,Predicate<ItemStack> filter,BooleanSupplier writable){
        this.stack=stack;this.size=size;this.filter=filter;this.writable=writable;
    }
    // Components are immutable, so decoding is memoized by component identity only.
    private ItemContainerContents decodedFrom;
    private NonNullList<ItemStack> decoded;
    private NonNullList<ItemStack> decode(){
        var contents=stack.get().getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);
        if(contents!=decodedFrom){
            decoded=NonNullList.withSize(size,ItemStack.EMPTY);contents.copyInto(decoded);decodedFrom=contents;
        }
        return decoded;
    }
    private NonNullList<ItemStack> read(){
        var items=NonNullList.withSize(size,ItemStack.EMPTY);var current=decode();
        for(int i=0;i<size;i++)items.set(i,current.get(i).copy());
        return items;
    }
    public int size(){return size;}
    public int getSlotLimit(int slot){return 1;}
    public ItemStack getStackInSlot(int slot){return slot<0||slot>=size?ItemStack.EMPTY:decode().get(slot).copy();}
    public void setItemDirect(int slot,ItemStack value){
        var host=stack.get();
        if(slot<0||slot>=size||host.isEmpty()||!writable.getAsBoolean())return;
        var items=read();items.set(slot,value.copy());
        host.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(items));
    }
    public boolean isItemValid(int slot,ItemStack value){return slot>=0&&slot<size&&writable.getAsBoolean()&&filter.test(value);}
    public ItemStack insertItem(int slot,ItemStack value,boolean simulate){return isItemValid(slot,value)?InternalInventory.super.insertItem(slot,value,simulate):value;}
    public ItemStack extractItem(int slot,int amount,boolean simulate){return writable.getAsBoolean()?InternalInventory.super.extractItem(slot,amount,simulate):ItemStack.EMPTY;}
    /** Snapshot handler for completeness; AE2 menus use the slot methods above. */
    public ResourceHandler<ItemResource> toResourceHandler(){
        var copy=new AppEngInternalInventory(size);
        for(int i=0;i<size;i++)copy.setItemDirect(i,getStackInSlot(i));
        return copy.toResourceHandler();
    }
}

// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import java.util.function.IntUnaryOperator;

/** A fixed-size window onto a larger inventory. The mapping is resolved on every access; -1 marks a hole. */
public final class PageWindow implements InternalInventory {
    private final InternalInventory backing;
    private final int size;
    private final IntUnaryOperator map;
    private final ResourceHandler<ItemResource> handler;
    public PageWindow(InternalInventory backing,int size,IntUnaryOperator map){
        this.backing=backing;this.size=size;this.map=map;
        handler=new MappedResourceHandler(size,i->{int at=index(i);return at<0?null:new MappedResourceHandler.Target(backing.toResourceHandler(),at);});
    }
    /** Backing index of window slot {@code slot}, or -1. */
    public int index(int slot){
        if(slot<0||slot>=size)return -1;
        int at=map.applyAsInt(slot);
        return at>=0&&at<backing.size()?at:-1;
    }
    public int size(){return size;}
    public int getSlotLimit(int slot){int at=index(slot);return at<0?1:backing.getSlotLimit(at);}
    public ItemStack getStackInSlot(int slot){int at=index(slot);return at<0?ItemStack.EMPTY:backing.getStackInSlot(at);}
    public void setItemDirect(int slot,ItemStack stack){int at=index(slot);if(at>=0)backing.setItemDirect(at,stack);}
    public boolean isItemValid(int slot,ItemStack stack){int at=index(slot);return at>=0&&backing.isItemValid(at,stack);}
    public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){int at=index(slot);return at<0?stack:backing.insertItem(at,stack,simulate);}
    public ItemStack extractItem(int slot,int amount,boolean simulate){int at=index(slot);return at<0?ItemStack.EMPTY:backing.extractItem(at,amount,simulate);}
    public ResourceHandler<ItemResource> toResourceHandler(){return handler;}
}

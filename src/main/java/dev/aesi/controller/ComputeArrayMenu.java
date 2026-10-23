// SPDX-License-Identifier: MIT
package dev.aesi.controller;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.*;
import appeng.menu.slot.AppEngSlot;
import dev.aesi.Aesi;
import dev.aesi.item.ModuleUpgradeItem;
import net.minecraft.world.entity.player.Inventory;
public final class ComputeArrayMenu extends HeldItemMenu {
    public static final SlotSemantic MODULES=SlotSemantics.register("AESI_COMPUTE_MODULES",false,100);
    public ComputeArrayMenu(int id,Inventory inv,ItemMenuHost<?> host){
        super(Aesi.COMPUTE_ARRAY_MENU.get(),id,inv,host);
        int size=host.getItemStack().is(Aesi.COMPUTE_SERVER.get())?1:4;
        var modules=contents(size,stack->!ModuleUpgradeItem.module(stack).isEmpty());
        for(int i=0;i<size;i++)addSlot(new AppEngSlot(modules,i),MODULES);
        createPlayerInventorySlots(inv);
    }
}

// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.util.inv.AppEngInternalInventory;
import dev.aesi.item.ItemStackInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

/**
 * Base for menus that edit the container of the item they were opened from. The host slot is locked by AE2;
 * this also rejects number-key and offhand swaps onto it, and the menu closes once the item leaves that slot.
 */
abstract class HeldItemMenu extends AEBaseMenu {
    protected final ItemMenuHost<?> host;
    protected HeldItemMenu(MenuType<?> type,int id,Inventory playerInventory,ItemMenuHost<?> host){
        super(type,id,playerInventory,host);
        this.host=host;
    }
    /** Server: a live, cache-free view of the held stack. Client: a plain mirror filled by slot sync. */
    protected InternalInventory contents(int size,Predicate<ItemStack> filter){
        if(isClientSide()){
            var mirror=new AppEngInternalInventory(null,size,1);
            mirror.setFilter(new appeng.util.inv.filter.IAEItemFilter(){
                @Override public boolean allowInsert(InternalInventory inv,int slot,ItemStack stack){return filter.test(stack);}
            });
            return mirror;
        }
        return new ItemStackInventory(host::getItemStack,size,filter,host::isValid);
    }
    @Override public void clicked(int slotId,int button,ContainerInput clickType,Player player){
        // For SWAP the button is the hotbar index, or the offhand slot for the offhand key.
        if(clickType==ContainerInput.SWAP&&isPlayerInventorySlotLocked(button))return;
        super.clicked(slotId,button,clickType,player);
    }
    @Override public boolean stillValid(Player player){return host.isValid()&&super.stillValid(player);}
}

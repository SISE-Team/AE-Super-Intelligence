// SPDX-License-Identifier: MIT
package dev.aesi.controller;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.stacks.GenericStack;
import appeng.menu.*;
import appeng.menu.guisync.*;
import appeng.menu.slot.FakeSlot;
import dev.aesi.Aesi;
import dev.aesi.item.SmartPatternItem;
import dev.aesi.planner.Strategy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** A ghost target, not a real inventory: encoding never consumes or manufactures the sample. */
public final class SmartPatternMenu extends HeldItemMenu {
    public static final SlotSemantic TARGET=SlotSemantics.register("AESI_TARGET",false,100);
    private static final ClientActionKey<Integer> ACTION=new ClientActionKey<>("aesi_pattern_configure");
    @GuiSync(30) public int strategy;
    public SmartPatternMenu(int id,Inventory inv,ItemMenuHost<?> host) {
        super(Aesi.SMART_PATTERN_MENU.get(),id,inv,host);
        registerClientAction(ACTION,ByteBufCodecs.VAR_INT,this::configure);
        var target=contents(1,stack->!(stack.getItem() instanceof SmartPatternItem));
        addSlot(new FakeSlot(target,0) {
            @Override public boolean canSetFilterTo(ItemStack stack){return !(stack.getItem() instanceof SmartPatternItem);}
            @Override public void set(ItemStack stack) {
                if(!canSetFilterTo(stack))return;
                if(stack.isEmpty()){super.set(ItemStack.EMPTY);return;}
                var resource=appeng.api.behaviors.ContainerItemStrategies.getContainedStack(stack);
                if(resource==null)resource=GenericStack.fromItemStack(stack);
                super.set(GenericStack.wrapInItemStack(resource.what(),1));
            }
            @Override public void increase(ItemStack stack){set(stack);}
        },TARGET);
        createPlayerInventorySlots(inv);
    }
    /** -1 clears the ghost; the other values select an explicit bounded strategy. */
    public void configure(int choice) {
        if(choice < -1 || choice>=Strategy.values().length)return;
        if(isClientSide()){sendClientAction(ACTION,choice);return;}
        if(!host.isValid())return;
        var stack=host.getItemStack();
        if(choice==-1)stack.remove(DataComponents.CONTAINER);
        else {
            var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            tag.putString("aesi_strategy",Strategy.values()[choice].name());
            stack.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
        }
        broadcastChanges();
    }
    @Override public void broadcastChanges(){
        if(isServerSide()&&host.isValid())strategy=SmartPatternItem.strategy(host.getItemStack()).ordinal();
        super.broadcastChanges();
    }
}

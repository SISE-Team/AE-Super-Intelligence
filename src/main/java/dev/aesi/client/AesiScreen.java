// SPDX-License-Identifier: MIT
package dev.aesi.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.*;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.AEBaseMenu;
import appeng.util.Icon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Uses AE2's own background generator, slot atlas and controls. */
public abstract class AesiScreen<T extends AEBaseMenu> extends AEBaseScreen<T> {
    protected AesiScreen(T menu,Inventory inventory,Component title,ScreenStyle style){super(menu,inventory,title,style);}
    @Override protected boolean shouldAddToolbar(){return false;}
    @Override public void drawBG(GuiGraphicsExtractor graphics,int x,int y,int mouseX,int mouseY,float partialTicks){
        super.drawBG(graphics,x,y,mouseX,mouseY,partialTicks);
        for(var slot:menu.slots)if(slot.isActive())
            Blitter.icon(Icon.SLOT_BACKGROUND).dest(x+slot.x-1,y+slot.y-1,18,18).blit(graphics);
    }
    protected final AeIcon icon(Icon icon,Component tooltip,int x,int y,Runnable action){
        var button=new AeIcon(icon,tooltip,action);button.setPosition(getGuiLeft()+x,getGuiTop()+y);
        return addRenderableWidget(button);
    }
    protected static final class AeIcon extends IconButton {
        private Icon icon;private Component tooltip;
        AeIcon(Icon icon,Component tooltip,Runnable action){super(b->action.run());this.icon=icon;this.tooltip=tooltip;setMessage(tooltip);}
        @Override protected Icon getIcon(){return icon;}
        @Override public java.util.List<Component> getTooltipMessage(){return java.util.List.of(tooltip);}
        void tooltip(Component value){tooltip=value;setMessage(value);}
        void icon(Icon value){icon=value;}
    }
}

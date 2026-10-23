// SPDX-License-Identifier: MIT
package dev.aesi.client;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.StyleManager;
import dev.aesi.controller.SmartPatternMenu;
import dev.aesi.planner.Strategy;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class SmartPatternScreen extends AesiScreen<SmartPatternMenu> {
    private Button strategy;
    public SmartPatternScreen(SmartPatternMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title,StyleManager.loadStyleDoc("/screens/aesi/smart_pattern.json"));
        setTextContent(TEXT_ID_DIALOG_TITLE,title);
    }
    @Override protected boolean shouldAddToolbar(){return false;}
    @Override protected void init(){
        super.init();
        strategy=addRenderableWidget(new appeng.client.gui.widgets.AE2Button(getGuiLeft()+50,getGuiTop()+30,118,20,
                Component.empty(),b->menu.configure((menu.strategy+1)%Strategy.values().length)));
        addRenderableWidget(new appeng.client.gui.widgets.AE2Button(getGuiLeft()+50,getGuiTop()+54,118,20,
                Component.translatable("gui.aesi.clear_target"),b->menu.configure(-1)));
    }
    @Override protected void updateBeforeRender(){
        super.updateBeforeRender();
        if(strategy!=null)strategy.setMessage(Component.translatable("strategy.aesi."+
                Strategy.values()[Math.clamp(menu.strategy,0,Strategy.values().length-1)].name().toLowerCase(java.util.Locale.ROOT)));
    }
}

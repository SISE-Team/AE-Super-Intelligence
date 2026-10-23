// SPDX-License-Identifier: MIT
package dev.aesi.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.StyleManager;
import dev.aesi.controller.WorkstationMenu;
import dev.aesi.controller.SmartLogic;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WorkstationScreen extends AesiScreen<WorkstationMenu> {
    private static final int[] TIER_COLORS = {0, 0xFF8A92A8, 0xFF3FB4E6, 0xFF8C5AD2, 0xFFECEEF4};
    private AeIcon previous,next,status;
    private appeng.client.gui.widgets.TabButton patternsTab,computeTab;
    public WorkstationScreen(WorkstationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StyleManager.loadStyleDoc("/screens/aesi/workstation.json"));
        setTextContent(TEXT_ID_DIALOG_TITLE, title);
    }

    @Override protected boolean shouldAddToolbar() { return false; }

    @Override protected void init() {
        super.init();
        patternsTab=addRenderableWidget(new appeng.client.gui.widgets.TabButton(dev.aesi.Aesi.SMART_PATTERN.get().getDefaultInstance(),
                Component.translatable("gui.aesi.patterns_tab"),b->menu.setTab(0)));
        computeTab=addRenderableWidget(new appeng.client.gui.widgets.TabButton(dev.aesi.Aesi.COMPUTE_SERVER.get().getDefaultInstance(),
                Component.translatable("gui.aesi.compute_tab"),b->menu.setTab(1)));
        patternsTab.setPosition(getGuiLeft()-22,getGuiTop()+4);computeTab.setPosition(getGuiLeft()-22,getGuiTop()+28);
        status=icon(appeng.util.Icon.COG,Component.empty(),188,172,()->{});
        previous=icon(appeng.util.Icon.ARROW_LEFT,Component.translatable("gui.aesi.previous_page"),8,30,()->menu.setPage(menu.page-1));
        next=icon(appeng.util.Icon.ARROW_RIGHT,Component.translatable("gui.aesi.next_page"),150,30,()->menu.setPage(menu.page+1));
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent("active_tab",Component.translatable(menu.tab==0?"gui.aesi.patterns_tab":"gui.aesi.compute_tab"));
        setTextContent("capacity", Component.translatable(menu.tab==0?"gui.aesi.capacity":"gui.aesi.modules_count", menu.tab==0?menu.capacity:menu.moduleCount));
        setTextContent("disk_count", Component.translatable("gui.aesi.disks_installed", menu.diskCount, SmartLogic.ARRAY_SLOTS));
        setTextContent("modules",Component.translatable(menu.legacyMask==0?"gui.aesi.disks":"gui.aesi.legacy_arrays"));
        setTextContent("page", menu.tab==0?Component.translatable("gui.aesi.page", menu.page + 1, menu.pageCount()):Component.translatable("gui.aesi.compute_slots"));
        if (previous != null) {previous.visible=menu.tab==0;previous.active = menu.page > 0;}
        if (next != null) {next.visible=menu.tab==0;next.active = menu.page + 1 < menu.pageCount();}
        if(patternsTab!=null){patternsTab.setSelected(menu.tab==0);computeTab.setSelected(menu.tab==1);}
        if(status!=null){
            status.icon(menu.activity==0?appeng.util.Icon.COG_DISABLED:appeng.util.Icon.COG);
            status.tooltip(Component.translatable("gui.aesi.activity."+menu.activity).append("\n").append(menu.missingModules.isEmpty()?Component.translatable("tooltip.aesi.module_ready"):
                    Component.translatable("tooltip.aesi.modules_missing",datasetNames(menu.missingModules))));
        }
    }

    private static Component datasetNames(String ids) {
        var result=Component.empty();
        for(var id:ids.split(", ")) {
            if(!result.getSiblings().isEmpty())result.append(", ");
            result.append(dev.aesi.recipe.LearningDatasets.get(id)==null?Component.literal(id):dev.aesi.item.ModuleUpgradeItem.name(id));
        }
        return result;
    }

    @Override public void drawFG(GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if(menu.tab==1)for(int bay=0;bay<3;bay++) {
            graphics.text(font,Component.translatable("gui.aesi.compute_bay",bay+1),86,70+36*bay,0xFF404040);
            if((menu.computeMask&(1<<bay))==0)graphics.fill(7,65+36*bay,79,83+36*bay,0x998B8B8B);
        }
        if(menu.legacyMask==0){
            for(int i=0;i<SmartLogic.ARRAY_SLOTS;i++){
                int x=188+(i%2)*34,y=70+(2-i/2)*26;
                boolean mounted=(menu.diskMask&(1<<i))!=0;
                appeng.client.gui.style.Blitter.icon(appeng.util.Icon.SLOT_BACKGROUND).dest(x,y,18,18).blit(graphics);
                graphics.text(font,String.valueOf(i+1),x+20,y+5,0xFF404040);
                if(!mounted)continue;
                // One strip per drive slot, coloured by tier, matching the bars in the world bay.
                for(int slot=0;slot<4;slot++){
                    char c=menu.bays.length()>i*4+slot?menu.bays.charAt(i*4+slot):'0';
                    int tier=c>='1'&&c<='4'?c-'0':0;
                    int sy=y+3+slot*3;
                    graphics.fill(x+3,sy,x+15,sy+2,tier==0?0xFF555555:TIER_COLORS[tier]);
                }
            }
            graphics.text(font,Component.translatable("gui.aesi.array_install"),188,151,0xFF404040);
        }
        graphics.text(font,Component.translatable("gui.aesi.activity."+menu.activity),209,177,0xFF404040);
        var patterns = menu.patternSlots();
        for (int i = 0; i < patterns.size(); i++) {
            if (!patterns.get(i).isActive() || menu.slotAvailable(i)) continue;
            var slot = patterns.get(i);
            if (!slot.hasItem()) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x998B8B8B);
        }
    }

}

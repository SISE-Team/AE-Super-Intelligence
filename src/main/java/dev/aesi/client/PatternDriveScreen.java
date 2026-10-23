// SPDX-License-Identifier: MIT
package dev.aesi.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.StyleManager;
import dev.aesi.controller.PatternDriveMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PatternDriveScreen extends AesiScreen<PatternDriveMenu> {
    private AeIcon previous, next;
    public PatternDriveScreen(PatternDriveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StyleManager.loadStyleDoc("/screens/aesi/pattern_drive.json"));
        setTextContent(TEXT_ID_DIALOG_TITLE, title);
    }

    @Override protected boolean shouldAddToolbar() { return false; }

    @Override protected void init() {
        super.init();
        previous=icon(appeng.util.Icon.ARROW_LEFT,Component.translatable("gui.aesi.previous_page"),114,4,()->menu.setPage(menu.page-1));
        next=icon(appeng.util.Icon.ARROW_RIGHT,Component.translatable("gui.aesi.next_page"),154,4,()->menu.setPage(menu.page+1));
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent("used", Component.translatable("gui.aesi.drive_used", menu.used, menu.capacity));
        setTextContent("page", Component.literal((menu.page + 1) + "/" + menu.pageCount()));
        boolean paged = menu.pageCount() > 1;
        if (previous != null) { previous.visible = paged; previous.active = menu.page > 0; }
        if (next != null) { next.visible = paged; next.active = menu.page + 1 < menu.pageCount(); }
    }

}

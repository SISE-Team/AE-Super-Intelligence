// SPDX-License-Identifier: MIT
package dev.aesi.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.StyleManager;
import dev.aesi.controller.DriveArrayMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DriveArrayScreen extends AesiScreen<DriveArrayMenu> {
    public DriveArrayScreen(DriveArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StyleManager.loadStyleDoc("/screens/aesi/drive_array.json"));
        setTextContent(TEXT_ID_DIALOG_TITLE, title);
    }

    @Override protected boolean shouldAddToolbar() { return false; }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent("used", Component.translatable("gui.aesi.drive_used", menu.used, menu.capacity));
    }

}

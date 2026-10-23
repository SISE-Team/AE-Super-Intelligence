// SPDX-License-Identifier: MIT
package dev.aesi.client;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.StyleManager;
import dev.aesi.controller.ComputeArrayMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class ComputeArrayScreen extends AesiScreen<ComputeArrayMenu> {
    public ComputeArrayScreen(ComputeArrayMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title,StyleManager.loadStyleDoc("/screens/aesi/compute_array.json"));
        setTextContent(TEXT_ID_DIALOG_TITLE,title);
    }
    @Override protected boolean shouldAddToolbar(){return false;}
}

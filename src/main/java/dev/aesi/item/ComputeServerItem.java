// SPDX-License-Identifier: MIT
package dev.aesi.item;

import appeng.api.implementations.menuobjects.*;
import appeng.menu.locator.*;
import dev.aesi.Aesi;
import dev.aesi.multiblock.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** One removable 2U server carrying one process dataset. */
public final class ComputeServerItem extends ServerBlockItem implements IMenuItem {
    public ComputeServerItem(Properties properties){super(Aesi.COMPUTE_SERVER_BLOCK.get(),properties);}
    @Override public InteractionResult useOn(UseOnContext context) {
        if(Workstation.formed(context.getLevel().getBlockState(context.getClickedPos())))
            return RackServers.install(context,true);
        return super.useOn(context);
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand) {
        if(!level.isClientSide())appeng.menu.MenuOpener.open(Aesi.COMPUTE_ARRAY_MENU.get(),player,MenuLocators.forHand(player,hand));
        return InteractionResult.SUCCESS;
    }
    @Override public ItemMenuHost<?> getMenuHost(Player player,ItemMenuHostLocator locator,BlockHitResult hit){
        return new ItemMenuHost<>(this,player,locator);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> lines,TooltipFlag flag){
        lines.accept(net.minecraft.network.chat.Component.translatable("message.aesi.server_mount"));
    }
}

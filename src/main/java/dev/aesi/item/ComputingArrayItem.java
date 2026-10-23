// SPDX-License-Identifier: MIT
package dev.aesi.item;
import appeng.api.implementations.menuobjects.*;
import appeng.menu.locator.*;
import dev.aesi.Aesi;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
public final class ComputingArrayItem extends ServerBlockItem implements IMenuItem {
    public ComputingArrayItem(Properties p){super(Aesi.STRATEGY_ARRAY.get(),p);}
    @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        var state=context.getLevel().getBlockState(context.getClickedPos());
        if(dev.aesi.multiblock.ArrayMounts.empty(state))
            return dev.aesi.multiblock.ArrayMounts.install(context.getLevel(),context.getClickedPos(),context.getItemInHand(),context.getPlayer(),true);
        if(dev.aesi.multiblock.Workstation.formed(state)) return InteractionResult.FAIL;
        return super.useOn(context);
    }
    @Override public InteractionResult place(net.minecraft.world.item.context.BlockPlaceContext context) {
        if(dev.aesi.multiblock.ArrayMounts.empty(context.getLevel().getBlockState(context.getClickedPos())))
            return dev.aesi.multiblock.ArrayMounts.install(context.getLevel(),context.getClickedPos(),context.getItemInHand(),context.getPlayer(),true);
        return super.place(context);
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(!level.isClientSide()) appeng.menu.MenuOpener.open(Aesi.COMPUTE_ARRAY_MENU.get(),player,MenuLocators.forHand(player,hand));
        return InteractionResult.SUCCESS;
    }
    @Override public ItemMenuHost<?> getMenuHost(Player p,ItemMenuHostLocator locator,BlockHitResult hit){return new ItemMenuHost<>(this,p,locator);}
}

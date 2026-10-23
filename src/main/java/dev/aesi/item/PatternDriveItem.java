// SPDX-License-Identifier: MIT
package dev.aesi.item;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import dev.aesi.Aesi;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import java.util.function.Consumer;

/** A tiered pattern drive. Its patterns live in the stack's container component. */
public final class PatternDriveItem extends Item implements IMenuItem {
    private final int tier;
    public PatternDriveItem(Properties properties,int tier){super(properties);this.tier=tier;}
    public int tier(){return tier;}

    public static int tier(ItemStack stack){return stack.getItem() instanceof PatternDriveItem drive?drive.tier:0;}
    public static int capacity(ItemStack stack){return DriveTiers.capacity(tier(stack));}
    public static int used(ItemStack stack){
        int used=0;
        for(var ignored:stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).nonEmptyItems())used++;
        return used;
    }
    public static ItemStack forTier(int tier){return Aesi.PATTERN_DRIVES.get(tier-1).get().getDefaultInstance();}
    public static boolean accepts(ItemStack stack){return stack.getItem() instanceof SmartPatternItem;}

    /** Moves every pattern into the player's inventory; whatever does not fit is dropped at the player. */
    public static int dump(Player player,ItemStack drive){
        var contents=drive.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);
        var patterns=contents.nonEmptyItemCopyStream().toList();
        drive.remove(DataComponents.CONTAINER);
        int moved=0;
        for(var pattern:patterns){
            moved++;
            if(!player.getInventory().add(pattern)&&!pattern.isEmpty())player.drop(pattern,false);
        }
        return moved;
    }

    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(!level.isClientSide()){
            if(player.isSecondaryUseActive()){
                int moved=dump(player,stack);
                player.sendOverlayMessage(Component.translatable("message.aesi.drive_dumped",moved));
            }else MenuOpener.open(Aesi.PATTERN_DRIVE_MENU.get(),player,MenuLocators.forHand(player,hand));
        }
        player.swing(hand);
        return InteractionResult.SUCCESS;
    }
    @Override public ItemMenuHost<?> getMenuHost(Player player,ItemMenuHostLocator locator,BlockHitResult hit){
        return new ItemMenuHost<>(this,player,locator);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag){
        lines.accept(Component.translatable("tooltip.aesi.drive_used",used(stack),DriveTiers.capacity(tier)));
        lines.accept(Component.translatable("tooltip.aesi.drive_use"));
        if(DriveTiers.upgradeTarget(tier)!=0)lines.accept(Component.translatable("tooltip.aesi.drive_upgrade",tier+1));
    }
}

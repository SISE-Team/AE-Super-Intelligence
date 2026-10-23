// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import dev.aesi.Aesi;
import dev.aesi.controller.SmartController;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;

/** Two 24U cabinets; each of three block cells holds four 2U or two 4U servers. */
public final class RackServers {
    // Each 16px construction block reserves 1px above/below its 8U opening.
    public static final int CELL_UNITS=8, COLUMN_UNITS=24;
    public static final double UNIT_PIXELS=14.0/CELL_UNITS;
    public static double height(boolean compute){return UNIT_PIXELS*(compute?2:4);}
    public static int x(boolean compute){return compute?0:1;}
    public static double bottom(boolean compute,int cell,int row){return 16*(cell+1)+1+row*height(compute);}
    public record Slot(SmartController head,int column,int row,boolean compute,BlockPos mount){}
    private RackServers(){}
    public static Slot select(Level level,BlockHitResult hit) {
        var state=level.getBlockState(hit.getBlockPos());
        var head=level.getBlockEntity(hit.getBlockPos()) instanceof SmartController c?c:Workstation.owner(level,hit.getBlockPos(),state);
        if(head==null||!Workstation.formed(state))return null;
        var facing=head.getBlockState().getValue(Workstation.FACING);
        if(hit.getDirection()!=facing)return null;
        var relative=hit.getLocation().subtract(Vec3.atCenterOf(head.getBlockPos()));
        if(Math.abs(relative.dot(Vec3.atLowerCornerOf(facing.getUnitVec3i()))-.5)>1.0e-4)return null;
        int x=(int)Math.floor(relative.dot(Vec3.atLowerCornerOf(facing.getCounterClockWise().getUnitVec3i()))+.5);
        double y=(hit.getLocation().y-head.getBlockPos().getY())*16;
        int column=(int)Math.floor(y/16)-1;
        if(x<0||x>=Workstation.WIDTH||column<0||column>=3)return null;
        double local=y-16*(column+1);
        if(local<1||local>=15)return null;
        boolean compute=x==0;
        int row=(int)((local-1)/height(compute));
        var mount=compute?Workstation.position(head.getBlockPos(),facing,ComputingArrayEntity.SEGMENTS.get(column))
                :DiskArrayEntity.position(head.getBlockPos(),facing,column*2+row);
        return new Slot(head,column,row,compute,mount);
    }
    public static InteractionResult install(UseOnContext context,boolean compute){
        var level=context.getLevel();var slot=select(level,new BlockHitResult(context.getClickLocation(),context.getClickedFace(),context.getClickedPos(),false));
        var player=context.getPlayer();var stack=context.getItemInHand();
        if(slot==null||slot.compute()!=compute||player==null||!player.mayBuild()||
                !Workstation.problems(slot.head(),true).isEmpty())return InteractionResult.FAIL;
        if(!compute)return ArrayMounts.install(level,slot.mount(),stack,player,false);
        var old=level.getBlockState(slot.mount());
        var array=level.getBlockEntity(slot.mount()) instanceof ComputingArrayEntity a?a:null;
        if(array!=null&&array.hasBlade(slot.row())||array==null&&!old.is(Aesi.ARRAY_RACK.get()))return InteractionResult.FAIL;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(array==null){
            var state=Aesi.STRATEGY_ARRAY.get().defaultBlockState().setValue(Workstation.TALL,true).setValue(Workstation.FORMED,true)
                    .setValue(Workstation.SEGMENT,old.getValue(Workstation.SEGMENT)).setValue(Workstation.FACING,old.getValue(Workstation.FACING))
                    .setValue(DiskArrayBlock.SOCKETED,true);
            if(!level.setBlock(slot.mount(),state,Block.UPDATE_ALL))return InteractionResult.FAIL;
            array=(ComputingArrayEntity)level.getBlockEntity(slot.mount());array.emptyChassis();
        }
        array.insertBlade(slot.row(),stack);
        if(!player.hasInfiniteMaterials())stack.shrink(1);
        sound(level,slot.mount());return InteractionResult.SUCCESS;
    }
    /** Sneak + empty hand removes precisely the server under the cursor, with all its contents. */
    public static InteractionResult remove(Level level,Player player,BlockHitResult hit){
        if(!player.isShiftKeyDown()||!player.getMainHandItem().isEmpty()||!player.getOffhandItem().isEmpty()||!player.mayBuild())
            return InteractionResult.PASS;
        var slot=select(level,hit);if(slot==null)return InteractionResult.PASS;
        var be=level.getBlockEntity(slot.mount());
        if(slot.compute()&&(!(be instanceof ComputingArrayEntity a)||!a.hasBlade(slot.row()))
                ||!slot.compute()&&!(be instanceof DiskArrayEntity))return InteractionResult.FAIL;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        ItemStack item;boolean restore;
        if(be instanceof ComputingArrayEntity array){item=array.removeBlade(slot.row());restore=array.bladeMask()==0;}
        else {var array=(DiskArrayEntity)be;item=array.portable();array.clearContent();restore=true;}
        if(restore){
            var old=level.getBlockState(slot.mount());
            level.setBlock(slot.mount(),Aesi.ARRAY_RACK.get().defaultBlockState().setValue(Workstation.TALL,true).setValue(Workstation.FORMED,true)
                    .setValue(Workstation.SEGMENT,old.getValue(Workstation.SEGMENT)).setValue(Workstation.FACING,old.getValue(Workstation.FACING)),Block.UPDATE_ALL);
        }
        if(!player.getInventory().add(item))player.drop(item,false);
        sound(level,slot.mount());return InteractionResult.SUCCESS;
    }
    private static void sound(Level level,BlockPos pos){level.playSound(null,pos,net.minecraft.sounds.SoundEvents.METAL_PLACE,net.minecraft.sounds.SoundSource.BLOCKS,1,.8f);}
}

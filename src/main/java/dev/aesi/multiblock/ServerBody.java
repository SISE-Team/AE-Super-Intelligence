// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import dev.aesi.Aesi;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/** The rear cell has no inventory or item: all contents belong to exactly one front block. */
public final class ServerBody {
    private static final ThreadLocal<Boolean> REMOVING=ThreadLocal.withInitial(()->false);
    private ServerBody(){}
    public static boolean loose(BlockState state){
        return (state.is(Aesi.COMPUTE_SERVER_BLOCK.get())||state.is(Aesi.STRATEGY_ARRAY.get())||state.is(Aesi.DISK_ARRAY.get()))
                &&!Workstation.formed(state)&&!state.getValue(DiskArrayBlock.SOCKETED);
    }
    public static BlockPos rear(BlockPos front,BlockState state){return front.relative(state.getValue(Workstation.FACING).getOpposite());}
    public static BlockState tail(BlockState state){return Aesi.SERVER_TAIL.get().defaultBlockState()
            .setValue(Workstation.FACING,state.getValue(Workstation.FACING)).setValue(ServerTailBlock.COMPUTE,!state.is(Aesi.DISK_ARRAY.get()));}
    public static boolean canPlace(BlockPlaceContext context,BlockState state){
        var level=context.getLevel();var pos=rear(context.getClickedPos(),state);var player=context.getPlayer();
        return level.hasChunkAt(pos)&&level.getWorldBorder().isWithinBounds(pos)&&!level.isOutsideBuildHeight(pos)
                &&level.getBlockState(pos).isAir()&&(player==null||player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()))
                &&level.isUnobstructed(tail(state),pos,CollisionContext.placementContext(player));
    }
    public static boolean attach(Level level,BlockPos front,BlockState state){
        var pos=rear(front,state);
        return level.getBlockState(pos).equals(tail(state))||level.getBlockState(pos).isAir()&&level.setBlock(pos,tail(state),Block.UPDATE_ALL);
    }
    public static void onReady(Level level,BlockPos front,BlockState state){
        if(level==null||level.isClientSide()||!loose(state)||DiskArrayEntity.findOwner(level,front)!=null)return;
        var pos=rear(front,state);
        if(level.hasChunkAt(pos)&&level.getBlockState(pos).isAir()&&level.isUnobstructed(tail(state),pos,CollisionContext.empty()))attach(level,front,state);
    }
    public static boolean owns(Level level,BlockPos tailPos,BlockState tailState){
        var front=tailPos.relative(tailState.getValue(Workstation.FACING));
        var state=level.getBlockState(front);
        return loose(state)&&state.getValue(Workstation.FACING)==tailState.getValue(Workstation.FACING)
                &&tailState.getValue(ServerTailBlock.COMPUTE)!=state.is(Aesi.DISK_ARRAY.get());
    }
    public static void frontRemoved(ServerLevel level,BlockPos front,BlockState state){
        if(!loose(state)||level.restoringBlockSnapshots||REMOVING.get())return;
        var pos=rear(front,state);if(!level.hasChunkAt(pos)||!level.getBlockState(pos).equals(tail(state)))return;
        REMOVING.set(true);try{level.removeBlock(pos,false);}finally{REMOVING.remove();}
    }
    public static void rearRemoved(ServerLevel level,BlockPos pos,BlockState state){
        if(level.restoringBlockSnapshots||REMOVING.get()||!owns(level,pos,state))return;
        REMOVING.set(true);try{level.destroyBlock(pos.relative(state.getValue(Workstation.FACING)),true);}finally{REMOVING.remove();}
    }
    public static void creativeRemove(ServerLevel level,BlockPos pos,BlockState state,net.minecraft.world.entity.player.Player player){
        if(!owns(level,pos,state))return;
        var front=pos.relative(state.getValue(Workstation.FACING));var frontState=level.getBlockState(front);
        frontState.getBlock().playerWillDestroy(level,front,frontState,player);
        REMOVING.set(true);try{level.removeBlock(front,false);}finally{REMOVING.remove();}
    }
}

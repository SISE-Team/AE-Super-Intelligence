// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Rear half of a free-standing server; paired occupancy with no duplicated inventory. */
public final class ServerTailBlock extends Block {
    public static final BooleanProperty COMPUTE=BooleanProperty.create("compute");
    public ServerTailBlock(Properties properties){super(properties.strength(2).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
        registerDefaultState(defaultBlockState().setValue(Workstation.FACING,Direction.NORTH).setValue(COMPUTE,true));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(Workstation.FACING,COMPUTE);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        double h=RackServers.height(state.getValue(COMPUTE));
        return switch(state.getValue(Workstation.FACING)){
            case SOUTH->Block.box(1,0,1,15,h,16);case EAST->Block.box(1,0,1,16,h,15);
            case WEST->Block.box(0,0,1,15,h,15);default->Block.box(1,0,0,15,h,15);};
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        var front=pos.relative(state.getValue(Workstation.FACING));
        return ServerBody.owns(level,pos,state)?level.getBlockState(front).useWithoutItem(level,player,new BlockHitResult(hit.getLocation(),hit.getDirection(),front,false)):InteractionResult.PASS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        var wrench=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,net.minecraft.resources.Identifier.parse("c:tools/wrench"));
        if(player.mayBuild()&&stack.is(wrench)&&ServerBody.owns(level,pos,state)){
            var front=pos.relative(state.getValue(Workstation.FACING));
            if(!level.isClientSide()&&level.getBlockEntity(front) instanceof appeng.blockentity.AEBaseBlockEntity be)
                be.disassembleWithWrench(player,level,new BlockHitResult(hit.getLocation(),hit.getDirection(),front,false),stack);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){
        ServerBody.rearRemoved(level,pos,state);super.affectNeighborsAfterRemoval(state,level,pos,moved);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(level instanceof ServerLevel server&&player.isCreative())ServerBody.creativeRemove(server,pos,state,player);
        return super.playerWillDestroy(level,pos,state,player);
    }
}

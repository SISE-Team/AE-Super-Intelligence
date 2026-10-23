// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import appeng.block.AEBaseEntityBlock;
import dev.aesi.Aesi;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

public final class ComputingArrayBlock extends AEBaseEntityBlock<ComputingArrayEntity> {
    public ComputingArrayBlock(Properties properties) {
        super(metalProps(properties).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
        registerDefaultState(defaultBlockState().setValue(Workstation.TALL,false).setValue(Workstation.FORMED,false)
                .setValue(Workstation.SEGMENT,0).setValue(Workstation.FACING,Direction.NORTH).setValue(DiskArrayBlock.SOCKETED,false));
    }
    @Override public appeng.api.orientation.IOrientationStrategy getOrientationStrategy() { return Workstation.ORIENTATION; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(Workstation.TALL,Workstation.FORMED,Workstation.SEGMENT,DiskArrayBlock.SOCKETED);
    }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return WorkstationShapes.get(state);
    }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getOcclusionShape(BlockState state) {
        return Workstation.formed(state) ? net.minecraft.world.phys.shapes.Shapes.empty() : super.getOcclusionShape(state);
    }
    @Override protected float getShadeBrightness(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) { return Workstation.formed(state)?1f:.2f; }
    @Override protected boolean propagatesSkylightDown(BlockState state) { return Workstation.formed(state); }
    @Override protected int getLightDampening(BlockState state) { return Workstation.formed(state)?0:super.getLightDampening(state); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        var removed=RackServers.remove(level,player,hit);
        if(removed!=InteractionResult.PASS)return removed;
        var result=Workstation.use(level,pos,state,player);
        if(!level.isClientSide() && player.containerMenu instanceof dev.aesi.controller.WorkstationMenu menu) menu.setTab(1);
        return result;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,
            net.minecraft.world.InteractionHand hand,BlockHitResult hit){
        if(ArrayMounts.isArray(stack))return InteractionResult.PASS;
        return super.useItemOn(stack,state,level,pos,player,hand,hit);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved) {
        ServerBody.frontRemoved(level,pos,state);
        ArrayMounts.restoreBacking(state,level,pos);
        Workstation.removed(state,level,pos);super.affectNeighborsAfterRemoval(state,level,pos,moved);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity player,ItemStack stack) {
        super.setPlacedBy(level,pos,state,player,stack);
        if(level.getBlockEntity(pos) instanceof ComputingArrayEntity array) array.restore(stack);
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) {
        var be=builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        return java.util.List.of(be instanceof ComputingArrayEntity array
                ?state.is(Aesi.COMPUTE_SERVER_BLOCK.get())?array.blade(0):array.portable()
                :state.getBlock().asItem().getDefaultInstance());
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(!level.isClientSide()&&player.isCreative()&&level.getBlockEntity(pos) instanceof ComputingArrayEntity array&&!array.upgrades().isEmpty()) {
            popResource(level,pos,state.is(Aesi.COMPUTE_SERVER_BLOCK.get())?array.blade(0):array.portable());array.clearContent();
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
}

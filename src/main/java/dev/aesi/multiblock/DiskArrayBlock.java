// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import appeng.block.AEBaseEntityBlock;
import appeng.api.orientation.*;
import dev.aesi.Aesi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

public final class DiskArrayBlock extends AEBaseEntityBlock<DiskArrayEntity> {
    // This backing belongs to the rack (or legacy cooling socket) used for installation,
    // not to the portable array. Restore it once when the module is removed.
    public static final BooleanProperty SOCKETED=BooleanProperty.create("socketed");
    public static final BooleanProperty LEGACY_SOCKET=BooleanProperty.create("legacy_socket");
    public DiskArrayBlock(Properties properties){
        super(metalProps(properties).noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(defaultBlockState().setValue(Workstation.TALL,false).setValue(Workstation.FORMED,false)
                .setValue(Workstation.SEGMENT,0).setValue(Workstation.FACING,Direction.NORTH).setValue(SOCKETED,false).setValue(LEGACY_SOCKET,true));
    }
    @Override public IOrientationStrategy getOrientationStrategy(){return Workstation.ORIENTATION;}
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return WorkstationShapes.get(state);
    }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getOcclusionShape(BlockState state) {
        return Workstation.formed(state) ? net.minecraft.world.phys.shapes.Shapes.empty() : super.getOcclusionShape(state);
    }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getVisualShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return Workstation.formed(state) ? net.minecraft.world.phys.shapes.Shapes.empty() : super.getVisualShape(state, level, pos, context);
    }
    @Override protected float getShadeBrightness(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return Workstation.formed(state) ? 1.0F : 0.2F;
    }
    @Override protected boolean propagatesSkylightDown(BlockState state) { return Workstation.formed(state); }
    @Override protected int getLightDampening(BlockState state) {
        return Workstation.formed(state) ? 0 : super.getLightDampening(state);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        super.createBlockStateDefinition(builder);builder.add(Workstation.TALL,Workstation.FORMED,Workstation.SEGMENT,SOCKETED,LEGACY_SOCKET);
    }
    public static boolean isSocket(BlockState state){return ArrayMounts.empty(state)&&Workstation.storageBay(state.getValue(Workstation.SEGMENT));}
    public static InteractionResult install(Level level,BlockPos pos,ItemStack stack,Player player){
        return ArrayMounts.install(level,pos,stack,player,false);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){
        ServerBody.frontRemoved(level,pos,state);
        ArrayMounts.restoreBacking(state,level,pos);
        Workstation.removed(state,level,pos);
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var state=super.getStateForPlacement(context);var owner=DiskArrayEntity.findOwner(context.getLevel(),context.getClickedPos());
        return state!=null&&owner!=null?state.setValue(Workstation.FACING,owner.getBlockState().getValue(Workstation.FACING)):state;
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(level.getBlockEntity(pos) instanceof DiskArrayEntity array)array.restore(stack);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        var removed=RackServers.remove(level,player,hit);
        if(removed!=InteractionResult.PASS)return removed;
        var array=level.getBlockEntity(pos) instanceof DiskArrayEntity a?a:null;var owner=array==null?null:array.owner();
        if(owner!=null)return Workstation.use(level,owner.getBlockPos(),owner.getBlockState(),player);
        if(!level.isClientSide())player.sendOverlayMessage(Component.translatable("message.aesi.array_mount"));
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,
            net.minecraft.world.InteractionHand hand,BlockHitResult hit){
        if(ArrayMounts.isArray(stack))return InteractionResult.PASS;
        return super.useItemOn(stack,state,level,pos,player,hand,hit);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide()&&player.isCreative()&&level.getBlockEntity(pos) instanceof DiskArrayEntity array&&array.hasDrives()){
            popResource(level,pos,array.portable());
            array.clearContent();
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){
        var be=builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        return List.of(be instanceof DiskArrayEntity a?a.portable():Aesi.PATTERN_DISK.get().getDefaultInstance());
    }
}

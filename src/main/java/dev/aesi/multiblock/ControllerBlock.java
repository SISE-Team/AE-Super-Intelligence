// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import appeng.api.orientation.IOrientationStrategy;
import appeng.block.crafting.PatternProviderBlock;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

public final class ControllerBlock extends PatternProviderBlock {
    public ControllerBlock(Properties properties) {
        super(properties.noOcclusion().lightLevel(state -> Workstation.formed(state) ? 1 : 0).pushReaction(PushReaction.BLOCK));
        registerDefaultState(defaultBlockState().setValue(Workstation.TALL,false).setValue(Workstation.FORMED, false)
                .setValue(Workstation.SEGMENT, 0).setValue(Workstation.FACING, Direction.NORTH));
    }
    @Override public IOrientationStrategy getOrientationStrategy() { return Workstation.ORIENTATION; }
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
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Workstation.TALL,Workstation.FORMED, Workstation.SEGMENT);
    }
    @Override public void setSide(Level level, BlockPos pos, Direction facing) {
        // AESI dispatches through native providers elsewhere on the network.
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) { return Workstation.use(level, pos, state, player); }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        Workstation.removed(state, level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }
}

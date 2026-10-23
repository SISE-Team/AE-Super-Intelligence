// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import dev.aesi.Aesi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The rack belongs to the construction, while the machine and its inventory remain portable. */
public final class ArrayMounts {
    private ArrayMounts() {}
    public static boolean isArray(ItemStack stack) {
        return stack.is(Aesi.COMPUTING_ARRAY_ITEM.get()) || stack.is(Aesi.COMPUTE_SERVER.get()) || stack.is(Aesi.PATTERN_DISK.get());
    }
    public static boolean empty(BlockState state) {
        return state.is(Aesi.ARRAY_RACK.get())
                || Workstation.formed(state) && state.is(Aesi.COOLING_UNIT.get())
                && Workstation.storageBay(state.getValue(Workstation.SEGMENT));
    }
    public static InteractionResult install(Level level, BlockPos pos, ItemStack stack, Player player, boolean compute) {
        var old = level.getBlockState(pos);
        if (!empty(old) || stack.isEmpty() || !stack.is(compute ? Aesi.COMPUTING_ARRAY_ITEM.get() : Aesi.PATTERN_DISK.get())
                || player != null && !player.mayBuild()) return InteractionResult.FAIL;
        int segment = old.getValue(Workstation.SEGMENT);
        if (Workstation.formed(old) && (compute ? !Workstation.computeBay(segment) : !Workstation.storageBay(segment))) {
            if (player != null && !level.isClientSide()) player.sendOverlayMessage(
                    net.minecraft.network.chat.Component.translatable("message.aesi.wrong_array_bay"));
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var owner = Workstation.owner(level, pos, old);
        if (Workstation.formed(old) && (owner == null || !Workstation.problems(owner, true).isEmpty())) return InteractionResult.FAIL;
        var state = (compute ? Aesi.STRATEGY_ARRAY.get() : Aesi.DISK_ARRAY.get()).defaultBlockState()
                .setValue(Workstation.TALL,old.getValue(Workstation.TALL)).setValue(Workstation.FACING, old.getValue(Workstation.FACING))
                .setValue(Workstation.SEGMENT, segment).setValue(Workstation.FORMED, Workstation.formed(old))
                .setValue(DiskArrayBlock.SOCKETED, true);
        if (!compute) state = state.setValue(DiskArrayBlock.LEGACY_SOCKET, old.is(Aesi.COOLING_UNIT.get()));
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) return InteractionResult.FAIL;
        if (compute) ((ComputingArrayEntity)level.getBlockEntity(pos)).restore(stack);
        else ((DiskArrayEntity)level.getBlockEntity(pos)).restore(stack);
        if (player == null || !player.hasInfiniteMaterials()) stack.shrink(1);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.METAL_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1, .8f);
        level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_PLACE, pos,
                net.minecraft.world.level.gameevent.GameEvent.Context.of(player, state));
        return InteractionResult.SUCCESS;
    }
    public static void restoreBacking(BlockState state, ServerLevel level, BlockPos pos) {
        if(Workstation.relocating())return;
        if (!state.getValue(DiskArrayBlock.SOCKETED) || level.getBlockState(pos).is(state.getBlock())) return;
        Block block = state.hasProperty(DiskArrayBlock.LEGACY_SOCKET) && state.getValue(DiskArrayBlock.LEGACY_SOCKET)
                ? Aesi.COOLING_UNIT.get() : Aesi.ARRAY_RACK.get();
        var backing = block.defaultBlockState().setValue(Workstation.TALL,state.getValue(Workstation.TALL)).setValue(Workstation.FACING, state.getValue(Workstation.FACING))
                .setValue(Workstation.SEGMENT, state.getValue(Workstation.SEGMENT))
                .setValue(Workstation.FORMED, Workstation.formed(state));
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, backing, Block.UPDATE_ALL);
        else if (!level.getBlockState(pos).is(block)) Block.popResource(level, pos, block.asItem().getDefaultInstance());
    }
}

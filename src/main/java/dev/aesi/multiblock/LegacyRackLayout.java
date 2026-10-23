// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import dev.aesi.Aesi;
import dev.aesi.controller.SmartController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Atomic, index-preserving relocation of intact older cabinets into clear space. */
final class LegacyRackLayout {
    private LegacyRackLayout() {}
    static void upgrade(SmartController head) {
        var level=head.getLevel();var origin=head.getBlockPos();var facing=head.getBlockState().getValue(Workstation.FACING);
        if(level==null||level.isClientSide()||Workstation.relocating()||head.getBlockState().getValue(Workstation.TALL))return;
        java.util.function.IntFunction<BlockPos> oldPos=i->origin.offset(Workstation.legacyOffset(facing,i));
        if(!level.hasChunkAt(oldPos.apply(11)))return;
        boolean aligned=level.getBlockState(oldPos.apply(10)).is(Aesi.PLANNING_CORE.get());
        int[] compute=aligned?new int[]{11,12,13}:new int[]{12,16,17};
        int[] storage=aligned?new int[]{21,26,22,27,23,28}:new int[]{3,8,13,18,23,28};
        var states=new BlockState[30];var items=new ItemStack[30];
        var oldPositions=new java.util.HashSet<BlockPos>();
        for(int i=0;i<30;i++) {
            var pos=oldPos.apply(i);if(!level.hasChunkAt(pos))return;oldPositions.add(pos);
            var state=level.getBlockState(pos);states[i]=state;items[i]=ItemStack.EMPTY;
            if(state.hasProperty(Workstation.TALL)&&state.getValue(Workstation.TALL))return;
            if(Workstation.formed(state)&&!Workstation.ownerPosition(pos,state).equals(origin))return;
            final int index=i;
            boolean c=java.util.Arrays.stream(compute).anyMatch(v->v==index),s=java.util.Arrays.stream(storage).anyMatch(v->v==index);
            boolean valid=c?(state.is(Aesi.ARRAY_RACK.get())||state.is(Aesi.STRATEGY_ARRAY.get()))
                    :s?(state.is(Aesi.ARRAY_RACK.get())||state.is(Aesi.DISK_ARRAY.get())||state.is(Aesi.COOLING_UNIT.get()))
                    :i==(aligned?10:11)?state.is(Aesi.PLANNING_CORE.get())
                    :aligned&&(i==15||i==19)?state.is(Aesi.COOLING_UNIT.get())||state.is(Aesi.STRUCTURAL_CASING.get())
                    :state.is(switch(i){case 0->Aesi.CONTROLLER.get();case 1,2->Aesi.DATA_BUS.get();case 5->Aesi.NETWORK_PORT.get();default->Aesi.STRUCTURAL_CASING.get();});
            if(!valid)return;
            if(level.getBlockEntity(pos) instanceof ComputingArrayEntity array)items[i]=array.portable();
            if(level.getBlockEntity(pos) instanceof DiskArrayEntity array)items[i]=array.portable();
        }
        var targets=new java.util.HashSet<BlockPos>();
        for(int i=0;i<Workstation.BLOCK_COUNT;i++) {
            var pos=Workstation.position(origin,facing,i);targets.add(pos);
            // Even replaceable plants are left alone: migration never clears another build.
            if(!level.hasChunkAt(pos)||!oldPositions.contains(pos)&&!level.getBlockState(pos).isAir())return;
        }
        int surplusCooling=(int)java.util.stream.IntStream.of(15,19).filter(i->aligned&&states[i].is(Aesi.COOLING_UNIT.get())).count()-1;
        Workstation.relocate(()->{
            // Detach all inventories before replacing their blocks, suppressing normal machine drops.
            for(int i=1;i<30;i++) {
                var pos=oldPos.apply(i);
                if(level.getBlockEntity(pos) instanceof ComputingArrayEntity array)array.clearContent();
                if(level.getBlockEntity(pos) instanceof DiskArrayEntity array)array.clearContent();
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
            }
            for(int i=Workstation.BLOCK_COUNT-1;i>=0;i--) {
                var state=Workstation.block(i).defaultBlockState();var item=ItemStack.EMPTY;
                int c=ComputingArrayEntity.SEGMENTS.indexOf(i),s=Workstation.STORAGE_SEGMENTS.indexOf(i);
                if(c>=0){item=items[compute[c]];if(!item.isEmpty())state=Aesi.STRATEGY_ARRAY.get().defaultBlockState().setValue(DiskArrayBlock.SOCKETED,true);}
                if(s>=0){item=items[storage[s]];if(!item.isEmpty())state=Aesi.DISK_ARRAY.get().defaultBlockState().setValue(DiskArrayBlock.SOCKETED,true);}
                state=state.setValue(Workstation.FACING,facing).setValue(Workstation.TALL,true).setValue(Workstation.FORMED,true).setValue(Workstation.SEGMENT,i);
                var pos=Workstation.position(origin,facing,i);level.setBlock(pos,state,Block.UPDATE_CLIENTS);
                if(level.getBlockEntity(pos) instanceof ComputingArrayEntity array)array.restore(item);
                if(level.getBlockEntity(pos) instanceof DiskArrayEntity array)array.restore(item);
            }
            Block.popResource(level,origin.above(),new ItemStack(Aesi.STRUCTURAL_CASING.get(),13-Math.max(0,surplusCooling)));
            Block.popResource(level,origin.above(),new ItemStack(Aesi.DATA_BUS.get(),1));
            if(surplusCooling>0)Block.popResource(level,origin.above(),new ItemStack(Aesi.COOLING_UNIT.get(),surplusCooling));
        });
    }
}

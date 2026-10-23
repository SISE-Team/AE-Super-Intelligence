// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;
import dev.aesi.Aesi;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
/** Recessed mounting pockets have solid backplanes; only installed machines fill the aperture. */
public final class WorkstationShapes {
    private WorkstationShapes() {}
    /** A rack's whole aperture is selectable, without filling its physical opening. */
    public static VoxelShape outline(BlockState state) {
        return rack(state)
                ? Shapes.block() : get(state);
    }
    private static boolean rack(BlockState state) {
        return state.is(Aesi.ARRAY_RACK.get()) || Workstation.formed(state)
                && state.is(Aesi.COOLING_UNIT.get()) && Workstation.storageBay(state.getValue(Workstation.SEGMENT));
    }
    private static final VoxelShape FRAME = frame();
    private static final VoxelShape[] EMPTY = rotations(FRAME);
    private static final VoxelShape MACHINE = Block.box(1,1,0,15,15,15);
    private static final VoxelShape[] LOOSE = rotations(MACHINE);
    private static final VoxelShape[] BLADE = rotations(Block.box(1,0,0,15,RackServers.height(true),16));
    private static final VoxelShape[] STORAGE = rotations(Block.box(1,0,0,15,RackServers.height(false),16));
    private static final VoxelShape[] MOUNTED = rotations(Shapes.or(FRAME,MACHINE));
    private static VoxelShape frame() {
        var shape=Shapes.or(Block.box(0,0,0,16,1,16),Block.box(0,15,0,16,16,16),
                Block.box(0,1,0,1,15,16),Block.box(15,1,0,16,15,16),Block.box(1,1,15,15,15,16),
                Block.box(5,5,14,11,11,15),Block.box(7,6,13,9,10,14));
        for(int x:new int[]{1,14})for(int y:new int[]{2,13})
            shape=Shapes.or(shape,Block.box(x,y,3,x+1,y+1,15));
        return shape.optimize();
    }
    private static VoxelShape[] rotations(VoxelShape base) {
        var result=new VoxelShape[4];result[0]=base;
        for(int i=1;i<4;i++) {
            var shape=Shapes.empty();
            for(var b:result[i-1].toAabbs())
                shape=Shapes.or(shape,Shapes.box(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX));
            result[i]=shape.optimize();
        }
        return result;
    }
    public static VoxelShape get(BlockState state) {
        boolean formed=Workstation.formed(state);
        int direction=switch(state.getValue(Workstation.FACING)){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
        if(state.is(Aesi.COMPUTE_SERVER_BLOCK.get())||!formed&&state.is(Aesi.STRATEGY_ARRAY.get()))return BLADE[direction];
        if(!formed&&state.is(Aesi.DISK_ARRAY.get()))return STORAGE[direction];
        boolean machine=state.is(Aesi.STRATEGY_ARRAY.get())||state.is(Aesi.DISK_ARRAY.get());
        if(machine) {
            Direction facing=state.getValue(Workstation.FACING);
            int rotation=switch(facing){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
            if(formed&&Workstation.rear(state.getValue(Workstation.SEGMENT)))rotation=(rotation+2)%4;
            return (formed?MOUNTED:LOOSE)[rotation];
        }
        if(rack(state)) {
            int rotation=switch(state.getValue(Workstation.FACING)){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
            if(formed&&Workstation.rear(state.getValue(Workstation.SEGMENT)))rotation=(rotation+2)%4;
            return EMPTY[rotation];
        }
        return Shapes.block();
    }
}

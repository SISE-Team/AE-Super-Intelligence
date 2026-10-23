// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import dev.aesi.Aesi;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** A passive, zero-extra-channel connection between the rear cable and controller. */
public final class NetworkPortEntity extends AENetworkedBlockEntity {
    public NetworkPortEntity(BlockPos pos, BlockState state) {
        super(Aesi.NETWORK_PORT_ENTITY.get(), pos, state);
        getMainNode().setIdlePowerUsage(0);
    }
    @Override public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        var state = getBlockState();
        if (!Workstation.formed(state)) return Set.of();
        var front = state.getValue(Workstation.FACING);
        return EnumSet.of(front, front.getOpposite());
    }
    @Override public void setBlockState(BlockState state) {
        super.setBlockState(state);
        onGridConnectableSidesChanged();
    }
    @Override public AECableType getCableConnectionType(Direction side) { return AECableType.SMART; }
    /** The front connection is internal; only the rear face can attach the whole rack to ME. */
    public boolean hasExternalConnection() {
        var node = getMainNode().getNode();
        return Workstation.formed(getBlockState()) && node != null && node.isActive()
                && node.getInWorldConnections().containsKey(getBlockState().getValue(Workstation.FACING).getOpposite());
    }
    public void serverTick() {
        if (level instanceof ServerLevel server && server.getGameTime() % 20 == 0)
            Workstation.checkOrphan(server, getBlockPos(), getBlockState());
    }
}

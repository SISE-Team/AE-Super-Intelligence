// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;

import dev.aesi.Aesi;

import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public final class SmartController extends PatternProviderBlockEntity {
    public SmartController(BlockPos pos, BlockState state) {
        super(Aesi.CONTROLLER_ENTITY.get(), pos, state);
    }

    @Override
    protected PatternProviderLogic createLogic() {
        return new SmartLogic(getMainNode(), this);
    }

    @Override
    public EnumSet<Direction> getTargets() {
        return EnumSet.noneOf(Direction.class);
    }

    @Override
    public java.util.Set<Direction> getGridConnectableSides(
            appeng.api.orientation.BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(Aesi.CONTROLLER_ITEM.get());
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (getLogic() instanceof SmartLogic smart) smart.markDirty();
    }

    public void serverTick() {
        ((SmartLogic) getLogic()).tick();
    }

    @Override
    public void setRemoved() {
        NetworkRuntime.remove((SmartLogic) getLogic());
        super.setRemoved();
    }
}

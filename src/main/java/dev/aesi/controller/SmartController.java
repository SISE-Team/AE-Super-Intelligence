// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;

import dev.aesi.Aesi;
import dev.aesi.multiblock.Workstation;

import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public final class SmartController extends PatternProviderBlockEntity {
    private volatile boolean structureReady;
    private int visualActivity;
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
        return Workstation.formed(getBlockState())
                ? EnumSet.of(getBlockState().getValue(Workstation.FACING).getOpposite())
                : EnumSet.noneOf(Direction.class);
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
        if (structureReady || level.getGameTime() % 10 == 0) {
            if (!structureReady || !Workstation.problems(this, true).isEmpty()) Workstation.validate(this);
            boolean ready = Workstation.problems(this, true).isEmpty();
            if (ready != structureReady) {
                structureReady = ready;
                ((SmartLogic) getLogic()).structureChanged();
            }
        }
        ((SmartLogic) getLogic()).tick();
        int activity=((SmartLogic)getLogic()).runtimeMode();
        if(activity!=visualActivity){visualActivity=activity;markForUpdate();}
    }

    public int visualActivity(){return visualActivity;}
    @Override protected void writeToStream(net.minecraft.network.RegistryFriendlyByteBuf data){
        super.writeToStream(data);data.writeByte(visualActivity);
    }
    @Override protected boolean readFromStream(net.minecraft.network.RegistryFriendlyByteBuf data){
        boolean changed=super.readFromStream(data);int activity=data.readUnsignedByte();
        changed|=visualActivity!=activity;visualActivity=activity;return changed;
    }

    public boolean structureReady() { return structureReady; }

    public boolean networkReady() {
        if (!structureReady || level == null || !getMainNode().isActive()) return false;
        var pos = Workstation.position(worldPosition, getBlockState().getValue(Workstation.FACING),
                Workstation.NETWORK_PORT_SEGMENT);
        return level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof dev.aesi.multiblock.NetworkPortEntity port
                && port.hasExternalConnection() && port.getMainNode().getGrid() == getMainNode().getGrid();
    }

    @Override public void openMenu(net.minecraft.world.entity.player.Player player,
            appeng.menu.locator.MenuHostLocator locator) {
        appeng.menu.MenuOpener.open(Aesi.WORKSTATION_MENU.get(), player, locator);
    }

    @Override public void returnToMainMenu(net.minecraft.world.entity.player.Player player,
            appeng.menu.ISubMenu subMenu) {
        appeng.menu.MenuOpener.returnTo(Aesi.WORKSTATION_MENU.get(), player, subMenu.getLocator());
    }

    /** Called on the server dispatch path, including before any input is touched. */
    public boolean canDispatch() {
        return structureReady && Workstation.problems(this, true).isEmpty();
    }

    @Override public net.minecraft.world.item.ItemStack getMainMenuIcon() {
        return Aesi.CONTROLLER_ITEM.get().getDefaultInstance();
    }

    @Override public void setBlockState(BlockState state) {
        if (!Workstation.formed(state)) {
            structureReady = false;
            if (getLogic() instanceof SmartLogic smart) smart.structureChanged();
        }
        super.setBlockState(state);
    }

    @Override
    public void setRemoved() {
        structureReady = false;
        NetworkRuntime.remove((SmartLogic) getLogic());
        super.setRemoved();
    }

    @Override public void onChunkUnloaded() {
        structureReady = false;
        NetworkRuntime.remove((SmartLogic) getLogic());
        super.onChunkUnloaded();
    }
}

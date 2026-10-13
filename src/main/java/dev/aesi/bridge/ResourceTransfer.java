// SPDX-License-Identifier: MIT
package dev.aesi.bridge;

import appeng.api.stacks.*;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** The units here are exactly the units in AEKey: items, millibuckets and chemical millibuckets. */
public final class ResourceTransfer {
    private ResourceTransfer() {}

    public static boolean supported(AEKey key) {
        return key instanceof AEItemKey
                || key instanceof AEFluidKey
                || ModList.get().isLoaded("appmek") && ChemicalTransfer.supported(key);
    }

    public static boolean hasHandler(ServerLevel level, BlockPos pos, Direction face, AEKey key) {
        if (key instanceof AEItemKey)
            return level.getCapability(Capabilities.Item.BLOCK, pos, face) != null;
        if (key instanceof AEFluidKey)
            return level.getCapability(Capabilities.Fluid.BLOCK, pos, face) != null;
        return ModList.get().isLoaded("appmek")
                && ChemicalTransfer.hasHandler(level, pos, face, key);
    }

    public static int insert(
            ServerLevel level,
            BlockPos pos,
            Direction face,
            AEKey key,
            int amount,
            TransactionContext tx) {
        if (key instanceof AEItemKey item) {
            var handler = level.getCapability(Capabilities.Item.BLOCK, pos, face);
            return handler == null ? 0 : handler.insert(item.toResource(), amount, tx);
        }
        if (key instanceof AEFluidKey fluid) {
            var handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, face);
            return handler == null ? 0 : handler.insert(fluid.toResource(), amount, tx);
        }
        return ModList.get().isLoaded("appmek")
                ? ChemicalTransfer.insert(level, pos, face, key, amount, tx)
                : 0;
    }

    /** Loaded only when Applied Mekanistics has registered its resource type. */
    private static final class ChemicalTransfer {
        static boolean supported(AEKey key) {
            return key instanceof me.ramidzkh.mekae2.ae2.MekanismKey;
        }

        static boolean hasHandler(ServerLevel level, BlockPos pos, Direction face, AEKey key) {
            return supported(key)
                    && level.getCapability(
                                    me.ramidzkh.mekae2.MekCapabilities.CHEMICAL.block(), pos, face)
                            != null;
        }

        static int insert(
                ServerLevel level,
                BlockPos pos,
                Direction face,
                AEKey key,
                int amount,
                TransactionContext tx) {
            if (!(key instanceof me.ramidzkh.mekae2.ae2.MekanismKey chemical)) return 0;
            var handler =
                    level.getCapability(
                            me.ramidzkh.mekae2.MekCapabilities.CHEMICAL.block(), pos, face);
            return handler == null ? 0 : handler.insert(chemical.toResource(), amount, tx);
        }
    }
}

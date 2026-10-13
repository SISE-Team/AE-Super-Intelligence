// SPDX-License-Identifier: MIT
package dev.aesi.bridge;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.helpers.externalstorage.GenericStackInv;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.*;

import java.util.*;

/** Output slots are identified by each machine adapter, never by an inventory count increase. */
public final class OutputRecovery {
    @FunctionalInterface
    public interface Extractor {
        int extract(int amount, TransactionContext transaction);
    }

    public record Port(AEKey key, long amount, Extractor extractor) {}

    private OutputRecovery() {}

    public static <R extends Resource> List<Port> handler(
            ResourceHandler<R> handler, java.util.function.Function<R, AEKey> key) {
        if (handler == null) return List.of();
        var ports = new ArrayList<Port>();
        for (int i = 0; i < handler.size(); i++) {
            var resource = handler.getResource(i);
            int slot = i;
            if (!resource.isEmpty())
                ports.add(
                        new Port(
                                key.apply(resource),
                                handler.getAmountAsLong(i),
                                (amount, tx) -> handler.extract(slot, resource, amount, tx)));
        }
        return ports;
    }

    public static long recover(Port output, GenericStackInv buffer) {
        if (output.key() == null || output.amount() <= 0) return 0;
        int limit =
                (int)
                        Math.min(
                                Integer.MAX_VALUE,
                                Math.min(
                                        output.amount(),
                                        buffer.insert(
                                                output.key(),
                                                output.amount(),
                                                Actionable.SIMULATE,
                                                IActionSource.empty())));
        if (limit == 0) return 0;
        try (var transaction = Transaction.openRoot()) {
            buffer.updateSnapshots(transaction);
            int extracted = output.extractor().extract(limit, transaction);
            if (extracted == 0) return 0;
            if (buffer.insert(output.key(), extracted, Actionable.MODULATE, IActionSource.empty())
                    != extracted) return 0;
            transaction.commit();
            return extracted;
        }
    }
}

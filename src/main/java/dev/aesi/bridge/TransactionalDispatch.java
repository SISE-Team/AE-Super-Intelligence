// SPDX-License-Identifier: MIT
package dev.aesi.bridge;

import appeng.api.stacks.*;

import dev.aesi.controller.MachineRegistry.Endpoint;

import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Known processing machines only. A failed batch rolls back every insertion. */
public final class TransactionalDispatch {
    private TransactionalDispatch() {}

    public static boolean push(Endpoint endpoint, KeyCounter[] inputs) {
        try (var transaction = Transaction.openRoot()) {
            for (var group : inputs)
                for (var entry : group) {
                    if (entry.getLongValue() <= 0 || entry.getLongValue() > Integer.MAX_VALUE)
                        return false;
                    int amount = (int) entry.getLongValue();
                    if (ResourceTransfer.insert(
                                    endpoint.level(),
                                    endpoint.machine(),
                                    endpoint.direction().getOpposite(),
                                    entry.getKey(),
                                    amount,
                                    transaction)
                            != amount) return false;
                }
            transaction.commit();
        }
        for (var group : inputs) group.clear();
        return true;
    }
}

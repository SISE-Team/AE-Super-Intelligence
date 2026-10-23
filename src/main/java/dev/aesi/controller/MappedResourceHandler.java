// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import java.util.function.IntFunction;

/**
 * Fixed-size handler whose indices are resolved on every call, so a packed or paged view
 * stays transactional (each target is an AE2 journaled handler) while its layout changes.
 */
public final class MappedResourceHandler implements ResourceHandler<ItemResource> {
    /** A resolved index: the backing handler and its index, or null for a hole. */
    public record Target(ResourceHandler<ItemResource> handler, int index) {}
    private final int size;
    private final IntFunction<Target> resolve;
    public MappedResourceHandler(int size, IntFunction<Target> resolve){this.size=size;this.resolve=resolve;}
    private Target at(int index){return index<0||index>=size?null:resolve.apply(index);}
    public int size(){return size;}
    public ItemResource getResource(int index){var t=at(index);return t==null?ItemResource.EMPTY:t.handler.getResource(t.index);}
    public long getAmountAsLong(int index){var t=at(index);return t==null?0:t.handler.getAmountAsLong(t.index);}
    public long getCapacityAsLong(int index,ItemResource resource){var t=at(index);return t==null?0:t.handler.getCapacityAsLong(t.index,resource);}
    public boolean isValid(int index,ItemResource resource){var t=at(index);return t!=null&&t.handler.isValid(t.index,resource);}
    public int insert(int index,ItemResource resource,int amount,TransactionContext transaction){var t=at(index);return t==null?0:t.handler.insert(t.index,resource,amount,transaction);}
    public int extract(int index,ItemResource resource,int amount,TransactionContext transaction){var t=at(index);return t==null?0:t.handler.extract(t.index,resource,amount,transaction);}
}

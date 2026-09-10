package com.techbucketdivision.mystcraft.menu.slot;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A fixed-size window over a larger {@link ItemStacksResourceHandler} with a movable offset (used for the Writing
 * Desk's scrolling notebook tabs). Every window slot is limited to one item.
 */
public class WindowedItemHandler implements ResourceHandler<ItemResource> {
    private final ItemStacksResourceHandler delegate;
    private final int windowSize;
    private final int stackLimit;
    private int offset;

    public WindowedItemHandler(ItemStacksResourceHandler delegate, int windowSize, int stackLimit) {
        this.delegate = delegate;
        this.windowSize = windowSize;
        this.stackLimit = stackLimit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        int max = Math.max(0, delegate.size() - windowSize);
        this.offset = Math.max(0, Math.min(offset, max));
    }

    private int map(int index) {
        return index + offset;
    }

    /** {@code IndexModifier} for {@code ResourceHandlerSlot}. */
    public void set(int index, ItemResource resource, int amount) {
        delegate.set(map(index), resource, amount);
    }

    @Override
    public int size() {
        return windowSize;
    }

    @Override
    public ItemResource getResource(int index) {
        return delegate.getResource(map(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return delegate.getAmountAsLong(map(index));
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return Math.min(stackLimit, delegate.getCapacityAsLong(map(index), resource));
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return delegate.isValid(map(index), resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        int room = stackLimit - delegate.getAmountAsInt(map(index));
        if (room <= 0) return 0;
        return delegate.insert(map(index), resource, Math.min(amount, room), transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return delegate.extract(map(index), resource, amount, transaction);
    }
}

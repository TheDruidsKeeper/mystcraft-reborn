package com.tbd.mystcraft.blockentity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;

import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;

/**
 * {@link ItemStacksResourceHandler} with a per-slot validity filter, per-slot stack limit and a change callback (the
 * replacement for the original {@code IOInventory} with {@code InventoryFilter}).
 */
public class FilteredItemHandler extends ItemStacksResourceHandler {
    private final BiPredicate<Integer, ItemResource> filter;
    private final IntUnaryOperator slotLimit;
    private final Runnable onChange;

    /**
     * @param filter    (slot, resource) → accepted
     * @param slotLimit slot → max stack size (values > 64 are capped by the item's own limit)
     * @param onChange  invoked after any content change
     */
    public FilteredItemHandler(int size, BiPredicate<Integer, ItemResource> filter, IntUnaryOperator slotLimit, Runnable onChange) {
        super(size);
        this.filter = filter;
        this.slotLimit = slotLimit;
        this.onChange = onChange;
    }

    public FilteredItemHandler(int size, BiPredicate<Integer, ItemResource> filter, Runnable onChange) {
        this(size, filter, i -> 64, onChange);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return resource.isEmpty() || filter.test(index, resource);
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        return Math.min(super.getCapacity(index, resource), slotLimit.applyAsInt(index));
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previous) {
        onChange.run();
    }

    /** Convenience read. */
    public ItemStack getStack(int index) {
        return ItemUtil.getStack(this, index);
    }

    /** Direct overwrite (fires the change callback). */
    public void setStack(int index, ItemStack stack) {
        set(index, ItemResource.of(stack), stack.getCount());
    }

    public boolean isEmpty() {
        for (int i = 0; i < size(); i++) if (getAmountAsInt(i) > 0) return false;
        return true;
    }
}

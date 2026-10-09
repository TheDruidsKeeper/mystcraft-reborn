package com.tbd.mystcraft.blockentity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Moves fluid between a single ink container item and a tank. {@code ItemAccess.forStack} must NOT be used for this:
 * it "never changes the underlying Item", so a bucket could never become an empty bucket and every transfer silently
 * returned 0 (the playtest "putting the ink bucket into the desk does nothing"). A one-slot scratch handler lets the
 * container's fluid handler exchange the item; the result is read back from the scratch slot.
 */
public final class InkContainers {
    private InkContainers() {}

    /** Result of a transfer: the container after the exchange (may be empty, e.g. a consumed bottle). */
    public record Result(ItemStack container, int amount) {}

    /** Drains up to {@code amount} of {@code resource} from one {@code container} into {@code tank} slot {@code tankIndex}. */
    public static @Nullable Result drainInto(ItemStack container, FluidResource resource, int amount, ResourceHandler<FluidResource> tank, int tankIndex) {
        ItemStacksResourceHandler scratch = scratch(container);
        ItemAccess access = ItemAccess.forHandlerIndexStrict(scratch, 0);
        ResourceHandler<FluidResource> handler = access.getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) return null;
        try (Transaction tx = Transaction.openRoot()) {
            int drained = handler.extract(resource, amount, tx);
            if (drained <= 0) return null;
            int filled = tank.insert(tankIndex, resource, drained, tx);
            if (filled != drained) return null;
            tx.commit();
            return new Result(stackOf(scratch), drained);
        }
    }

    /** Fills one {@code container} with up to {@code amount} of the fluid in {@code tank} slot {@code tankIndex}. */
    public static @Nullable Result fillFrom(ItemStack container, ResourceHandler<FluidResource> tank, int tankIndex, int amount) {
        FluidResource resource = tank.getResource(tankIndex);
        if (resource.isEmpty()) return null;
        ItemStacksResourceHandler scratch = scratch(container);
        ItemAccess access = ItemAccess.forHandlerIndexStrict(scratch, 0);
        ResourceHandler<FluidResource> handler = access.getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) return null;
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(resource, amount, tx);
            if (inserted <= 0) return null;
            int drained = tank.extract(tankIndex, resource, inserted, tx);
            if (drained != inserted) return null;
            tx.commit();
            return new Result(stackOf(scratch), inserted);
        }
    }

    /** Whether {@code stack} (a container) exposes an item fluid handler at all. */
    public static boolean isFluidContainer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return ItemAccess.forHandlerIndexStrict(scratch(stack), 0).getCapability(Capabilities.Fluid.ITEM) != null;
    }

    private static ItemStack stackOf(ItemStacksResourceHandler scratch) {
        int amount = scratch.getAmountAsInt(0);
        return amount <= 0 ? ItemStack.EMPTY : scratch.getResource(0).toStack(amount);
    }

    private static ItemStacksResourceHandler scratch(ItemStack container) {
        ItemStacksResourceHandler scratch = new ItemStacksResourceHandler(1);
        scratch.set(0, ItemResource.of(container), 1);
        return scratch;
    }
}

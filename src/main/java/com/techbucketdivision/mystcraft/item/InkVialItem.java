package com.techbucketdivision.mystcraft.item;

import com.techbucketdivision.mystcraft.registry.ModFluids;
import com.techbucketdivision.mystcraft.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Ink Vial (REQUIREMENTS §2.7): a fixed 1000 mB container of black ink. The fluid capability is exposed through
 * {@link Handler} (registered in {@link ItemCapabilities}); draining converts the vial into a glass bottle, filling a
 * glass bottle with ≥1000 mB of black ink converts it into a vial.
 */
public class InkVialItem extends Item {

    public static final int VOLUME = FluidType.BUCKET_VOLUME;

    public InkVialItem(Item.Properties properties) {
        super(properties);
    }

    public static boolean isVial(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof InkVialItem;
    }

    public static FluidStack contents() {
        return new FluidStack(ModFluids.BLACK_INK.get(), VOLUME);
    }

    public static FluidResource inkResource() {
        return FluidResource.of(ModFluids.BLACK_INK.get());
    }

    /**
     * Single-slot fluid handler backed by an {@link ItemAccess}. Only whole vials are moved: extraction of 1000 mB
     * exchanges one vial for one glass bottle, insertion of 1000 mB exchanges one glass bottle for one vial.
     */
    public static final class Handler implements ResourceHandler<FluidResource> {
        private final ItemAccess access;

        public Handler(ItemAccess access) {
            this.access = access;
        }

        private boolean holdsVial() {
            ItemResource res = access.getResource();
            return !res.isEmpty() && res.getItem() instanceof InkVialItem;
        }

        private boolean holdsBottle() {
            ItemResource res = access.getResource();
            return !res.isEmpty() && res.is(Items.GLASS_BOTTLE);
        }

        private static boolean isInk(FluidResource resource) {
            return !resource.isEmpty() && ModFluids.isInk(resource.getFluid());
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            return holdsVial() ? inkResource() : FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return holdsVial() ? (long) VOLUME * access.getAmount() : 0L;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return (long) VOLUME * Math.max(1, access.getAmount());
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return isInk(resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            if (index != 0 || !isInk(resource) || amount < VOLUME || !holdsBottle()) return 0;
            int vials = Math.min(amount / VOLUME, access.getAmount());
            int exchanged = access.exchange(ItemResource.of(ModItems.INK_VIAL.get()), vials, transaction);
            return exchanged * VOLUME;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            if (index != 0 || !isInk(resource) || amount < VOLUME || !holdsVial()) return 0;
            int vials = Math.min(amount / VOLUME, access.getAmount());
            int exchanged = access.exchange(ItemResource.of(Items.GLASS_BOTTLE), vials, transaction);
            return exchanged * VOLUME;
        }
    }
}

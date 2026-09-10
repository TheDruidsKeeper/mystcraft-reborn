package com.techbucketdivision.mystcraft.menu.slot;

import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

import java.util.function.BooleanSupplier;

/** A {@link ResourceHandlerSlot} whose visibility / usability is controlled by a supplier. */
public class ToggleHandlerSlot extends ResourceHandlerSlot {
    private final BooleanSupplier active;

    public ToggleHandlerSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier, int index, int x, int y, BooleanSupplier active) {
        super(handler, modifier, index, x, y);
        this.active = active;
    }

    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }
}

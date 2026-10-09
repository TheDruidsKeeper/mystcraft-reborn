package com.tbd.mystcraft.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import java.util.function.BooleanSupplier;

/** A vanilla container slot whose visibility / usability is controlled by a supplier. */
public class ToggleSlot extends Slot {
    private final BooleanSupplier active;

    public ToggleSlot(Container container, int index, int x, int y, BooleanSupplier active) {
        super(container, index, x, y);
        this.active = active;
    }

    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }
}

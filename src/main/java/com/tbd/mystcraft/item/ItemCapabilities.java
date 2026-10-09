package com.tbd.mystcraft.item;

import com.tbd.mystcraft.registry.ModItems;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Item capability registration (called from the mod-bus {@code RegisterCapabilitiesEvent} listener). */
public final class ItemCapabilities {
    private ItemCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        // Vial: exposes one vial of black ink; draining turns it into a glass bottle.
        event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new InkVialItem.Handler(access), ModItems.INK_VIAL.get());
        // Glass bottle: accepts one vial of black ink and becomes a vial.
        // Other mods may register their own bottle handler; NeoForge queries providers in registration order.
        event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new InkVialItem.Handler(access), Items.GLASS_BOTTLE);
    }
}

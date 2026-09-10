package com.techbucketdivision.mystcraft.item;

import com.techbucketdivision.mystcraft.registry.ModItems;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Item capability registration (called from the mod-bus {@code RegisterCapabilitiesEvent} listener). */
public final class ItemCapabilities {
    private ItemCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        // Vial: exposes 1000 mB of black ink; draining turns it into a glass bottle.
        event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new InkVialItem.Handler(access), ModItems.INK_VIAL.get());
        // Glass bottle: accepts 1000 mB of black ink and becomes a vial (the original vial handler also handled this).
        // Other mods may register their own bottle handler; NeoForge queries providers in registration order.
        event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new InkVialItem.Handler(access), Items.GLASS_BOTTLE);
    }
}

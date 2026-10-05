package com.tbd.mystcraft.event;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.blockentity.BlockEntityCapabilities;
import com.tbd.mystcraft.item.ItemCapabilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Mod-bus listeners (auto-routed: RegisterCapabilitiesEvent implements IModBusEvent). */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class ModBusEvents {
    private ModBusEvents() {}

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        BlockEntityCapabilities.register(event);
        ItemCapabilities.register(event);
    }
}

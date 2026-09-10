package com.techbucketdivision.mystcraft.event;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.blockentity.BlockEntityCapabilities;
import com.techbucketdivision.mystcraft.item.ItemCapabilities;
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

package com.tbd.mystcraft;

import com.tbd.mystcraft.client.ClientSetup;
import com.tbd.mystcraft.config.ClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point. All client registration lives in {@link ClientSetup}. */
@Mod(value = Mystcraft.MOD_ID, dist = Dist.CLIENT)
public final class MystcraftClient {
    public MystcraftClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        ClientSetup.register(modBus);
    }
}

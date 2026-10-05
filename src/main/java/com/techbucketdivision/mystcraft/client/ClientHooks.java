package com.tbd.mystcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Client-only lookups for common code that already knows it runs on the client (tooltips). */
public final class ClientHooks {
    private ClientHooks() {}

    public static @Nullable Player player() {
        return Minecraft.getInstance().player;
    }
}

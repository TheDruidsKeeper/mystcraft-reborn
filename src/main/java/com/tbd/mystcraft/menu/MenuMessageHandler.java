package com.tbd.mystcraft.menu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Implemented by menus that accept {@code MenuMessagePayload}s (on both sides). */
public interface MenuMessageHandler {
    /**
     * @param player the player who sent (server) / receives (client) the message
     * @param data   message data; the message name is {@code data.getStringOr("msg", "")}
     */
    void processMessage(Player player, CompoundTag data);
}

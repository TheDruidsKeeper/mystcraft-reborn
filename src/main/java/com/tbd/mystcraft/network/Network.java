package com.tbd.mystcraft.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends mod payloads only to players whose connection negotiated them: a vanilla client, or a mock player in a game
 * test, has no channel for them and NeoForge throws on a plain {@code sendToPlayer}.
 */
public final class Network {
    private Network() {}

    public static boolean canReceive(ServerPlayer player, CustomPacketPayload payload) {
        return player.connection != null && player.connection.hasChannel(payload.type());
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (canReceive(player, payload)) PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) sendToPlayer(player, payload);
    }
}

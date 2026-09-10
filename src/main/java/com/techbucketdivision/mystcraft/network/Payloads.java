package com.techbucketdivision.mystcraft.network;

import com.techbucketdivision.mystcraft.Mystcraft;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers every payload. Client-bound handlers are registered on the client in
 * {@code client.ClientPayloadHandlers} via {@code RegisterClientPayloadHandlersEvent}.
 */
public final class Payloads {
    private Payloads() {}

    public static final String VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);
        // server -> client
        r.playToClient(AgeDataSyncPayload.TYPE, AgeDataSyncPayload.STREAM_CODEC);
        r.playToClient(UpdateDimensionsPayload.TYPE, UpdateDimensionsPayload.STREAM_CODEC);
        r.playToClient(ServerConfigPayload.TYPE, ServerConfigPayload.STREAM_CODEC);
        r.playToClient(LinkParticlesPayload.TYPE, LinkParticlesPayload.STREAM_CODEC);
        r.playToClient(ExplosionEffectsPayload.TYPE, ExplosionEffectsPayload.STREAM_CODEC);
        r.playToClient(LightningPayload.TYPE, LightningPayload.STREAM_CODEC);
        r.playToClient(ProfilingStatePayload.TYPE, ProfilingStatePayload.STREAM_CODEC);
        // bidirectional GUI messages
        r.playBidirectional(MenuMessagePayload.TYPE, MenuMessagePayload.STREAM_CODEC, MenuMessagePayload::handleServer);
    }
}

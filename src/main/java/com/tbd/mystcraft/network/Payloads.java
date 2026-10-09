package com.tbd.mystcraft.network;

import com.tbd.mystcraft.Mystcraft;
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
        r.playToClient(KnowledgePayload.TYPE, KnowledgePayload.STREAM_CODEC);
        r.playToClient(LinkParticlesPayload.TYPE, LinkParticlesPayload.STREAM_CODEC);
        r.playToClient(ExplosionEffectsPayload.TYPE, ExplosionEffectsPayload.STREAM_CODEC);
        r.playToClient(LightningPayload.TYPE, LightningPayload.STREAM_CODEC);
        r.playToClient(ProfilingStatePayload.TYPE, ProfilingStatePayload.STREAM_CODEC);
        r.playToClient(PanelImagePayloads.CaptureRequest.TYPE, PanelImagePayloads.CaptureRequest.STREAM_CODEC);
        r.playToClient(PanelImagePayloads.Images.TYPE, PanelImagePayloads.Images.STREAM_CODEC);
        // client -> server
        r.playToServer(PanelImagePayloads.Upload.TYPE, PanelImagePayloads.Upload.STREAM_CODEC, PanelImagePayloads.Upload::handleServer);
        r.playToServer(PanelImagePayloads.Request.TYPE, PanelImagePayloads.Request.STREAM_CODEC, PanelImagePayloads.Request::handleServer);
        // bidirectional GUI messages
        r.playBidirectional(MenuMessagePayload.TYPE, MenuMessagePayload.STREAM_CODEC, MenuMessagePayload::handleServer);
    }
}

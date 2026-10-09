package com.tbd.mystcraft.network;

import com.tbd.mystcraft.linking.PanelImageStorage;
import com.tbd.mystcraft.util.MystIds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Link panel pictures (original spec §8.5 extension): a book's link panel shows what the destination looks like.
 * <ol>
 *     <li>{@link CaptureRequest} (server → client): after a link the server asks the arriving client to photograph the
 *     view for {@code key} ({@link PanelImageStorage#keyFor}).</li>
 *     <li>{@link Upload} (client → server): the small PNG the client took.</li>
 *     <li>{@link Request} (client → server) / {@link Images} (server → client): a book GUI asks for the pictures of
 *     a key and receives all stored frames (newest last) for its slideshow.</li>
 * </ol>
 */
public final class PanelImagePayloads {
    private PanelImagePayloads() {}

    /** Hard cap on one uploaded picture (the client sends ~10 KB PNGs). */
    public static final int MAX_IMAGE_BYTES = 64 * 1024;
    public static final int MAX_KEY_LENGTH = 128;

    private static final StreamCodec<ByteBuf, String> KEY = ByteBufCodecs.stringUtf8(MAX_KEY_LENGTH);
    private static final StreamCodec<ByteBuf, byte[]> IMAGE = ByteBufCodecs.byteArray(MAX_IMAGE_BYTES);

    public record CaptureRequest(String key) implements CustomPacketPayload {
        public static final Type<CaptureRequest> TYPE = new Type<>(MystIds.id("panel_capture_request"));
        public static final StreamCodec<ByteBuf, CaptureRequest> STREAM_CODEC = KEY.map(CaptureRequest::new, CaptureRequest::key);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Upload(String key, byte[] png) implements CustomPacketPayload {
        public static final Type<Upload> TYPE = new Type<>(MystIds.id("panel_upload"));
        public static final StreamCodec<ByteBuf, Upload> STREAM_CODEC = StreamCodec.composite(
                KEY, Upload::key, IMAGE, Upload::png, Upload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleServer(Upload payload, IPayloadContext ctx) {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            PanelImageStorage storage = PanelImageStorage.get(player.level().getServer());
            if (storage == null) return;
            storage.add(payload.key(), payload.png());
        }
    }

    public record Request(String key) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(MystIds.id("panel_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = KEY.map(Request::new, Request::key);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleServer(Request payload, IPayloadContext ctx) {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            PanelImageStorage storage = PanelImageStorage.get(player.level().getServer());
            List<byte[]> frames = storage == null ? List.of() : storage.frames(payload.key());
            Network.sendToPlayer(player, new Images(payload.key(), frames));
        }
    }

    public record Images(String key, List<byte[]> frames) implements CustomPacketPayload {
        public static final Type<Images> TYPE = new Type<>(MystIds.id("panel_images"));
        public static final StreamCodec<ByteBuf, Images> STREAM_CODEC = StreamCodec.composite(
                KEY, Images::key,
                IMAGE.apply(ByteBufCodecs.list(PanelImageStorage.MAX_FRAMES)), Images::frames,
                Images::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}

package com.techbucketdivision.mystcraft.network;

import com.techbucketdivision.mystcraft.util.MystIds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-controlled client options (label rendering permission). */
public record ServerConfigPayload(boolean serverLabels) implements CustomPacketPayload {
    public static final Type<ServerConfigPayload> TYPE = new Type<>(MystIds.id("server_config"));
    public static final StreamCodec<ByteBuf, ServerConfigPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(ServerConfigPayload::new, ServerConfigPayload::serverLabels);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

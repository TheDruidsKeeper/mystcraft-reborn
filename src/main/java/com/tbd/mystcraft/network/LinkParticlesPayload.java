package com.tbd.mystcraft.network;

import com.tbd.mystcraft.util.MystIds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Spawn 50 link particles at a position on the client. */
public record LinkParticlesPayload(double x, double y, double z) implements CustomPacketPayload {
    public static final Type<LinkParticlesPayload> TYPE = new Type<>(MystIds.id("link_particles"));
    public static final StreamCodec<ByteBuf, LinkParticlesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, LinkParticlesPayload::x,
            ByteBufCodecs.DOUBLE, LinkParticlesPayload::y,
            ByteBufCodecs.DOUBLE, LinkParticlesPayload::z,
            LinkParticlesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

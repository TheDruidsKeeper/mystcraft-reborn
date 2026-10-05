package com.tbd.mystcraft.network;

import com.tbd.mystcraft.util.MystIds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Spawns a coloured, visual-only lightning bolt on the client. Colour is 0xRRGGBB. */
public record LightningPayload(double x, double y, double z, int color) implements CustomPacketPayload {
    public static final Type<LightningPayload> TYPE = new Type<>(MystIds.id("lightning"));
    public static final StreamCodec<ByteBuf, LightningPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, LightningPayload::x,
            ByteBufCodecs.DOUBLE, LightningPayload::y,
            ByteBufCodecs.DOUBLE, LightningPayload::z,
            ByteBufCodecs.INT, LightningPayload::color,
            LightningPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

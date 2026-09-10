package com.techbucketdivision.mystcraft.network;

import com.techbucketdivision.mystcraft.util.MystIds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Baseline profiling started/finished toast. */
public record ProfilingStatePayload(boolean running) implements CustomPacketPayload {
    public static final Type<ProfilingStatePayload> TYPE = new Type<>(MystIds.id("profiling_state"));
    public static final StreamCodec<ByteBuf, ProfilingStatePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(ProfilingStatePayload::new, ProfilingStatePayload::running);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

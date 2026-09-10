package com.techbucketdivision.mystcraft.network;

import com.techbucketdivision.mystcraft.util.MystIds;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Client-side visual effects of an {@code AdvancedExplosion} (meteor impacts). */
public record ExplosionEffectsPayload(double x, double y, double z, float size, List<BlockPos> affected) implements CustomPacketPayload {
    public static final Type<ExplosionEffectsPayload> TYPE = new Type<>(MystIds.id("explosion_effects"));
    public static final StreamCodec<ByteBuf, ExplosionEffectsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, ExplosionEffectsPayload::x,
            ByteBufCodecs.DOUBLE, ExplosionEffectsPayload::y,
            ByteBufCodecs.DOUBLE, ExplosionEffectsPayload::z,
            ByteBufCodecs.FLOAT, ExplosionEffectsPayload::size,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ExplosionEffectsPayload::affected,
            ExplosionEffectsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

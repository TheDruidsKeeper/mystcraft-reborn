package com.techbucketdivision.mystcraft.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Float health of a linking item (REQUIREMENTS §19.2). */
public record BookHealth(float health, float maxHealth) {
    public static final float DEFAULT_MAX = 10f;
    public static final BookHealth FULL = new BookHealth(DEFAULT_MAX, DEFAULT_MAX);

    public static final Codec<BookHealth> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("health").forGetter(BookHealth::health),
            Codec.FLOAT.optionalFieldOf("max_health", DEFAULT_MAX).forGetter(BookHealth::maxHealth)
    ).apply(i, BookHealth::new));
    public static final StreamCodec<ByteBuf, BookHealth> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, BookHealth::health, ByteBufCodecs.FLOAT, BookHealth::maxHealth, BookHealth::new);

    public BookHealth withHealth(float h) {
        return new BookHealth(Math.max(0, Math.min(maxHealth, h)), maxHealth);
    }

    public boolean isDamaged() {
        return health < maxHealth;
    }

    public float damage() {
        return maxHealth - health;
    }
}

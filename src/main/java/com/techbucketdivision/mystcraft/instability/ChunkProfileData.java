package com.tbd.mystcraft.instability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * One accumulation map of the chunk profiler (original spec §6.2): a per-cell counter over a 16×16 column of
 * {@link #LAYERS} layers, indexed {@code (y - minY) << 8 | z << 4 | x}, plus the number of samples.
 */
public final class ChunkProfileData {
    /** Age levels span y -64..319. */
    public static final int MIN_Y = -64;
    public static final int LAYERS = 384;
    public static final int MAP_LENGTH = LAYERS * 256;

    public static final Codec<ChunkProfileData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("count", 0).forGetter(d -> d.count),
            Codec.INT_STREAM.optionalFieldOf("data").forGetter(d -> java.util.Optional.of(Arrays.stream(d.data)))
    ).apply(i, (count, data) -> new ChunkProfileData(count, data.map(IntStream::toArray).orElse(null))));

    public int[] data;
    public int count;

    public ChunkProfileData() {
        this.data = new int[MAP_LENGTH];
        this.count = 0;
    }

    private ChunkProfileData(int count, int @org.jspecify.annotations.Nullable [] data) {
        if (data == null || data.length != MAP_LENGTH) {
            this.data = new int[MAP_LENGTH];
            this.count = 0;
        } else {
            this.data = data;
            this.count = count;
        }
    }

    public static int index(int localX, int y, int localZ) {
        return (y - MIN_Y) << 8 | localZ << 4 | localX;
    }
}

package com.tbd.mystcraft.age.celestial;

import com.tbd.mystcraft.api.symbol.ColorGradient;
import com.tbd.mystcraft.api.symbol.logic.Celestial;
import org.jspecify.annotations.Nullable;

import java.util.Random;

/**
 * Rainbow doodad (original spec §4.3.1 Rainbow): an arc rotated by -angle (random 0..360 when no angle was written).
 * The renderer draws {@code renderRainbow(0, 50)} about the Y axis by {@link #angle()}. Provides no light.
 */
public final class RainbowCelestial implements Celestial {
    public static final float ARC_OFFSET = 0f;
    public static final int ARC_SEGMENTS = 50;

    private final float angle;

    public RainbowCelestial(long seed, @Nullable Float angleDegrees) {
        Random rand = new Random(seed);
        float a = angleDegrees == null ? (float) (rand.nextDouble() * 360.0) : angleDegrees;
        this.angle = -a;
    }

    @Override public Kind kind() { return Kind.RAINBOW; }
    @Override public boolean providesLight() { return false; }
    @Override public float getAltitudeAngle(long time, float partialTick) { return 0.5f; }
    @Override public long getTimeToDawn(long time) { return Long.MAX_VALUE; }
    @Override public float angle() { return angle; }
    @Override public long period() { return 0L; }
    @Override public @Nullable ColorGradient gradient() { return null; }
    @Override public @Nullable ColorGradient horizonGradient() { return null; }
    @Override public float size() { return ARC_SEGMENTS; }
}

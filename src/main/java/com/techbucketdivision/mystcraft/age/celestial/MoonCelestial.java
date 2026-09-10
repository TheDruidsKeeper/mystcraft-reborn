package com.techbucketdivision.mystcraft.age.celestial;

import com.techbucketdivision.mystcraft.api.symbol.ColorGradient;
import com.techbucketdivision.mystcraft.api.symbol.logic.Celestial;
import org.jspecify.annotations.Nullable;

import java.util.Random;

/**
 * A moon (REQUIREMENTS §4.3.2 MoonNormal): like the sun but default period {@code 1.8*rand+0.2} days, size 20, phase
 * {@code (time / period) mod 8}, no light; the horizon is drawn (alpha 0.3) only when a sunset gradient was given.
 */
public final class MoonCelestial implements Celestial {
    public static final float SIZE = 20f;
    public static final float HORIZON_ALPHA = 0.3f;

    private final long period;
    private final float angle;
    private final float offset;
    private final @Nullable ColorGradient sunset;

    public MoonCelestial(long seed, @Nullable Float periodFactor, @Nullable Float angleDegrees, @Nullable Float phaseDegrees,
                         @Nullable ColorGradient sunset) {
        Random rand = new Random(seed);
        double p = periodFactor == null ? 1.8 * rand.nextDouble() + 0.2 : periodFactor;
        this.period = (long) (p * 24000L);
        float a = angleDegrees == null ? (float) (rand.nextDouble() * 360.0) : angleDegrees;
        this.angle = -a;
        Float off = phaseDegrees == null ? null : phaseDegrees / 360f;
        if (off == null) {
            off = rand.nextFloat();
            if (this.period == 0) off = off / 2f + 0.25f;
        }
        this.offset = off - 0.5f;
        this.sunset = sunset;
    }

    @Override public Kind kind() { return Kind.MOON; }
    @Override public boolean providesLight() { return false; }

    @Override
    public float getAltitudeAngle(long time, float partialTick) {
        return CelestialMath.easedAngle(time, partialTick, period, offset);
    }

    @Override
    public long getTimeToDawn(long time) {
        return Long.MAX_VALUE;
    }

    @Override
    public int phase(long time) {
        if (period == 0) return 0;
        return (int) (time / period) % 8;
    }

    @Override public float angle() { return angle; }
    @Override public long period() { return period; }
    @Override public @Nullable ColorGradient gradient() { return null; }
    /** Non-null only when a sunset gradient was written; the renderer draws no horizon otherwise. */
    @Override public @Nullable ColorGradient horizonGradient() { return sunset; }
    @Override public float size() { return SIZE; }

    public float offset() {
        return offset;
    }
}

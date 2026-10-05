package com.techbucketdivision.mystcraft.age.celestial;

import com.techbucketdivision.mystcraft.api.symbol.ColorGradient;
import com.techbucketdivision.mystcraft.api.symbol.logic.Celestial;
import org.jspecify.annotations.Nullable;

import java.util.Random;

/**
 * A sun (original spec §4.3.2 SunNormal). Provides light. Period = (wavelength or {@code 0.4*rand+0.8}) x 24000 ticks;
 * angle = -(angle or random 360); offset = phase/360 (or random; if period == 0: {@code rand/2 + 0.25}) - 0.5.
 */
public final class SunCelestial implements Celestial {
    public static final float SIZE = 30f;

    private final long period;
    private final float angle;
    private final float offset;
    private final @Nullable ColorGradient sunset;

    public SunCelestial(long seed, @Nullable Float periodFactor, @Nullable Float angleDegrees, @Nullable Float phaseDegrees,
                        @Nullable ColorGradient sunset) {
        Random rand = new Random(seed);
        double p = periodFactor == null ? 0.4 * rand.nextDouble() + 0.8 : periodFactor;
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

    @Override public Kind kind() { return Kind.SUN; }
    @Override public boolean providesLight() { return true; }

    @Override
    public float getAltitudeAngle(long time, float partialTick) {
        return CelestialMath.easedAngle(time, partialTick, period, offset);
    }

    @Override
    public long getTimeToDawn(long time) {
        return CelestialMath.timeToDawn(time, period, offset);
    }

    @Override public float angle() { return angle; }
    @Override public long period() { return period; }
    @Override public @Nullable ColorGradient gradient() { return null; }
    @Override public @Nullable ColorGradient horizonGradient() { return sunset; }
    @Override public float size() { return SIZE; }

    public float offset() {
        return offset;
    }
}

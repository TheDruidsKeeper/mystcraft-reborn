package com.tbd.mystcraft.age.celestial;

import com.tbd.mystcraft.api.symbol.ColorGradient;
import com.tbd.mystcraft.api.symbol.logic.Celestial;
import com.tbd.mystcraft.util.Colors;
import org.jspecify.annotations.Nullable;

import java.util.Random;

/**
 * Normal starfield (original spec §4.3.2 StarsNormal): 1500 vanilla-style stars (seed {@link #STAR_SEED}), colour =
 * gradient(time/12000), alpha = star brightness x (1 - rain), rotating with period (wavelength or {@code 1.8*rand+0.2})
 * x 240000 ticks using the eased angle formula. The renderer builds the star mesh from {@link #STAR_SEED}.
 */
public class StarfieldCelestial implements Celestial {
    public static final long STAR_SEED = 10842L;
    public static final int STAR_COUNT = 1500;

    protected final long period;
    protected final float angle;
    protected final ColorGradient gradient;

    public StarfieldCelestial(long seed, @Nullable Float periodFactor, @Nullable Float angleDegrees, ColorGradient gradient) {
        Random rand = new Random(seed);
        double p = periodFactor == null ? 1.8 * rand.nextDouble() + 0.2 : periodFactor;
        this.period = (long) (p * 240000L);
        float a = angleDegrees == null ? (float) (rand.nextDouble() * 360.0) : angleDegrees;
        this.angle = -a;
        this.gradient = gradient;
    }

    @Override public Kind kind() { return Kind.STARS; }
    @Override public boolean providesLight() { return false; }

    /** Rotation fraction (eased) — used by the renderer as the rotation about X. */
    @Override
    public float getAltitudeAngle(long time, float partialTick) {
        return CelestialMath.easedAngle(time, partialTick, period, 0f);
    }

    @Override public long getTimeToDawn(long time) { return Long.MAX_VALUE; }
    @Override public float angle() { return angle; }
    @Override public long period() { return period; }
    @Override public ColorGradient gradient() { return gradient; }
    @Override public @Nullable ColorGradient horizonGradient() { return null; }
    @Override public float size() { return 0f; }

    /** Star colour at the given age time. */
    public Colors.RGB color(long time) {
        return gradient.getColor(time / 12000f);
    }
}

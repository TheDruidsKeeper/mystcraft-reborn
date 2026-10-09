package com.tbd.mystcraft.age.celestial;

import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Shared celestial maths (original spec §4.3.2). Server-safe, no client classes. */
public final class CelestialMath {
    private CelestialMath() {}

    /**
     * Eased altitude angle 0..1 for a periodic object: {@code f = ((time mod period) + partial) / period + offset}
     * wrapped to 0..1, then {@code f1 = f; f = 1 - (cos(f*pi) + 1) / 2; f = f1 + (f - f1) / 3}. A period of 0 returns
     * the offset unchanged (static object).
     */
    public static float easedAngle(long time, float partialTick, long period, float offset) {
        if (period == 0) return offset;
        int i = (int) (time % period);
        float f = (i + partialTick) / period + offset;
        if (f < 0.0f) ++f;
        if (f > 1.0f) --f;
        float f1 = f;
        f = 1.0f - (float) ((Math.cos(f * Math.PI) + 1.0) / 2.0);
        f = f1 + (f - f1) / 3.0f;
        return f;
    }

    /** {@code period * |0.75 - offset| - (time mod period)} (wrapping forward by one period when negative). */
    public static long timeToDawn(long time, long period, float offset) {
        if (period == 0) return Long.MAX_VALUE;
        long current = time % period;
        long next = (long) (period * Math.abs(0.75f - offset));
        if (current > next) next += period;
        return next - current;
    }

    /** Sunrise/sunset alpha as in vanilla: non-null only near the horizon (cos(angle*2pi) within +-0.4). */
    public static float @Nullable [] sunriseSunsetAlpha(float celestialAngle) {
        float f2 = 0.4f;
        float f3 = Mth.cos(celestialAngle * Mth.PI * 2.0f);
        float f4 = 0f;
        if (f3 >= f4 - f2 && f3 <= f4 + f2) {
            float f5 = ((f3 - f4) / f2) * 0.5f + 0.5f;
            float f6 = 1.0f - (1.0f - Mth.sin(f5 * Mth.PI)) * 0.99f;
            f6 *= f6;
            return new float[] {f5, f6};
        }
        return null;
    }

    /** Vanilla default sunrise colours for the position {@code f5} (0..1) along the horizon transition. */
    public static float[] defaultHorizonColor(float f5) {
        return new float[] {f5 * 0.3f + 0.7f, f5 * f5 * 0.7f + 0.2f, f5 * f5 * 0.0f + 0.2f};
    }
}

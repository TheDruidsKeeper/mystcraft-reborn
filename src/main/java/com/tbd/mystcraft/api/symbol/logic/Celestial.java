package com.tbd.mystcraft.api.symbol.logic;

import com.tbd.mystcraft.api.symbol.ColorGradient;
import org.jspecify.annotations.Nullable;

/**
 * A sky object (sun, moon, starfield, rainbow, end-sky). The controller keeps the list; the client sky renderer draws
 * them based on {@link #kind()} and the accessors, so this interface must stay free of client classes.
 */
public interface Celestial {
    enum Kind { SUN, MOON, STARS, STARS_TWINKLE, END_SKY, RAINBOW }

    Kind kind();

    /** Whether this object contributes to the day/night celestial angle (suns do). */
    boolean providesLight();

    /**
     * Altitude fraction 0..1 for the given age time (0.0 and 1.0 = horizon rising, 0.5 = zenith... mirrors vanilla
     * celestial angle semantics: 0 = noon-ish rising, 0.5 = midnight).
     */
    float getAltitudeAngle(long time, float partialTick);

    /** Ticks until the next dawn for this object (only meaningful when {@link #providesLight()}). */
    long getTimeToDawn(long time);

    /** Rotation about the vertical axis (degrees). */
    float angle();

    /** Period in ticks (0 = static). */
    long period();

    /** Colour gradient (stars/end-sky/moon horizon) or {@code null}. */
    @Nullable ColorGradient gradient();

    /** Sunset/horizon gradient or {@code null}. */
    @Nullable ColorGradient horizonGradient();

    /** Visual size (sun 30, moon 20). */
    float size();

    /** Moon phase 0..7 for moons, else 0. */
    default int phase(long time) {
        return 0;
    }
}

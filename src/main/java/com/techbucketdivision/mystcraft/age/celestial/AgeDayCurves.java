package com.techbucketdivision.mystcraft.age.celestial;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Day/night curves as a function of the Age's combined celestial angle (0 = noon, 0.5 = midnight), REQUIREMENTS §4.3.
 * 26.1 drives all of this through data-driven timelines on a world clock, but clocks are global per server, so an
 * Age's own celestial periods cannot use them; instead these curves are installed as environment-attribute layers
 * on both the server level ({@code AgeEnvironment}) and the client level ({@code AgeClientEnvironment}). The
 * constants reproduce the vanilla overworld timeline: sky light level bottoms out at 4/15, the lightmap factor at
 * 0.24, sky / fog / clouds multiply towards black / #0f0f16 / #191919 at night.
 */
public final class AgeDayCurves {
    private AgeDayCurves() {}

    private static final int NIGHT_FOG = 0xFF0F0F16;
    private static final int NIGHT_CLOUD = 0xFF191919;

    /** Classic brightness 0..1: {@code clamp(cos(angle * 2pi) * 2 + 0.5)} (the original {@code getSkyDarken} base). */
    public static float brightness(float celestialAngle) {
        return Mth.clamp(Mth.cos(celestialAngle * Mth.TWO_PI) * 2.0f + 0.5f, 0.0f, 1.0f);
    }

    /** Multiplier for {@code gameplay/sky_light_level}: 1 at day, 4/15 at night (vanilla timeline bounds). */
    public static float skyLightLevelFactor(float celestialAngle) {
        return 4f / 15f + brightness(celestialAngle) * (11f / 15f);
    }

    /** Multiplier for {@code visual/sky_light_factor}: 1 at day, 0.24 at night. */
    public static float skyLightFactor(float celestialAngle) {
        return 0.24f + brightness(celestialAngle) * 0.76f;
    }

    /** Sky colour darkening: multiply by white..black. */
    public static int skyColor(int base, float celestialAngle) {
        return ARGB.scaleRGB(base, brightness(celestialAngle));
    }

    /** Fog colour darkening: multiply by white..#0f0f16. */
    public static int fogColor(int base, float celestialAngle) {
        return ARGB.multiply(base, ARGB.srgbLerp(brightness(celestialAngle), NIGHT_FOG, 0xFFFFFFFF));
    }

    /** Cloud colour darkening: multiply by white..#191919 (alpha kept). */
    public static int cloudColor(int base, float celestialAngle) {
        return ARGB.multiply(base, ARGB.srgbLerp(brightness(celestialAngle), NIGHT_CLOUD, 0xFFFFFFFF));
    }
}

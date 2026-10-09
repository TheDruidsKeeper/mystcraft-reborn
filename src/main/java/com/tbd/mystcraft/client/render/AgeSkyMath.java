package com.tbd.mystcraft.client.render;

import com.tbd.mystcraft.age.AgeController;
import com.tbd.mystcraft.api.symbol.ColorGradient;
import com.tbd.mystcraft.api.symbol.logic.Celestial;
import com.tbd.mystcraft.api.symbol.logic.ColorKind;
import com.tbd.mystcraft.util.Colors;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Pure maths shared by the sky/fog/cloud hooks (Phase 1 drives the vanilla sky renderer with these values). */
public final class AgeSkyMath {
    private AgeSkyMath() {}

    /** First celestial of the given kind, or {@code null}. */
    public static @Nullable Celestial first(AgeController controller, Celestial.Kind kind) {
        for (Celestial c : controller.celestials()) {
            if (c.kind() == kind) return c;
        }
        return null;
    }

    public static @Nullable Celestial firstStars(AgeController controller) {
        Celestial c = first(controller, Celestial.Kind.STARS);
        if (c == null) c = first(controller, Celestial.Kind.STARS_TWINKLE);
        if (c == null) c = first(controller, Celestial.Kind.END_SKY);
        return c;
    }

    /** Vanilla-style celestial altitude (0..1) → degrees for {@code SkyRenderState}. */
    public static float angleDegrees(@Nullable Celestial c, long time, float partial, float fallbackFraction) {
        float f = c == null ? fallbackFraction : c.getAltitudeAngle(time, partial);
        f = f - (float) Math.floor(f);
        return f * 360f;
    }

    /** Classic vanilla star brightness curve from the combined celestial angle. */
    public static float starBrightness(float celestialAngle, float rainLevel) {
        float f = 1.0f - (Mth.cos(celestialAngle * Mth.TWO_PI) * 2.0f + 0.25f);
        f = Mth.clamp(f, 0.0f, 1.0f);
        return f * f * 0.5f * (1.0f - rainLevel * 0.5f);
    }

    /**
     * Sunrise/sunset band colour (ARGB) for the given angle; alpha is zero outside the horizon window. Uses the sun's
     * horizon gradient when one was written, else the vanilla curve.
     */
    public static int sunriseColor(@Nullable Celestial sun, float celestialAngle) {
        float cos = Mth.cos(celestialAngle * Mth.TWO_PI);
        if (cos < -0.4f || cos > 0.4f) return 0;
        float f = cos / 0.4f * 0.5f + 0.5f;
        float alpha = 1.0f - (1.0f - Mth.sin(f * Mth.PI)) * 0.99f;
        alpha *= alpha;
        float r, g, b;
        ColorGradient gradient = sun == null ? null : sun.horizonGradient();
        if (gradient != null && !gradient.isEmpty()) {
            Colors.RGB c = gradient.getColor(f * gradient.totalLength());
            r = c.r();
            g = c.g();
            b = c.b();
        } else {
            r = f * 0.3f + 0.7f;
            g = f * f * 0.7f + 0.2f;
            b = 0.2f;
        }
        return argb(alpha, r, g, b);
    }

    public static int argb(float a, float r, float g, float b) {
        int ai = Mth.clamp((int) (a * 255f), 0, 255);
        int ri = Mth.clamp((int) (r * 255f), 0, 255);
        int gi = Mth.clamp((int) (g * 255f), 0, 255);
        int bi = Mth.clamp((int) (b * 255f), 0, 255);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    public static int rgb(Colors.RGB c) {
        return argb(1f, c.r(), c.g(), c.b());
    }

    /** Dynamic colour of a kind or {@code null} (caller keeps vanilla). */
    public static Colors.@Nullable RGB color(AgeController controller, ColorKind kind, long time, float partial, float angle, float biomeTemp) {
        Colors.RGB c = controller.dynamicColor(kind, time, partial, angle, biomeTemp);
        if (c == null) c = controller.staticColor(kind);
        return c == null ? null : c.clamp();
    }
}

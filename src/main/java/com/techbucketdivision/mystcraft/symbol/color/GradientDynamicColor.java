package com.tbd.mystcraft.symbol.color;

import com.tbd.mystcraft.api.symbol.ColorGradient;
import com.tbd.mystcraft.api.symbol.logic.ColorKind;
import com.tbd.mystcraft.api.symbol.logic.DynamicColorProvider;
import com.tbd.mystcraft.util.Colors;
import net.minecraft.util.Mth;

/**
 * Gradient-driven sky/fog/cloud colour (original spec §4.3.1: ColorSky, ColorSkyNight, ColorFog, ColorCloud). The gradient
 * is sampled at {@code time / 12000} (one interval = half a day) and optionally scaled by the daylight factor
 * {@code clamp(cos(angle * 2pi) * 2 + 0.5, 0, 1)} (or its complement for night colours).
 */
public final class GradientDynamicColor implements DynamicColorProvider {
    public enum Daylight { NONE, DAY, NIGHT }

    private static final Colors.RGB NEAR_BLACK = new Colors.RGB(0.0001f, 0.0001f, 0.0001f);

    private final ColorKind kind;
    private final ColorGradient gradient;
    private final Daylight daylight;
    private final boolean avoidPureBlack;

    public GradientDynamicColor(ColorKind kind, ColorGradient gradient, Daylight daylight, boolean avoidPureBlack) {
        this.kind = kind;
        this.gradient = gradient;
        this.daylight = daylight;
        this.avoidPureBlack = avoidPureBlack;
    }

    public static GradientDynamicColor sky(ColorGradient gradient) {
        return new GradientDynamicColor(ColorKind.SKY, gradient, Daylight.DAY, false);
    }

    public static GradientDynamicColor nightSky(ColorGradient gradient) {
        return new GradientDynamicColor(ColorKind.SKY, gradient, Daylight.NIGHT, false);
    }

    public static GradientDynamicColor fog(ColorGradient gradient) {
        return new GradientDynamicColor(ColorKind.FOG, gradient, Daylight.NONE, true);
    }

    public static GradientDynamicColor cloud(ColorGradient gradient) {
        return new GradientDynamicColor(ColorKind.CLOUD, gradient, Daylight.NONE, false);
    }

    public ColorGradient gradient() {
        return gradient;
    }

    @Override
    public ColorKind kind() {
        return kind;
    }

    /** Vanilla daylight factor used by the sky colour. */
    public static float daylightFactor(float celestialAngle) {
        return Mth.clamp(Mth.cos(celestialAngle * Mth.TWO_PI) * 2.0f + 0.5f, 0f, 1f);
    }

    @Override
    public Colors.RGB getColor(long time, float partialTick, float celestialAngle, float biomeTemp) {
        Colors.RGB color = gradient.getColor(time / 12000f);
        switch (daylight) {
            case DAY -> color = color.scale(daylightFactor(celestialAngle));
            case NIGHT -> color = color.scale(1f - daylightFactor(celestialAngle));
            case NONE -> {}
        }
        if (avoidPureBlack && color.r() == 0f && color.g() == 0f && color.b() == 0f) {
            return NEAR_BLACK;
        }
        return color;
    }
}

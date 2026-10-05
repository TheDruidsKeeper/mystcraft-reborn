package com.tbd.mystcraft.symbol.color;

import com.tbd.mystcraft.api.symbol.logic.ColorKind;
import com.tbd.mystcraft.api.symbol.logic.DynamicColorProvider;
import com.tbd.mystcraft.util.Colors;
import net.minecraft.util.Mth;

/**
 * Vanilla-formula sky / fog / cloud colours (original spec §4.3.1: ColorSkyNat, ColorFogNat, ColorCloudNat).
 */
public final class NaturalDynamicColor implements DynamicColorProvider {
    private final ColorKind kind;

    public NaturalDynamicColor(ColorKind kind) {
        if (!kind.isDynamic()) throw new IllegalArgumentException("Not a dynamic colour kind: " + kind);
        this.kind = kind;
    }

    public static NaturalDynamicColor sky() {
        return new NaturalDynamicColor(ColorKind.SKY);
    }

    public static NaturalDynamicColor fog() {
        return new NaturalDynamicColor(ColorKind.FOG);
    }

    public static NaturalDynamicColor cloud() {
        return new NaturalDynamicColor(ColorKind.CLOUD);
    }

    @Override
    public ColorKind kind() {
        return kind;
    }

    @Override
    public Colors.RGB getColor(long time, float partialTick, float celestialAngle, float biomeTemp) {
        return switch (kind) {
            case SKY -> skyColor(celestialAngle, biomeTemp);
            case FOG -> fogColor(celestialAngle);
            default -> Colors.RGB.WHITE;
        };
    }

    /** {@code HSB(0.6222 - t*0.05, 0.5 + t*0.1, 1.0)} with {@code t = clamp(temp/3, -1, 1)}, scaled by daylight. */
    public static Colors.RGB skyColor(float celestialAngle, float biomeTemp) {
        float t = Mth.clamp(biomeTemp / 3.0f, -1.0f, 1.0f);
        Colors.RGB base = Colors.hsb(0.62222224f - t * 0.05f, 0.5f + t * 0.1f, 1.0f);
        return base.scale(GradientDynamicColor.daylightFactor(celestialAngle));
    }

    /** Vanilla overworld fog: (0.7529, 0.8471, 1.0) darkened by the daylight factor. */
    public static Colors.RGB fogColor(float celestialAngle) {
        float f = GradientDynamicColor.daylightFactor(celestialAngle);
        return new Colors.RGB(0.7529412f * (f * 0.94f + 0.06f), 0.8470588f * (f * 0.94f + 0.06f), 1.0f * (f * 0.91f + 0.09f));
    }
}

package com.tbd.mystcraft.age.celestial;

import com.tbd.mystcraft.api.symbol.ColorGradient;
import com.tbd.mystcraft.api.symbol.logic.Celestial;
import com.tbd.mystcraft.util.Colors;
import org.jspecify.annotations.Nullable;

/**
 * Ender starfield (original spec §4.3.2 StarsEndSky): the End sky box tinted by gradient(time/12000); default colour
 * 0x282828 = (0.156, 0.156, 0.156). Static (no rotation), no light.
 */
public final class EndSkyCelestial implements Celestial {
    public static final Colors.RGB DEFAULT_COLOR = new Colors.RGB(0.156f, 0.156f, 0.156f);

    private final ColorGradient gradient;

    public EndSkyCelestial(ColorGradient gradient) {
        this.gradient = gradient;
    }

    @Override public Kind kind() { return Kind.END_SKY; }
    @Override public boolean providesLight() { return false; }
    @Override public float getAltitudeAngle(long time, float partialTick) { return 0.5f; }
    @Override public long getTimeToDawn(long time) { return Long.MAX_VALUE; }
    @Override public float angle() { return 0f; }
    @Override public long period() { return 0L; }
    @Override public ColorGradient gradient() { return gradient; }
    @Override public @Nullable ColorGradient horizonGradient() { return null; }
    @Override public float size() { return 100f; }

    public Colors.RGB color(long time) {
        return gradient.getColor(time / 12000f);
    }
}

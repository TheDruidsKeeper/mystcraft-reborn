package com.tbd.mystcraft.age.lighting;

import com.tbd.mystcraft.api.symbol.logic.LightingController;

/**
 * Bright lighting (original spec §4.3.3 LightingBright): {@code t[i] = vanilla * 0.75 + 0.25};
 * {@code scale(v) = v + (15 - v) / 2}.
 */
public final class BrightLighting implements LightingController {
    public static final float FLOOR = 0.25f;

    @Override
    public float brightness(int lightLevel) {
        return NormalLighting.vanillaBrightness(lightLevel) * (1.0f - FLOOR) + FLOOR;
    }

    @Override
    public float scaleLighting(float value) {
        return value + (15f - value) / 2f;
    }

    @Override
    public float ambientLight() {
        return FLOOR;
    }
}

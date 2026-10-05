package com.tbd.mystcraft.age.lighting;

import com.tbd.mystcraft.api.symbol.logic.LightingController;

/** Dark lighting (original spec §4.3.3 LightingDark): {@code t[i] = vanilla / 2}; {@code scale(v) = v / 2}. */
public final class DarkLighting implements LightingController {

    @Override
    public float brightness(int lightLevel) {
        return NormalLighting.vanillaBrightness(lightLevel) / 2f;
    }

    @Override
    public float scaleLighting(float value) {
        return value / 2f;
    }

    @Override
    public float ambientLight() {
        return 0f;
    }
}

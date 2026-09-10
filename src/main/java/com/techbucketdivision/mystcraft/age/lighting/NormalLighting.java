package com.techbucketdivision.mystcraft.age.lighting;

import com.techbucketdivision.mystcraft.api.symbol.logic.LightingController;
import net.minecraft.util.Mth;

/** Vanilla light curve (REQUIREMENTS §4.3.3 LightingNormal): {@code f1 = 1 - i/15; (1 - f1) / (f1*3 + 1)}. */
public class NormalLighting implements LightingController {

    /** Vanilla brightness for light level {@code i} (0..15). */
    public static float vanillaBrightness(int lightLevel) {
        float f1 = 1.0f - Mth.clamp(lightLevel, 0, 15) / 15.0f;
        return (1.0f - f1) / (f1 * 3.0f + 1.0f);
    }

    @Override
    public float brightness(int lightLevel) {
        return vanillaBrightness(lightLevel);
    }

    @Override
    public float scaleLighting(float value) {
        return value;
    }

    @Override
    public float ambientLight() {
        return 0f;
    }
}

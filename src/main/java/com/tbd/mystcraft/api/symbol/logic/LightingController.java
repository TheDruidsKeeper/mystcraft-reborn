package com.tbd.mystcraft.api.symbol.logic;

/** Controls the light curve of an Age. Exactly one per Age. */
public interface LightingController {
    /**
     * Brightness for a light level 0..15 (vanilla: {@code (1-f1)/(f1*3+1)} with {@code f1 = 1 - i/15}).
     * Used by the client lightmap hook and for ambient light.
     */
    float brightness(int lightLevel);

    /** Scales an incoming light value 0..15 (e.g. bright lighting raises the floor). */
    float scaleLighting(float value);

    /** Ambient light factor 0..1 applied to the dimension (0 = vanilla overworld). */
    default float ambientLight() {
        return 0f;
    }
}

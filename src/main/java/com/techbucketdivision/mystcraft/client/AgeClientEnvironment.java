package com.tbd.mystcraft.client;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.age.AgeController;
import org.jspecify.annotations.Nullable;
import net.minecraft.util.ARGB;
import com.tbd.mystcraft.api.symbol.logic.LightingController;
import com.tbd.mystcraft.age.AgeManager;
import com.tbd.mystcraft.age.celestial.AgeDayCurves;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Installs the Age day/night curves ({@link AgeDayCurves}) as environment-attribute layers on the client level of an
 * Age. Vanilla computes lighting (sky light level, lightmap sky factor, sky/fog/cloud darkening, star brightness) from
 * timelines on a global world clock, which cannot express per-Age celestial periods; server-side layers are not
 * synced, so the client rebuilds its own attribute system here ({@code ClientLevel.environmentAttributes} is opened
 * by the access transformer). Colour symbols (sky/fog/cloud) still win through {@code ClientGameEvents}.
 */
public final class AgeClientEnvironment {
    private AgeClientEnvironment() {}

    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        if (!AgeManager.isAge(level.dimension())) return;
        install(level);
    }

    public static void install(ClientLevel level) {
        EnvironmentAttributeSystem.Builder builder = EnvironmentAttributeSystem.builder().addDefaultLayers(level);
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_LEVEL, (base, tick) -> base * AgeDayCurves.skyLightLevelFactor(angle(level)));
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, (base, tick) -> base * AgeDayCurves.skyLightFactor(angle(level)));
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (base, tick) -> AgeDayCurves.skyColor(base, angle(level)));
        builder.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (base, tick) -> AgeDayCurves.fogColor(base, angle(level)));
        builder.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (base, tick) -> AgeDayCurves.cloudColor(base, angle(level)));
        // Lighting symbols: the controller's light curve is expressed through the lightmap attributes - a raised floor
        // (bright) as ambient light, a lowered ceiling (dark) as sky-light factor and block-light tint. Time-based, not
        // constant: constant layers are baked when the system is built, before the Age's pages have been synced.
        builder.addTimeBasedLayer(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, (base, tick) -> {
            LightingController lighting = lighting(level);
            if (lighting == null) return base;
            float floor = Math.clamp(lighting.scaleLighting(0f) / 15f, 0f, 1f);
            return floor <= 0f ? base : ARGB.colorFromFloat(1f, floor, floor, floor);
        });
        builder.addTimeBasedLayer(EnvironmentAttributes.BLOCK_LIGHT_TINT, (base, tick) -> {
            LightingController lighting = lighting(level);
            if (lighting == null) return base;
            float ceiling = Math.clamp(lighting.scaleLighting(15f) / 15f, 0f, 1f);
            return ceiling >= 1f ? base : ARGB.scaleRGB(base, ceiling);
        });
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, (base, tick) -> {
            LightingController lighting = lighting(level);
            return lighting == null ? base : base * Math.clamp(lighting.scaleLighting(15f) / 15f, 0f, 1f);
        });
        level.environmentAttributes = builder.build();
        Mystcraft.LOGGER.info("[clientenv] installed Age day/night attribute layers for {}", level.dimension().identifier());
    }

    private static @Nullable LightingController lighting(ClientLevel level) {
        AgeController controller = ClientAgeData.controllerFor(level);
        return controller == null ? null : controller.lighting();
    }

    /** Combined celestial angle of the level's Age; noon when the Age is not synced yet. */
    private static float angle(ClientLevel level) {
        AgeController controller = ClientAgeData.controllerFor(level);
        if (controller == null) return 0f;
        return controller.celestialAngle(ClientAgeData.ageTime(level), 0f);
    }
}

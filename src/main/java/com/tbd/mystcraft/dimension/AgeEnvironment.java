package com.tbd.mystcraft.dimension;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.age.AgeController;
import com.tbd.mystcraft.age.celestial.AgeDayCurves;
import com.tbd.mystcraft.api.symbol.logic.ColorKind;
import com.tbd.mystcraft.util.Colors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.jspecify.annotations.Nullable;

/**
 * Installs server-side environment-attribute layers for an Age level (cloud height, sky/fog/cloud colour hints and
 * the sun angle derived from the Age's celestials). Clients mirror this from the synced {@code AgeData}; nothing
 * here is sent over the wire (see API_NOTES J).
 */
public final class AgeEnvironment {
    private AgeEnvironment() {}

    public static void apply(ServerLevel level, AgeController controller) {
        try {
            EnvironmentAttributeSystem system = EnvironmentAttributeSystem.builder()
                    .addDefaultLayers(level)
                    .addConstantLayer(EnvironmentAttributes.CLOUD_HEIGHT, base -> controller.sky().cloudHeight)
                    .addTimeBasedLayer(EnvironmentAttributes.SUN_ANGLE, (base, tick) -> sunAngle(controller))
                    .addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (base, tick) -> rgb(controller, ColorKind.SKY, base))
                    .addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (base, tick) -> rgb(controller, ColorKind.FOG, base))
                    .addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (base, tick) -> argb(controller, ColorKind.CLOUD, base))
                    // Day/night from the Age's celestials (no timeline can express per-Age periods): server-side
                    // sky light level drives skyDarken -> mob spawning / sleeping; the client installs the same
                    // curves in AgeClientEnvironment because attribute layers are not synced.
                    .addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_LEVEL, (base, tick) -> base * AgeDayCurves.skyLightLevelFactor(angle(controller)))
                    .build();
            level.setEnvironmentAttributes(system);
        } catch (Exception e) {
            Mystcraft.LOGGER.error("Failed to install environment attributes for {}", level.dimension().identifier(), e);
        }
    }

    private static float angle(AgeController controller) {
        return controller.celestialAngle(controller.ageData().worldTime(), 0f);
    }

    private static float sunAngle(AgeController controller) {
        long time = controller.ageData().worldTime();
        return controller.celestialAngle(time, 0f) * 360f;
    }

    private static Colors.@Nullable RGB color(AgeController controller, ColorKind kind) {
        long time = controller.ageData().worldTime();
        float angle = controller.celestialAngle(time, 0f);
        return controller.dynamicColor(kind, time, 0f, angle, 0.5f);
    }

    private static int rgb(AgeController controller, ColorKind kind, int base) {
        Colors.RGB c = color(controller, kind);
        return c == null ? base : c.clamp().toRGB();
    }

    private static int argb(AgeController controller, ColorKind kind, int base) {
        Colors.RGB c = color(controller, kind);
        if (c == null) return base;
        return ARGB.color(ARGB.alpha(base), c.clamp().toRGB());
    }
}

package com.tbd.mystcraft.client.render.tint;

import com.tbd.mystcraft.age.AgeController;
import com.tbd.mystcraft.api.symbol.logic.ColorKind;
import com.tbd.mystcraft.client.ClientAgeData;
import com.tbd.mystcraft.util.Colors;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;

/**
 * Static grass / foliage / water colour overrides of the Age the client is currently in (original spec §10 colour
 * symbols). Refreshed once per client tick on the render thread and read lock-free from chunk-meshing workers by
 * {@link AgeBiomeTintSource}; {@code 0} means "no override, use vanilla".
 */
public final class AgeBiomeTints {
    private AgeBiomeTints() {}

    private static volatile int grass;
    private static volatile int foliage;
    private static volatile int water;

    public static void update(@Nullable ClientLevel level) {
        AgeController controller = level == null ? null : ClientAgeData.controllerFor(level);
        grass = pack(controller, ColorKind.GRASS);
        foliage = pack(controller, ColorKind.FOLIAGE);
        water = pack(controller, ColorKind.WATER);
    }

    public static void clear() {
        grass = foliage = water = 0;
    }

    /** Opaque ARGB override for {@code kind}, or {@code 0} when the current level has none. */
    public static int get(ColorKind kind) {
        return switch (kind) {
            case GRASS -> grass;
            case FOLIAGE -> foliage;
            case WATER -> water;
            default -> 0;
        };
    }

    private static int pack(@Nullable AgeController controller, ColorKind kind) {
        if (controller == null) return 0;
        Colors.RGB c = controller.staticColor(kind);
        return c == null ? 0 : c.clamp().toARGB();
    }
}

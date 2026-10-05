package com.techbucketdivision.mystcraft.api.symbol;

import com.techbucketdivision.mystcraft.util.Colors;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Random;

/** Helpers replicating the original {@code ModifierUtils} semantics (see original spec §4.2). */
public final class ModifierUtils {
    private ModifierUtils() {}

    // --- numeric modifiers (with averaging semantics) ----------------------------------------------------------

    /** Sets or averages an angle on the circle. */
    public static void pushAngle(AgeDirector d, float degrees) {
        Modifier pending = d.popModifier(Modifier.ANGLE);
        float value = pending.isEmpty() ? degrees : averageAngles(pending.asFloat(degrees), degrees);
        d.setModifier(Modifier.ANGLE, new Modifier(value));
    }

    public static void pushPhase(AgeDirector d, float degrees) {
        Modifier pending = d.popModifier(Modifier.PHASE);
        float value = pending.isEmpty() ? degrees : averageAngles(pending.asFloat(degrees), degrees);
        d.setModifier(Modifier.PHASE, new Modifier(value));
    }

    /** Linear average for length/wavelength factors. */
    public static void pushFactor(AgeDirector d, float factor) {
        Modifier pending = d.popModifier(Modifier.FACTOR);
        float value = pending.isEmpty() ? factor : (pending.asFloat(factor) + factor) / 2f;
        d.setModifier(Modifier.FACTOR, new Modifier(value));
    }

    public static float averageAngles(float a, float b) {
        double ar = Math.toRadians(a), br = Math.toRadians(b);
        double x = Math.cos(ar) + Math.cos(br);
        double y = Math.sin(ar) + Math.sin(br);
        float r = (float) Math.toDegrees(Math.atan2(y, x));
        return Mth.positiveModulo(r, 360f);
    }

    public static @Nullable Float popAngle(AgeDirector d) {
        Modifier m = d.popModifier(Modifier.ANGLE);
        return m.isEmpty() ? null : m.asFloat(0);
    }

    public static @Nullable Float popPhase(AgeDirector d) {
        Modifier m = d.popModifier(Modifier.PHASE);
        return m.isEmpty() ? null : m.asFloat(0);
    }

    public static @Nullable Float popFactor(AgeDirector d) {
        Modifier m = d.popModifier(Modifier.FACTOR);
        return m.isEmpty() ? null : m.asFloat(1);
    }

    // --- colours -----------------------------------------------------------------------------------------------

    /** Sets or averages the pending colour. */
    public static void pushColor(AgeDirector d, Colors.RGB color) {
        Modifier pending = d.popModifier(Modifier.COLOR);
        Colors.RGB existing = pending.as(Colors.RGB.class);
        d.setModifier(Modifier.COLOR, new Modifier(existing == null ? color : existing.average(color)));
    }

    public static Colors.@Nullable RGB popColor(AgeDirector d) {
        return d.popModifier(Modifier.COLOR).as(Colors.RGB.class);
    }

    /** Appends the pending colour (interval = pending wavelength, default 1) to the pending gradient. */
    public static void pushGradient(AgeDirector d) {
        Colors.RGB color = popColor(d);
        Float factor = popFactor(d);
        ColorGradient gradient = d.popModifier(Modifier.GRADIENT).as(ColorGradient.class);
        if (gradient == null) gradient = new ColorGradient();
        if (color != null) gradient.pushColor(color, factor == null ? 1.0f : factor);
        d.setModifier(Modifier.GRADIENT, new Modifier(gradient));
    }

    /**
     * Pops the gradient; if absent builds one from the pending colour; if still empty uses the default colour.
     */
    public static ColorGradient popGradient(AgeDirector d, Colors.RGB fallback) {
        ColorGradient gradient = d.popModifier(Modifier.GRADIENT).as(ColorGradient.class);
        if (gradient == null) {
            gradient = new ColorGradient();
            Colors.RGB color = popColor(d);
            if (color != null) gradient.pushColor(color);
        }
        if (gradient.isEmpty()) gradient.pushColor(fallback);
        return gradient;
    }

    /** Pops the gradient without creating a fallback; may return {@code null}. */
    public static @Nullable ColorGradient popGradientOrNull(AgeDirector d) {
        ColorGradient gradient = d.popModifier(Modifier.GRADIENT).as(ColorGradient.class);
        if (gradient == null) {
            Colors.RGB color = popColor(d);
            if (color != null) {
                gradient = new ColorGradient();
                gradient.pushColor(color);
            }
        }
        return gradient;
    }

    public static @Nullable ColorGradient popSunset(AgeDirector d) {
        return d.popModifier(Modifier.SUNSET).as(ColorGradient.class);
    }

    // --- blocks & biomes ---------------------------------------------------------------------------------------

    public static BlockState popBlockState(AgeDirector d, BlockState fallback, BlockCategory... categories) {
        BlockDescriptor desc = d.popBlockMatching(categories);
        return desc == null ? fallback : desc.state();
    }

    public static BlockState popStructureBlock(AgeDirector d, BlockState fallback) {
        return popBlockState(d, fallback, BlockCategory.STRUCTURE);
    }

    /** Pops a biome or picks a random selectable biome (seeded). */
    public static Holder<Biome> popBiomeOrRandom(AgeDirector d, long seed) {
        Holder<Biome> biome = d.popBiome();
        return biome != null ? biome : randomBiome(d, seed);
    }

    public static Holder<Biome> randomBiome(AgeDirector d, long seed) {
        List<Holder<Biome>> all = com.techbucketdivision.mystcraft.symbol.BiomeSymbols.selectableBiomes(d.registries());
        if (all.isEmpty()) {
            return d.registries().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        }
        return all.get(new Random(seed).nextInt(all.size()));
    }
}

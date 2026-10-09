package com.tbd.mystcraft.instability;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.config.BalanceConfig;
import com.tbd.mystcraft.config.MystcraftConfig;
import com.tbd.mystcraft.registry.ModBlocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Baseline ("free") instability values per watched block (original spec §6.3). With {@code baselining.useconfigs}
 * the values come from the balance config; the generation mode (profiling a control Age) is not implemented yet and
 * currently also falls back to the config values.
 */
public final class BaselineProfiler {
    private BaselineProfiler() {}

    private static @Nullable Map<String, Integer> baseline;

    /** Whether a baseline exists. Chunk instability is 0 until it does. */
    public static boolean isConstructed() {
        return baseline != null;
    }

    public static int baseline(String key) {
        if (baseline == null) return 0;
        Integer v = baseline.get(key);
        return v == null ? 0 : v;
    }

    public static Map<String, Integer> all() {
        return baseline == null ? Map.of() : Collections.unmodifiableMap(baseline);
    }

    public static void setBaseline(Map<String, Integer> values) {
        baseline = new HashMap<>(values);
    }

    public static void clear() {
        baseline = null;
    }

    /** Called on server start: loads config values, or (TODO) kicks off profiling of a control Age. */
    public static void initialize(MinecraftServer server) {
        if (!MystcraftConfig.BASELINE_USE_CONFIGS.get()) {
            // TODO: generation mode — profile a hidden control Age (grid of every selectable biome, TerrainNormal,
            // surface water lakes, deep lava lakes, caves, ravines, villages, mineshafts) one chunk per
            // `baselining.tickrate.minimum` ticks until biomeCount * max(10, ceil(500 / biomeCount)) chunks were
            // profiled, then freevals = ceil(split * 1.05 / 100) * 100, and send ProfilingStatePayload toasts.
            Mystcraft.LOGGER.warn("Baseline profiling by generation is not implemented yet; using config values");
        }
        setBaseline(fromConfig());
        Mystcraft.LOGGER.info("Instability baseline loaded ({} entries)", all().size());
    }

    /** Baseline values from the balance config (deepslate ore variants share their ore's value). */
    public static Map<String, Integer> fromConfig() {
        Map<String, Integer> values = new HashMap<>();
        put(values, BalanceConfig.BASE_COAL.get(), Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
        put(values, BalanceConfig.BASE_DIAMOND.get(), Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
        put(values, BalanceConfig.BASE_EMERALD.get(), Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
        put(values, BalanceConfig.BASE_GOLD.get(), Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
        put(values, BalanceConfig.BASE_IRON.get(), Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
        put(values, BalanceConfig.BASE_LAPIS.get(), Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
        put(values, BalanceConfig.BASE_REDSTONE.get(), Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
        put(values, BalanceConfig.BASE_GLOWSTONE.get(), Blocks.GLOWSTONE);
        put(values, BalanceConfig.BASE_QUARTZ.get(), Blocks.NETHER_QUARTZ_ORE);
        put(values, BalanceConfig.BASE_CRYSTAL.get(), ModBlocks.CRYSTAL.get());
        return values;
    }

    private static void put(Map<String, Integer> values, int value, Block... blocks) {
        for (Block b : blocks) values.put(InstabilityBlocks.key(b), value);
    }
}

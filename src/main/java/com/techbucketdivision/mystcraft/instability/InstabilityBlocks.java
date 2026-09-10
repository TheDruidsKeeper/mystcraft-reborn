package com.techbucketdivision.mystcraft.instability;

import com.techbucketdivision.mystcraft.registry.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Blocks watched by the chunk profiler and their instability factors (REQUIREMENTS §6.2). Keys are block registry
 * ids ({@code minecraft:coal_ore}); every state of a watched block shares one key. Add-ons register more via
 * {@link #setFactors(Block, float, float)} before the first Age is profiled.
 */
public final class InstabilityBlocks {
    private InstabilityBlocks() {}

    private static final Map<String, Float> FACTOR1 = new LinkedHashMap<>();
    private static final Map<String, Float> FACTOR2 = new LinkedHashMap<>();
    private static final Map<Block, String> KEYS = new java.util.HashMap<>();

    /** @param factor1 accessibility-weighted factor; @param factor2 flat factor */
    public static synchronized void setFactors(Block block, float factor1, float factor2) {
        String key = key(block);
        KEYS.put(block, key);
        FACTOR1.put(key, factor1);
        FACTOR2.put(key, factor2);
    }

    public static synchronized void setFactors(String key, float factor1, float factor2) {
        FACTOR1.put(key, factor1);
        FACTOR2.put(key, factor2);
    }

    public static String key(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id.toString();
    }

    /** Watched-block key for a state, or {@code null} if the block is not watched. */
    public static @Nullable String keyFor(BlockState state) {
        return KEYS.get(state.getBlock());
    }

    public static Collection<String> watchedKeys() {
        return Collections.unmodifiableCollection(FACTOR1.keySet());
    }

    public static float factor1(String key) {
        return FACTOR1.getOrDefault(key, 0f);
    }

    public static float factor2(String key) {
        return FACTOR2.getOrDefault(key, 0f);
    }

    /** Defaults from the original {@code InstabilityData.initialize} (deepslate variants share their ore's factors). */
    public static void registerDefaults() {
        setFactors(Blocks.COAL_ORE, 5, 1);
        setFactors(Blocks.DEEPSLATE_COAL_ORE, 5, 1);
        setFactors(Blocks.LAPIS_ORE, 5, 1);
        setFactors(Blocks.DEEPSLATE_LAPIS_ORE, 5, 1);
        setFactors(Blocks.IRON_ORE, 60, 1);
        setFactors(Blocks.DEEPSLATE_IRON_ORE, 60, 1);
        setFactors(Blocks.EMERALD_ORE, 200, 2);
        setFactors(Blocks.DEEPSLATE_EMERALD_ORE, 200, 2);
        setFactors(Blocks.REDSTONE_ORE, 250, 2);
        setFactors(Blocks.DEEPSLATE_REDSTONE_ORE, 250, 2);
        setFactors(Blocks.GOLD_ORE, 750, 4);
        setFactors(Blocks.DEEPSLATE_GOLD_ORE, 750, 4);
        setFactors(Blocks.DIAMOND_ORE, 4000, 20);
        setFactors(Blocks.DEEPSLATE_DIAMOND_ORE, 4000, 20);
        setFactors(ModBlocks.CRYSTAL.get(), 20, 4);
        setFactors(Blocks.GLOWSTONE, 50, 4);
        setFactors(Blocks.NETHER_QUARTZ_ORE, 20, 4);
        // Fluids (§4.3.14): package A's FluidSymbols registers per-fluid factors via setFactors(Block, ...).
    }
}

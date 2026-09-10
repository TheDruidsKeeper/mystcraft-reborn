package com.techbucketdivision.mystcraft.world.biome;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Classic (1.12) biome base height / height variation table used by the legacy terrain shaping
 * (plains 0.125/0.05, ocean −1.0/0.1, mountains 1.0/0.5 …). Keyed by vanilla biome id; unknown biomes fall back to
 * biome tags and finally to 0.1 / 0.2.
 */
public final class BiomeHeights {
    private BiomeHeights() {}

    private record Entry(float base, float variation) {}

    private static final Entry DEFAULT = new Entry(0.1F, 0.2F);
    private static final Entry OCEAN = new Entry(-1.0F, 0.1F);
    private static final Entry DEEP_OCEAN = new Entry(-1.8F, 0.1F);
    private static final Entry RIVER = new Entry(-0.5F, 0.0F);
    private static final Entry BEACH = new Entry(0.0F, 0.025F);
    private static final Entry FLAT = new Entry(0.125F, 0.05F);
    private static final Entry HILLS = new Entry(0.45F, 0.3F);
    private static final Entry MOUNTAINS = new Entry(1.0F, 0.5F);
    private static final Entry PLATEAU = new Entry(1.5F, 0.025F);
    private static final Entry PEAKS = new Entry(1.5F, 0.5F);
    private static final Entry SWAMP = new Entry(-0.2F, 0.1F);

    private static final Map<String, Entry> TABLE = new HashMap<>();

    static {
        put(OCEAN, "ocean", "frozen_ocean", "warm_ocean", "lukewarm_ocean", "cold_ocean");
        put(DEEP_OCEAN, "deep_ocean", "deep_frozen_ocean", "deep_lukewarm_ocean", "deep_cold_ocean");
        put(RIVER, "river", "frozen_river");
        put(BEACH, "beach", "snowy_beach");
        put(new Entry(0.1F, 0.8F), "stony_shore");
        put(FLAT, "plains", "sunflower_plains", "desert", "snowy_plains", "savanna");
        put(SWAMP, "swamp", "mangrove_swamp");
        put(DEFAULT, "forest", "birch_forest", "dark_forest", "jungle", "sparse_jungle", "bamboo_jungle", "badlands",
                "eroded_badlands", "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas",
                "the_end", "end_highlands", "end_midlands", "end_barrens", "small_end_islands", "the_void", "pale_garden",
                "lush_caves", "dripstone_caves", "deep_dark");
        put(new Entry(0.2F, 0.2F), "taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga");
        put(new Entry(0.2F, 0.3F), "mushroom_fields");
        put(new Entry(0.1F, 0.4F), "flower_forest");
        put(new Entry(0.2F, 0.4F), "old_growth_birch_forest");
        put(new Entry(0.425F, 0.45F), "ice_spikes");
        put(HILLS, "meadow", "cherry_grove");
        put(new Entry(0.6F, 0.4F), "grove");
        put(new Entry(0.8F, 0.5F), "snowy_slopes");
        put(MOUNTAINS, "windswept_hills", "windswept_forest", "windswept_gravelly_hills");
        put(new Entry(0.3625F, 1.225F), "windswept_savanna");
        put(PLATEAU, "savanna_plateau", "wooded_badlands");
        put(PEAKS, "jagged_peaks", "frozen_peaks", "stony_peaks");
    }

    private static void put(Entry entry, String... paths) {
        for (String path : paths) TABLE.put("minecraft:" + path, entry);
    }

    private static Entry lookup(Holder<Biome> biome) {
        Optional<ResourceKey<Biome>> key = biome.unwrapKey();
        if (key.isPresent()) {
            Entry e = TABLE.get(key.get().identifier().toString());
            if (e != null) return e;
        }
        // Tag fallbacks for modded / unknown biomes. Tag constants are stable since 1.19.
        try {
            if (biome.is(BiomeTags.IS_DEEP_OCEAN)) return DEEP_OCEAN;   // UNVERIFIED: BiomeTags.IS_DEEP_OCEAN constant name
            if (biome.is(BiomeTags.IS_OCEAN)) return OCEAN;             // UNVERIFIED: BiomeTags.IS_OCEAN
            if (biome.is(BiomeTags.IS_RIVER)) return RIVER;             // UNVERIFIED: BiomeTags.IS_RIVER
            if (biome.is(BiomeTags.IS_BEACH)) return BEACH;             // UNVERIFIED: BiomeTags.IS_BEACH
            if (biome.is(BiomeTags.IS_MOUNTAIN)) return MOUNTAINS;      // UNVERIFIED: BiomeTags.IS_MOUNTAIN
            if (biome.is(BiomeTags.IS_HILL)) return HILLS;              // UNVERIFIED: BiomeTags.IS_HILL
        } catch (RuntimeException ignored) {
            // unbound holder (e.g. during datagen) — fall through
        }
        return DEFAULT;
    }

    /** Classic base height (plains = 0.125, ocean = −1.0). */
    public static float baseHeight(Holder<Biome> biome) {
        return lookup(biome).base();
    }

    /** Classic height variation (plains = 0.05, mountains = 0.5). */
    public static float heightVariation(Holder<Biome> biome) {
        return lookup(biome).variation();
    }

    /** Ground level a flat Age would use for this biome: {@code base * 64 + 64}. */
    public static int groundLevel(Holder<Biome> biome) {
        return Math.round(baseHeight(biome) * 64F + 64F);
    }
}

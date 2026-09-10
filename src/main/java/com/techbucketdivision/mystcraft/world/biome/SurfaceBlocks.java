package com.techbucketdivision.mystcraft.world.biome;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Simple biome-keyed top/filler block table (vanilla surface rules are not available without noise settings).
 * grass/dirt by default; sand for beaches, deserts and ocean floors; snow for cold biomes; netherrack for the Nether;
 * end stone for the End; mycelium for mushroom fields.
 */
public final class SurfaceBlocks {
    private SurfaceBlocks() {}

    /** Top / filler pair. */
    public record Pair(BlockState top, BlockState filler) {}

    private static final Pair GRASS = new Pair(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
    private static final Pair SAND = new Pair(Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState());
    private static final Pair RED_SAND = new Pair(Blocks.RED_SAND.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState());
    private static final Pair GRAVEL = new Pair(Blocks.GRAVEL.defaultBlockState(), Blocks.GRAVEL.defaultBlockState());
    private static final Pair SNOW = new Pair(Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
    private static final Pair PODZOL = new Pair(Blocks.PODZOL.defaultBlockState(), Blocks.DIRT.defaultBlockState());
    private static final Pair MYCELIUM = new Pair(Blocks.MYCELIUM.defaultBlockState(), Blocks.DIRT.defaultBlockState());
    private static final Pair NETHER = new Pair(Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState());
    private static final Pair SOUL = new Pair(Blocks.SOUL_SAND.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState());
    private static final Pair END = new Pair(Blocks.END_STONE.defaultBlockState(), Blocks.END_STONE.defaultBlockState());
    private static final Pair STONE = new Pair(Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState());

    private static String path(Holder<Biome> biome) {
        Optional<ResourceKey<Biome>> key = biome.unwrapKey();
        return key.map(k -> k.identifier().getNamespace().equals("minecraft") ? k.identifier().getPath() : k.identifier().toString()).orElse("");
    }

    public static Pair of(Holder<Biome> biome) {
        String p = path(biome);
        switch (p) {
            case "desert", "beach", "snowy_beach", "ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean", "cold_ocean",
                 "deep_lukewarm_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean" -> {
                return SAND;
            }
            case "badlands", "eroded_badlands", "wooded_badlands" -> {
                return RED_SAND;
            }
            case "stony_shore", "windswept_gravelly_hills", "stony_peaks", "jagged_peaks", "frozen_peaks" -> {
                return p.equals("windswept_gravelly_hills") ? GRAVEL : STONE;
            }
            case "snowy_plains", "ice_spikes", "snowy_slopes", "grove", "snowy_taiga" -> {
                return SNOW;
            }
            case "old_growth_pine_taiga", "old_growth_spruce_taiga" -> {
                return PODZOL;
            }
            case "mushroom_fields" -> {
                return MYCELIUM;
            }
            case "nether_wastes", "crimson_forest", "warped_forest", "basalt_deltas" -> {
                return NETHER;
            }
            case "soul_sand_valley" -> {
                return SOUL;
            }
            case "the_end", "end_highlands", "end_midlands", "end_barrens", "small_end_islands" -> {
                return END;
            }
            default -> {
                try {
                    if (biome.is(BiomeTags.IS_NETHER)) return NETHER;
                    if (biome.is(BiomeTags.IS_END)) return END;       // UNVERIFIED: BiomeTags.IS_END constant name
                    if (biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_OCEAN)) return SAND; // UNVERIFIED: tag names
                } catch (RuntimeException ignored) {
                    // unbound holder
                }
                return isCold(biome) ? SNOW : GRASS;
            }
        }
    }

    /** Cold enough for a snow surface (classic threshold: temperature ≤ 0.15). */
    public static boolean isCold(Holder<Biome> biome) {
        try {
            return biome.value().getBaseTemperature() <= 0.15F;
        } catch (RuntimeException e) {
            return false;
        }
    }
}

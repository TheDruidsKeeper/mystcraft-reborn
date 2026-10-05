package com.tbd.mystcraft.world.biome;

import com.tbd.mystcraft.api.symbol.logic.BiomeController;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.List;

/**
 * "Native Biome Distribution" (original spec §4.3.5): wraps the vanilla overworld multi-noise biome source seeded with
 * the Age seed. 3D (cave) biomes are honoured through {@link #getNoiseBiome}; the 2D query samples at y = 64.
 */
public final class NativeBiomeController implements BiomeController {
    private static final int SURFACE_QUART_Y = 64 >> 2;

    private final MultiNoiseBiomeSource source;
    private final Climate.Sampler sampler;
    private final List<Holder<Biome>> possible;

    public NativeBiomeController(long seed, HolderLookup.Provider registries) {
        Holder<MultiNoiseBiomeSourceParameterList> preset = registries
                .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        this.source = MultiNoiseBiomeSource.createFromPreset(preset);
        RandomState randomState = RandomState.create(registries, NoiseGeneratorSettings.OVERWORLD, seed);
        this.sampler = randomState.sampler();
        this.possible = new ArrayList<>(source.possibleBiomes());
    }

    @Override
    public Holder<Biome> getBiomeAt(int blockX, int blockZ) {
        return source.getNoiseBiome(blockX >> 2, SURFACE_QUART_Y, blockZ >> 2, sampler);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        return source.getNoiseBiome(quartX, quartY, quartZ, sampler);
    }

    @Override
    public List<Holder<Biome>> possibleBiomes() {
        return possible;
    }
}

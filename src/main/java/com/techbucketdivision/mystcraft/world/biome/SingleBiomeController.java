package com.techbucketdivision.mystcraft.world.biome;

import com.techbucketdivision.mystcraft.api.symbol.logic.BiomeController;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/** "Single" biome distribution (original spec §4.3.5): the whole Age is one biome. */
public final class SingleBiomeController implements BiomeController {
    private final Holder<Biome> biome;
    private final List<Holder<Biome>> possible;

    public SingleBiomeController(Holder<Biome> biome) {
        this.biome = biome;
        this.possible = List.of(biome);
    }

    public Holder<Biome> biome() {
        return biome;
    }

    @Override
    public Holder<Biome> getBiomeAt(int blockX, int blockZ) {
        return biome;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        return biome;
    }

    @Override
    public List<Holder<Biome>> possibleBiomes() {
        return possible;
    }
}

package com.tbd.mystcraft.world.biome;

import com.tbd.mystcraft.api.symbol.logic.BiomeController;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/**
 * "Tiled" / "Grid-form" biome distribution (original spec §4.3.5): biome at (x,z) = {@code list[((x>>4)+(z>>4)) mod n]},
 * i.e. 16-block diagonal stripes / checkerboard.
 *
 * <p>The original Grid variant sampled the terrain-shaping biome array at generation scale (×4). Modern chunks store
 * biomes at 4-block resolution only, so {@code gridScale = true} enlarges the tiles to 64 blocks (the generation-scale
 * grid) while {@code false} keeps the classic 16-block tiles.
 */
public final class TiledBiomeController implements BiomeController {
    private final List<Holder<Biome>> biomes;
    private final boolean gridScale;

    public TiledBiomeController(List<Holder<Biome>> biomes, boolean gridScale) {
        if (biomes.isEmpty()) throw new IllegalArgumentException("TiledBiomeController needs at least one biome");
        this.biomes = List.copyOf(biomes);
        this.gridScale = gridScale;
    }

    public boolean gridScale() {
        return gridScale;
    }

    @Override
    public Holder<Biome> getBiomeAt(int blockX, int blockZ) {
        int shift = gridScale ? 6 : 4;
        int index = ((blockX >> shift) + (blockZ >> shift)) % biomes.size();
        if (index < 0) index += biomes.size();
        return biomes.get(index);
    }

    @Override
    public List<Holder<Biome>> possibleBiomes() {
        return biomes;
    }
}

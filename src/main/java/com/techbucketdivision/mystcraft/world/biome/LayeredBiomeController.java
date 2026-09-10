package com.techbucketdivision.mystcraft.world.biome;

import com.techbucketdivision.mystcraft.api.symbol.logic.BiomeController;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Tiny / Small / Medium / Large / Huge" biome distributions (REQUIREMENTS §4.3.5): a GenLayer-style stack
 * (random biome → zoom×2 → zoom×{@code zoom} → smooth → Voronoi) over the Age's biome list. Results are cached per
 * 16×16 tile (the original used a {@code BiomeCache}); the cache is bounded and safe to use from several threads.
 */
public final class LayeredBiomeController implements BiomeController {
    private static final int CACHE_TILES = 4096;

    private final List<Holder<Biome>> biomes;
    private final int zoom;
    private final LegacyLayers.Layer layer;
    private final Map<Long, int[]> cache = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
            return size() > CACHE_TILES;
        }
    };

    public LayeredBiomeController(long seed, int zoom, List<Holder<Biome>> biomes) {
        if (biomes.isEmpty()) throw new IllegalArgumentException("LayeredBiomeController needs at least one biome");
        this.biomes = List.copyOf(biomes);
        this.zoom = Math.max(0, zoom);
        this.layer = LegacyLayers.build(seed, this.zoom, this.biomes.size());
    }

    public int zoom() {
        return zoom;
    }

    private int[] tile(int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        synchronized (cache) {
            int[] t = cache.get(key);
            if (t != null) return t;
        }
        int[] t = layer.getInts(chunkX << 4, chunkZ << 4, 16, 16);
        synchronized (cache) {
            cache.putIfAbsent(key, t);
        }
        return t;
    }

    @Override
    public Holder<Biome> getBiomeAt(int blockX, int blockZ) {
        int[] t = tile(blockX >> 4, blockZ >> 4);
        int idx = t[(blockX & 15) + (blockZ & 15) * 16];
        if (idx < 0 || idx >= biomes.size()) idx = 0;
        return biomes.get(idx);
    }

    @Override
    public List<Holder<Biome>> possibleBiomes() {
        return biomes;
    }

    /** Debug helper: number of cached tiles. */
    public int cachedTiles() {
        synchronized (cache) {
            return cache.size();
        }
    }
}

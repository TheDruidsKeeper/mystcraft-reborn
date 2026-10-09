package com.tbd.mystcraft.world.feature;

import com.tbd.mystcraft.api.symbol.logic.ChunkFinalizer;
import com.tbd.mystcraft.api.symbol.logic.TerrainAlteration;
import com.tbd.mystcraft.api.symbol.logic.TerrainContext;
import com.tbd.mystcraft.world.biome.SurfaceBlocks;
import com.tbd.mystcraft.world.gen.ChunkBlocks;
import com.tbd.mystcraft.world.gen.LegacyNoise;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Floating Islands" (original spec §4.3.7): 1/192 per chunk (range 5) a blob at {@code y = 150 + rand(rand(50)+50)}
 * (scalar 12, squash 0.2) plus 40–51 sub-blobs (scale 1–4, squash 0.4) within ±20/±10/±20, built from the structure
 * block; the island surface gets the island biome's top/filler blocks and the touched columns are re-biomed to the
 * island biome when the chunk is finalized.
 */
public final class FloatingIslandsAlteration extends AbstractTunnelGen implements TerrainAlteration, ChunkFinalizer {
    private static final int RATE = 192;

    private final Holder<Biome> biome;
    private final LegacyNoise stoneNoise;
    private final Map<Long, boolean[]> modifiedColumns = new ConcurrentHashMap<>();

    public FloatingIslandsAlteration(long seed, Holder<Biome> biome, BlockState state) {
        super(seed, state, 5);
        this.biome = biome;
        this.stoneNoise = new LegacyNoise(new Random(seed), 4);
    }

    public Holder<Biome> islandBiome() {
        return biome;
    }

    @Override
    public void alterTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {
        generate(new ChunkBlocks(chunk), chunkX, chunkZ);
    }

    @Override
    protected void recursiveGenerate(Random rand, int x, int z, int chunkX, int chunkZ, ChunkBlocks blocks) {
        if (rand.nextInt(RATE) != 0) return;
        boolean[] modified = new boolean[256];
        double dx = x * 16 + rand.nextInt(16);
        double dy = rand.nextInt(rand.nextInt(50) + 50) + 150;
        double dz = z * 16 + rand.nextInt(16);
        generateNode(rand.nextLong(), chunkX, chunkZ, blocks, modified, dx, dy, dz, 12F, 0.0F, 0.0F, -1, -1, 0.2D, Shape.BLOB, 1.0F);
        int subelements = rand.nextInt(12) + 40;
        for (int i = 0; i < subelements; ++i) {
            double subx = dx + (rand.nextDouble() - rand.nextDouble()) * 20.0D;
            double suby = dy + (rand.nextDouble() - rand.nextDouble()) * 10.0D;
            double subz = dz + (rand.nextDouble() - rand.nextDouble()) * 20.0D;
            float scale = rand.nextFloat() * 3F + 1F;
            generateNode(rand.nextLong(), chunkX, chunkZ, blocks, modified, subx, suby, subz, scale, 0.0F, 0.0F, -1, -1, 0.4D, Shape.BLOB, 1.0F);
        }
        boolean any = false;
        for (boolean b : modified) {
            if (b) {
                any = true;
                break;
            }
        }
        if (!any) return;
        replaceSurface(rand, chunkX, chunkZ, blocks, modified);
        modifiedColumns.merge(ChunkPos.pack(chunkX, chunkZ), modified, (prev, next) -> {
            for (int i = 0; i < prev.length; i++) next[i] |= prev[i];
            return next;
        });
    }

    private void replaceSurface(Random rand, int chunkX, int chunkZ, ChunkBlocks blocks, boolean[] modified) {
        double noisefactor = 0.03125D;
        double[] noise = stoneNoise.generate(null, chunkX * 16, chunkZ * 16, 0, 16, 16, 1, noisefactor * 2D, noisefactor * 2D, noisefactor * 2D);
        SurfaceBlocks.Pair surface = SurfaceBlocks.of(biome);
        BlockState island = state == null ? Blocks.STONE.defaultBlockState() : state;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (!modified[x | z << 4]) continue;
                int depth = (int) (noise[z + x * 16] / 3D + 3D + rand.nextDouble() * 0.25D);
                int counter = -1;
                BlockState filler = surface.filler();
                for (int y = LAYERS - 1; y >= 1; --y) {
                    BlockState current = blocks.get(x, y, z);
                    if (current.isAir()) {
                        // a gap below a blob starts a new "surface" (sub-blobs stacked with air between)
                        if (counter != -1) counter = -1;
                        continue;
                    }
                    if (current != island) continue;
                    if (counter == -1) {
                        counter = depth;
                        blocks.set(x, y, z, surface.top());
                        continue;
                    }
                    if (counter <= 0) continue;
                    --counter;
                    blocks.set(x, y, z, filler);
                    if (counter == 0 && filler.is(Blocks.SAND)) {
                        counter = rand.nextInt(4);
                        filler = Blocks.SANDSTONE.defaultBlockState();
                    }
                }
            }
        }
    }

    @Override
    public void finalizeChunk(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {
        boolean[] modified = modifiedColumns.remove(ChunkPos.pack(chunkX, chunkZ));
        if (modified == null) return;
        // Snapshot the current biomes, then rewrite with the island biome in the touched columns.
        int qMinX = chunkX << 2;
        int qMinZ = chunkZ << 2;
        int qMinY = chunk.getMinY() >> 2;
        int qHeight = chunk.getHeight() >> 2;
        @SuppressWarnings("unchecked")
        Holder<Biome>[] snapshot = (Holder<Biome>[]) new Holder[4 * 4 * qHeight];
        for (int qy = 0; qy < qHeight; qy++) {
            for (int qz = 0; qz < 4; qz++) {
                for (int qx = 0; qx < 4; qx++) {
                    snapshot[(qy * 4 + qz) * 4 + qx] = chunk.getNoiseBiome(qMinX + qx, qMinY + qy, qMinZ + qz);
                }
            }
        }
        boolean[] quartModified = new boolean[16];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (modified[x | z << 4]) quartModified[(x >> 2) | (z >> 2) << 2] = true;
            }
        }
        BiomeResolver resolver = (x, y, z, sampler) -> { // BiomeResolver#getNoiseBiome(int, int, int, Climate.Sampler)
            int lx = x - qMinX;
            int lz = z - qMinZ;
            int ly = y - qMinY;
            if (lx < 0 || lx > 3 || lz < 0 || lz > 3 || ly < 0 || ly >= qHeight) return biome;
            if (quartModified[lx | lz << 2]) return biome;
            return snapshot[(ly * 4 + lz) * 4 + lx];
        };
        chunk.fillBiomesFromNoise(resolver, Climate.empty());
    }
}

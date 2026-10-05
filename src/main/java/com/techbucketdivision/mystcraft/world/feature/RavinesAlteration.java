package com.tbd.mystcraft.world.feature;

import com.tbd.mystcraft.api.symbol.logic.TerrainAlteration;
import com.tbd.mystcraft.api.symbol.logic.TerrainContext;
import com.tbd.mystcraft.world.gen.ChunkBlocks;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Random;

/**
 * "Ravines" (original spec §4.3.8): 1/50 per chunk (range 8), {@code y = rand(rand(40)+8)+20}, classic ravine shape
 * carving air, skipped where water would be breached. Port of {@code MapGenRavineMyst}.
 */
public final class RavinesAlteration extends AbstractMapGen implements TerrainAlteration {

    public RavinesAlteration(long seed) {
        super(seed, Blocks.AIR.defaultBlockState(), 8);
    }

    @Override
    public void alterTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {
        generate(new ChunkBlocks(chunk), chunkX, chunkZ);
    }

    @Override
    protected void recursiveGenerate(Random rand, int x, int z, int chunkX, int chunkZ, ChunkBlocks blocks) {
        if (rand.nextInt(50) != 0) return;
        double px = (x * 16 + rand.nextInt(16));
        double py = (rand.nextInt(rand.nextInt(40) + 8) + 20);
        double pz = (z * 16 + rand.nextInt(16));
        float yaw = rand.nextFloat() * (float) Math.PI * 2.0F;
        float pitch = (rand.nextFloat() - 0.5F) * 2.0F / 8.0F;
        float scalar = (rand.nextFloat() * 2.0F + rand.nextFloat()) * 2.0F;
        generateRavine(rand.nextLong(), chunkX, chunkZ, blocks, px, py, pz, scalar, yaw, pitch, 0, 0, 3.0D);
    }

    private void generateRavine(long nodeSeed, int chunkX, int chunkZ, ChunkBlocks blocks, double baseX, double baseY, double baseZ,
                                float scalar, float yaw, float pitch, int loopc, int maxLoops, double squash) {
        Random rand = new Random(nodeSeed);
        double chunkXmid = (chunkX * 16 + 8);
        double chunkZmid = (chunkZ * 16 + 8);
        float var24 = 0.0F;
        float var25 = 0.0F;
        if (maxLoops <= 0) {
            int var26 = this.range * 16 - 16;
            maxLoops = var26 - rand.nextInt(var26 / 4);
        }
        boolean single = false;
        if (loopc == -1) {
            loopc = maxLoops / 2;
            single = true;
        }
        float[] widths = new float[1024];
        float var27 = 1.0F;
        for (int var28 = 0; var28 < 128; ++var28) {
            if (var28 == 0 || rand.nextInt(3) == 0) {
                var27 = 1.0F + rand.nextFloat() * rand.nextFloat() * 1.0F;
            }
            widths[var28] = var27 * var27;
        }
        for (; loopc < maxLoops; ++loopc) {
            double var53 = 1.5D + (Mth.sin(loopc * (float) Math.PI / maxLoops) * scalar * 1.0F);
            double var30 = var53 * squash;
            var53 *= rand.nextFloat() * 0.25D + 0.75D;
            var30 *= rand.nextFloat() * 0.25D + 0.75D;
            float var32 = Mth.cos(pitch);
            float var33 = Mth.sin(pitch);
            baseX += (Mth.cos(yaw) * var32);
            baseY += var33;
            baseZ += (Mth.sin(yaw) * var32);
            pitch *= 0.7F;
            pitch += var25 * 0.05F;
            yaw += var24 * 0.05F;
            var25 *= 0.8F;
            var24 *= 0.5F;
            var25 += (rand.nextFloat() - rand.nextFloat()) * rand.nextFloat() * 2.0F;
            var24 += (rand.nextFloat() - rand.nextFloat()) * rand.nextFloat() * 4.0F;
            if (!single && rand.nextInt(4) == 0) continue;

            double var34 = baseX - chunkXmid;
            double var36 = baseZ - chunkZmid;
            double var38 = (maxLoops - loopc);
            double var40 = (scalar + 2.0F + 16.0F);
            if (var34 * var34 + var36 * var36 - var38 * var38 > var40 * var40) return;
            if (!(baseX >= chunkXmid - 16.0D - var53 * 2.0D && baseZ >= chunkZmid - 16.0D - var53 * 2.0D
                    && baseX <= chunkXmid + 16.0D + var53 * 2.0D && baseZ <= chunkZmid + 16.0D + var53 * 2.0D)) continue;

            int minX = Mth.floor(baseX - var53) - chunkX * 16 - 1;
            int maxX = Mth.floor(baseX + var53) - chunkX * 16 + 1;
            int minY = Mth.floor(baseY - var30) - 1;
            int maxY = Mth.floor(baseY + var30) + 1;
            int minZ = Mth.floor(baseZ - var53) - chunkZ * 16 - 1;
            int maxZ = Mth.floor(baseZ + var53) - chunkZ * 16 + 1;
            if (minX < 0) minX = 0;
            if (maxX > 16) maxX = 16;
            if (minY < 1) minY = 1;
            if (maxY > LAYERS) maxY = LAYERS;
            if (minZ < 0) minZ = 0;
            if (maxZ > 16) maxZ = 16;

            boolean foundWater = false;
            for (int localY = maxY + 1; !foundWater && localY >= minY - 1; --localY) {
                for (int localZ = minZ; !foundWater && localZ < maxZ; ++localZ) {
                    for (int localX = minX; !foundWater && localX < maxX; ++localX) {
                        if (localY >= 0 && localY < LAYERS) {
                            BlockState s = blocks.get(localX, localY, localZ);
                            if (s.is(Blocks.WATER)) foundWater = true;
                            if (localY != minY - 1 && localX != minX && localX != maxX - 1 && localZ != minZ && localZ != maxZ - 1) {
                                localY = minY;
                            }
                        }
                    }
                }
            }
            if (foundWater) continue;

            // NOTE: the original iterated y in [0, minY) (a porting bug that carved a hidden chasm under the tube).
            // original spec asks for the vanilla ravine shape, so the tube itself [minY, maxY) is carved here.
            for (int localY = minY; localY < maxY; ++localY) {
                double yfactor = (localY + 0.5D - baseY) / var30;
                double yfactorSq = yfactor * yfactor;
                for (int localZ = minZ; localZ < maxZ; ++localZ) {
                    double zfactor = ((localZ + chunkZ * 16) + 0.5D - baseZ) / var53;
                    double zfactorSq = zfactor * zfactor;
                    for (int localX = minX; localX < maxX; ++localX) {
                        double xfactor = ((localX + chunkX * 16) + 0.5D - baseX) / var53;
                        double xfactorSq = xfactor * xfactor;
                        if (xfactorSq + zfactorSq < 1.0D) {
                            if ((xfactorSq + zfactorSq) * widths[localY & 127] + yfactorSq / 6.0D < 1.0D) {
                                placeBlock(blocks, localX, localY, localZ);
                            }
                        }
                    }
                }
            }
            if (single) break;
        }
    }
}

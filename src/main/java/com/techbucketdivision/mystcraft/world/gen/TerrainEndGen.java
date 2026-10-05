package com.techbucketdivision.mystcraft.world.gen;

import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainContext;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * "Island World" terrain (original spec §4.3.6): End-style noise with a central island
 * ({@code distFactor = clamp(100 - dist*4, -100, 80)}), density crushed above mid-height and below y=8, no bedrock,
 * nothing below y=0. The symbol defaults the sea block to air and sets horizon 0 / sea level 49.
 */
public final class TerrainEndGen extends AbstractLegacyTerrainGen {
    private final LegacyNoise noiseGen1;
    private final LegacyNoise noiseGen2;
    private final LegacyNoise noiseGen3;
    private final LegacyNoise noiseGen4;
    private final LegacyNoise noiseGen5;

    public TerrainEndGen(long seed, BlockState terrain, BlockState sea) {
        super(seed, terrain, sea, false, false);
        Random rand = new Random(seed);
        noiseGen1 = new LegacyNoise(rand, 16);
        noiseGen2 = new LegacyNoise(rand, 16);
        noiseGen3 = new LegacyNoise(rand, 8);
        noiseGen4 = new LegacyNoise(rand, 10);
        noiseGen5 = new LegacyNoise(rand, 16);
    }

    @Override
    protected double[] initializeNoiseField(TerrainContext ctx, double[] field, int subchunkX, int subchunkY, int subchunkZ,
                                            int sizeX, int sizeY, int sizeZ) {
        if (field == null) field = new double[sizeX * sizeY * sizeZ];
        double var8 = 684.412D;
        double var10 = 684.412D;
        double[] noiseData4 = noiseGen4.generate2d(null, subchunkX, subchunkZ, sizeX, sizeZ, 1.121D, 1.121D);
        // noiseData5 was computed by the original but its contribution is forced to zero below; skipped.
        var8 *= 2.0D;
        double[] noiseData1 = noiseGen3.generate(null, subchunkX, subchunkY, subchunkZ, sizeX, sizeY, sizeZ, var8 / 80.0D, var10 / 160.0D, var8 / 80.0D);
        double[] noiseData2 = noiseGen1.generate(null, subchunkX, subchunkY, subchunkZ, sizeX, sizeY, sizeZ, var8, var10, var8);
        double[] noiseData3 = noiseGen2.generate(null, subchunkX, subchunkY, subchunkZ, sizeX, sizeY, sizeZ, var8, var10, var8);
        int noiseIndex = 0;
        int noise5Index = 0;
        for (int x = 0; x < sizeX; ++x) {
            for (int z = 0; z < sizeZ; ++z) {
                double noise4 = (noiseData4[noise5Index] + 256.0D) / 512.0D;
                if (noise4 > 1.0D) noise4 = 1.0D;
                float xCoord = (x + subchunkX);
                float zCoord = (z + subchunkZ);
                float distFactor = 100.0F - (float) Math.sqrt(xCoord * xCoord + zCoord * zCoord) * 4.0F;
                if (distFactor > 80.0F) distFactor = 80.0F;
                if (distFactor < -100.0F) distFactor = -100.0F;
                if (noise4 < 0.0D) noise4 = 0.0D;
                noise4 += 0.5D;
                ++noise5Index;
                for (int y = 0; y < sizeY; ++y) {
                    double density;
                    double noise2 = noiseData2[noiseIndex] / 512.0D;
                    double noise3 = noiseData3[noiseIndex] / 512.0D;
                    double noise1 = (noiseData1[noiseIndex] / 10.0D + 1.0D) / 2.0D;
                    if (noise1 < 0.0D) {
                        density = noise2;
                    } else if (noise1 > 1.0D) {
                        density = noise3;
                    } else {
                        density = noise2 + (noise3 - noise2) * noise1;
                    }
                    density -= 8.0D;
                    density += distFactor;
                    int value = 2;
                    double factor;
                    if (y > sizeY / 2 - value) {
                        factor = ((y - (sizeY / 2 - value)) / 64.0F);
                        if (factor < 0.0D) factor = 0.0D;
                        if (factor > 1.0D) factor = 1.0D;
                        density = density * (1.0D - factor) + -3000.0D * factor;
                    }
                    value = 8;
                    if (y < value) {
                        factor = ((value - y) / (value - 1.0F));
                        density = density * (1.0D - factor) + -30.0D * factor;
                    }
                    field[noiseIndex] = density;
                    ++noiseIndex;
                }
            }
        }
        return field;
    }
}

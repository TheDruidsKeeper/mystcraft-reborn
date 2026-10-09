package com.tbd.mystcraft.world.gen;

import com.tbd.mystcraft.api.symbol.logic.TerrainContext;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * "Cave World" terrain (original spec §4.3.6): nether-style noise (684.412 / 2053.236 scales, cos-shaped vertical
 * density, closed top and bottom). The symbol sets cloud height 200, horizon 128 and sea level 32.
 */
public final class TerrainNetherGen extends AbstractLegacyTerrainGen {
    private final LegacyNoise noiseGen1;
    private final LegacyNoise noiseGen2;
    private final LegacyNoise noiseGen3;
    private final LegacyNoise noiseGen4;
    private final LegacyNoise noiseGen5;

    public TerrainNetherGen(long seed, BlockState terrain, BlockState sea) {
        super(seed, terrain, sea, true, true);
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
        double cfactor1 = 684.412D;
        double cfactor2 = 2053.236D;
        double[] noiseData5 = noiseGen5.generate(null, subchunkX, subchunkY, subchunkZ, sizeX, 1, sizeZ, 100D, 0.0D, 100D);
        double[] noiseData3 = noiseGen3.generate(null, subchunkX, subchunkY, subchunkZ, sizeX, sizeY, sizeZ, cfactor1 / 80D, cfactor2 / 60D, cfactor1 / 80D);
        double[] noiseData1 = noiseGen1.generate(null, subchunkX, subchunkY, subchunkZ, sizeX, sizeY, sizeZ, cfactor1, cfactor2, cfactor1);
        double[] noiseData2 = noiseGen2.generate(null, subchunkX, subchunkY, subchunkZ, sizeX, sizeY, sizeZ, cfactor1, cfactor2, cfactor1);
        int i = 0;
        int j = 0;
        double[] ad = new double[sizeY];
        for (int y = 0; y < sizeY; y++) {
            ad[y] = Math.cos((y * Math.PI * 6D) / sizeY) * 2D;
            double d2 = y;
            if (y > sizeY / 2) d2 = sizeY - 1 - y;
            if (d2 < 4D) {
                d2 = 4D - d2;
                ad[y] -= d2 * d2 * d2 * 10D;
            }
        }
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                double d4 = 0.0D;
                double d5 = noiseData5[j] / 8000D;
                if (d5 < 0.0D) d5 = -d5;
                d5 = d5 * 3D - 3D;
                if (d5 < 0.0D) {
                    d5 /= 2D;
                    if (d5 < -1D) d5 = -1D;
                    d5 /= 1.4D;
                    d5 /= 2D;
                } else {
                    if (d5 > 1.0D) d5 = 1.0D;
                    d5 /= 6D;
                }
                j++;
                for (int y = 0; y < sizeY; y++) {
                    double d6;
                    double d7 = ad[y];
                    double d8 = noiseData1[i] / 512D;
                    double d9 = noiseData2[i] / 512D;
                    double d10 = (noiseData3[i] / 10D + 1.0D) / 2D;
                    if (d10 < 0.0D) {
                        d6 = d8;
                    } else if (d10 > 1.0D) {
                        d6 = d9;
                    } else {
                        d6 = d8 + (d9 - d8) * d10;
                    }
                    d6 -= d7;
                    if (y > sizeY - 4) {
                        double d11 = (y - (sizeY - 4)) / 3F;
                        d6 = d6 * (1.0D - d11) + -10D * d11;
                    }
                    if (y < d4) {
                        double d12 = (d4 - y) / 4D;
                        if (d12 < 0.0D) d12 = 0.0D;
                        if (d12 > 1.0D) d12 = 1.0D;
                        d6 = d6 * (1.0D - d12) + -10D * d12;
                    }
                    field[i] = d6;
                    i++;
                }
            }
        }
        return field;
    }
}

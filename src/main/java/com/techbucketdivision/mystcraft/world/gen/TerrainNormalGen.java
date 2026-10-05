package com.techbucketdivision.mystcraft.world.gen;

import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainContext;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * "Standard World" / "Amplified Normal World" terrain (original spec §4.3.6): the vanilla-1.6 overworld noise stack
 * (octaves 16/16/8/10/16, scale 684.412) with a parabolic 5×5 biome height/variation field.
 */
public final class TerrainNormalGen extends AbstractLegacyTerrainGen {
    private final boolean amplified;
    private final LegacyNoise noiseGen1;
    private final LegacyNoise noiseGen2;
    private final LegacyNoise noiseGen3;
    private final LegacyNoise noiseGen4;
    private final LegacyNoise noiseGen5;
    private final float[] parabolicField = new float[25];

    public TerrainNormalGen(long seed, boolean amplified, BlockState terrain, BlockState sea) {
        super(seed, terrain, sea, true, true);
        this.amplified = amplified;
        Random rand = new Random(seed);
        noiseGen1 = new LegacyNoise(rand, 16);
        noiseGen2 = new LegacyNoise(rand, 16);
        noiseGen3 = new LegacyNoise(rand, 8);
        noiseGen4 = new LegacyNoise(rand, 10);
        noiseGen5 = new LegacyNoise(rand, 16);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                parabolicField[(z + 2) * 5 + x + 2] = 10F / (float) Math.sqrt((x * x + z * z) + 0.2F);
            }
        }
    }

    public boolean isAmplified() {
        return amplified;
    }

    @Override
    protected double[] initializeNoiseField(TerrainContext ctx, double[] field, int subchunkX, int subchunkY, int subchunkZ,
                                            int sizeX, int sizeY, int sizeZ) {
        int bw = sizeX + 5;
        int bh = sizeZ + 5;
        Holder<Biome>[] biomes = sampleBiomes(ctx, subchunkX - 2, subchunkZ - 2, bw, bh);
        float[] heights = new float[bw * bh];
        float[] variations = new float[bw * bh];
        for (int i = 0; i < biomes.length; i++) {
            float h = ctx.biomeBaseHeight(biomes[i]);
            float v = ctx.biomeHeightVariation(biomes[i]);
            if (amplified && h > 0.0F) {
                h = 1.0F + h * 2.0F;
                v = 1.0F + v * 4.0F;
            }
            heights[i] = h;
            variations[i] = v;
        }
        if (field == null) field = new double[sizeX * sizeY * sizeZ];

        double cfactor1 = 684.412D;
        double cfactor2 = 684.412D;
        // noiseGen4 output was unused by the original; kept out for speed.
        double[] noiseData5 = noiseGen5.generate(null, subchunkX, 10, subchunkZ, sizeX, 1, sizeZ, 200D, 1.0D, 200D);
        double[] noiseData3 = noiseGen3.generate(null, subchunkX, subchunkY, subchunkZ, sizeX, sizeY, sizeZ, cfactor1 / 80D, cfactor2 / 160D, cfactor1 / 80D);
        double[] noiseData1 = noiseGen1.generate(null, subchunkX, subchunkY, subchunkZ, sizeX, sizeY, sizeZ, cfactor1, cfactor2, cfactor1);
        double[] noiseData2 = noiseGen2.generate(null, subchunkX, subchunkY, subchunkZ, sizeX, sizeY, sizeZ, cfactor1, cfactor2, cfactor1);
        int noiseIndex = 0;
        int noise5Index = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                float avgMaxHeight = 0.0F;
                float avgMinHeight = 0.0F;
                float sumTotalWeight = 0.0F;
                float centerHeight = heights[x + 2 + (z + 2) * bw];
                for (int xOffset = -2; xOffset <= 2; ++xOffset) {
                    for (int zOffset = -2; zOffset <= 2; ++zOffset) {
                        int bi = x + xOffset + 2 + (z + zOffset + 2) * bw;
                        float height = heights[bi];
                        float variation = variations[bi];
                        float weight = parabolicField[xOffset + 2 + (zOffset + 2) * 5] / (height + 2.0F);
                        if (height > centerHeight) weight /= 2.0F;
                        avgMaxHeight += variation * weight;
                        avgMinHeight += height * weight;
                        sumTotalWeight += weight;
                    }
                }
                avgMaxHeight /= sumTotalWeight;
                avgMinHeight /= sumTotalWeight;
                avgMaxHeight = avgMaxHeight * 0.9F + 0.1F;
                avgMinHeight = (avgMinHeight * 4F - 1.0F) / 8F;
                double noise5 = noiseData5[noise5Index++] / 8000D;
                if (noise5 < 0.0D) noise5 = -noise5 * 0.3D;
                noise5 = noise5 * 3D - 2D;
                if (noise5 < 0.0D) {
                    noise5 /= 2D;
                    if (noise5 < -1D) noise5 = -1D;
                    noise5 /= 1.4D;
                    noise5 /= 2D;
                } else {
                    if (noise5 > 1.0D) noise5 = 1.0D;
                    noise5 /= 8D;
                }
                for (int y = 0; y < sizeY; y++) {
                    double minHeightD = avgMinHeight;
                    double maxHeightD = avgMaxHeight;
                    minHeightD += noise5 * 0.2D;
                    minHeightD = (minHeightD * sizeY) / 16D;
                    double height = sizeY / 2D + minHeightD * 4D;
                    double density;
                    double avgDensity = ((y - height) * 12D * 128D) / 128 / maxHeightD;
                    if (avgDensity < 0.0D) avgDensity *= 4D;
                    double noise1 = noiseData1[noiseIndex] / 512D;
                    double noise2 = noiseData2[noiseIndex] / 512D;
                    double noise3 = (noiseData3[noiseIndex] / 10D + 1.0D) / 2D;
                    if (noise3 < 0.0D) {
                        density = noise1;
                    } else if (noise3 > 1.0D) {
                        density = noise2;
                    } else {
                        density = noise1 + (noise2 - noise1) * noise3;
                    }
                    density -= avgDensity;
                    if (y > sizeY - 4) {
                        double d11 = (y - (sizeY - 4)) / 3F;
                        density = density * (1.0D - d11) + -10D * d11;
                    }
                    field[noiseIndex++] = density;
                }
            }
        }
        return field;
    }

    @SuppressWarnings("unchecked")
    private static Holder<Biome>[] sampleBiomes(TerrainContext ctx, int quartX, int quartZ, int w, int h) {
        Holder<Biome>[] out = (Holder<Biome>[]) new Holder[w * h];
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                out[x + z * w] = ctx.biomeAt((quartX + x) << 2, (quartZ + z) << 2);
            }
        }
        return out;
    }
}

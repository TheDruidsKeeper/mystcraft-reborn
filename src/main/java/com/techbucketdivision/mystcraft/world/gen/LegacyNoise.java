package com.tbd.mystcraft.world.gen;

import java.util.Random;

/**
 * Faithful re-implementation of the pre-1.13 {@code NoiseGeneratorImproved} / {@code NoiseGeneratorOctaves} pair used
 * by the original Mystcraft terrain generators, so Ages keep their classic look. Instances are immutable after
 * construction (only permutation tables + offsets) and therefore safe to share between chunk-generation threads;
 * output arrays are always owned by the caller.
 */
public final class LegacyNoise {

    /** Single improved-Perlin octave (the original {@code NoiseGeneratorImproved}). */
    public static final class Octave {
        private static final double[] GRAD_X = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0, 1, 0, -1, 0};
        private static final double[] GRAD_Y = {1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1, 0, 1, 0, -1};
        private static final double[] GRAD_Z = {0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1, 0, 1, 0, -1};
        private static final double[] GRAD_2X = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0, 1, 0, -1, 0};
        private static final double[] GRAD_2Z = {0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1, 0, 1, 0, -1};

        private final int[] permutations = new int[512];
        private final double xCoord;
        private final double yCoord;
        private final double zCoord;

        public Octave(Random rand) {
            xCoord = rand.nextDouble() * 256.0D;
            yCoord = rand.nextDouble() * 256.0D;
            zCoord = rand.nextDouble() * 256.0D;
            for (int i = 0; i < 256; i++) permutations[i] = i;
            for (int i = 0; i < 256; ++i) {
                int j = rand.nextInt(256 - i) + i;
                int k = permutations[i];
                permutations[i] = permutations[j];
                permutations[j] = k;
                permutations[i + 256] = permutations[i];
            }
        }

        private static double lerp(double t, double a, double b) {
            return a + t * (b - a);
        }

        private static double grad2(int hash, double x, double z) {
            int j = hash & 15;
            return GRAD_2X[j] * x + GRAD_2Z[j] * z;
        }

        private static double grad(int hash, double x, double y, double z) {
            int j = hash & 15;
            return GRAD_X[j] * x + GRAD_Y[j] * y + GRAD_Z[j] * z;
        }

        /**
         * Adds this octave's contribution into {@code out} (index order x-major, then z, then y — exactly like the
         * original). {@code ySize == 1} selects the cheaper 2D path.
         */
        public void populate(double[] out, double xOff, double yOff, double zOff, int xSize, int ySize, int zSize,
                             double xScale, double yScale, double zScale, double amplitude) {
            int index = 0;
            double inv = 1.0D / amplitude;
            if (ySize == 1) {
                for (int ix = 0; ix < xSize; ++ix) {
                    double dx = xOff + ix * xScale + xCoord;
                    int fx = (int) dx;
                    if (dx < fx) --fx;
                    int xi = fx & 255;
                    dx -= fx;
                    double fadeX = dx * dx * dx * (dx * (dx * 6.0D - 15.0D) + 10.0D);
                    for (int iz = 0; iz < zSize; ++iz) {
                        double dz = zOff + iz * zScale + zCoord;
                        int fz = (int) dz;
                        if (dz < fz) --fz;
                        int zi = fz & 255;
                        dz -= fz;
                        double fadeZ = dz * dz * dz * (dz * (dz * 6.0D - 15.0D) + 10.0D);
                        int a = permutations[xi];
                        int aa = permutations[a] + zi;
                        int b = permutations[xi + 1];
                        int ba = permutations[b] + zi;
                        double l1 = lerp(fadeX, grad2(permutations[aa], dx, dz), grad(permutations[ba], dx - 1.0D, 0.0D, dz));
                        double l2 = lerp(fadeX, grad(permutations[aa + 1], dx, 0.0D, dz - 1.0D), grad(permutations[ba + 1], dx - 1.0D, 0.0D, dz - 1.0D));
                        out[index++] += lerp(fadeZ, l1, l2) * inv;
                    }
                }
                return;
            }
            int lastY = -1;
            double l1 = 0, l2 = 0, l3 = 0, l4 = 0;
            for (int ix = 0; ix < xSize; ++ix) {
                double dx = xOff + ix * xScale + xCoord;
                int fx = (int) dx;
                if (dx < fx) --fx;
                int xi = fx & 255;
                dx -= fx;
                double fadeX = dx * dx * dx * (dx * (dx * 6.0D - 15.0D) + 10.0D);
                for (int iz = 0; iz < zSize; ++iz) {
                    double dz = zOff + iz * zScale + zCoord;
                    int fz = (int) dz;
                    if (dz < fz) --fz;
                    int zi = fz & 255;
                    dz -= fz;
                    double fadeZ = dz * dz * dz * (dz * (dz * 6.0D - 15.0D) + 10.0D);
                    for (int iy = 0; iy < ySize; ++iy) {
                        double dy = yOff + iy * yScale + yCoord;
                        int fy = (int) dy;
                        if (dy < fy) --fy;
                        int yi = fy & 255;
                        dy -= fy;
                        double fadeY = dy * dy * dy * (dy * (dy * 6.0D - 15.0D) + 10.0D);
                        if (iy == 0 || yi != lastY) {
                            lastY = yi;
                            int a = permutations[xi] + yi;
                            int aa = permutations[a] + zi;
                            int ab = permutations[a + 1] + zi;
                            int b = permutations[xi + 1] + yi;
                            int ba = permutations[b] + zi;
                            int bb = permutations[b + 1] + zi;
                            l1 = lerp(fadeX, grad(permutations[aa], dx, dy, dz), grad(permutations[ba], dx - 1.0D, dy, dz));
                            l2 = lerp(fadeX, grad(permutations[ab], dx, dy - 1.0D, dz), grad(permutations[bb], dx - 1.0D, dy - 1.0D, dz));
                            l3 = lerp(fadeX, grad(permutations[aa + 1], dx, dy, dz - 1.0D), grad(permutations[ba + 1], dx - 1.0D, dy, dz - 1.0D));
                            l4 = lerp(fadeX, grad(permutations[ab + 1], dx, dy - 1.0D, dz - 1.0D), grad(permutations[bb + 1], dx - 1.0D, dy - 1.0D, dz - 1.0D));
                        }
                        double m1 = lerp(fadeY, l1, l2);
                        double m2 = lerp(fadeY, l3, l4);
                        out[index++] += lerp(fadeZ, m1, m2) * inv;
                    }
                }
            }
        }
    }

    private final Octave[] octaves;

    /** Equivalent of {@code new NoiseGeneratorOctaves(rand, octaveCount)}. */
    public LegacyNoise(Random rand, int octaveCount) {
        octaves = new Octave[octaveCount];
        for (int i = 0; i < octaveCount; i++) octaves[i] = new Octave(rand);
    }

    public int octaveCount() {
        return octaves.length;
    }

    /**
     * 3D octave noise into a caller-owned array (allocated when {@code out} is null or too small; always zeroed).
     * Layout: {@code out[(x * zSize + z) * ySize + y]}.
     */
    public double[] generate(double[] out, int xOff, int yOff, int zOff, int xSize, int ySize, int zSize,
                             double xScale, double yScale, double zScale) {
        int len = xSize * ySize * zSize;
        if (out == null || out.length < len) {
            out = new double[len];
        } else {
            java.util.Arrays.fill(out, 0, len, 0.0D);
        }
        double amp = 1.0D;
        for (Octave octave : octaves) {
            double d0 = xOff * amp * xScale;
            double d1 = yOff * amp * yScale;
            double d2 = zOff * amp * zScale;
            long k = (long) Math.floor(d0);
            long l = (long) Math.floor(d2);
            d0 -= k;
            d2 -= l;
            k %= 16777216L;
            l %= 16777216L;
            d0 += k;
            d2 += l;
            octave.populate(out, d0, d1, d2, xSize, ySize, zSize, xScale * amp, yScale * amp, zScale * amp, amp);
            amp /= 2.0D;
        }
        return out;
    }

    /** 2D convenience (the original 8-arg overload: y offset 10, y size 1). Layout {@code out[x * zSize + z]}. */
    public double[] generate2d(double[] out, int xOff, int zOff, int xSize, int zSize, double xScale, double zScale) {
        return generate(out, xOff, 10, zOff, xSize, 1, zSize, xScale, 1.0D, zScale);
    }
}

package com.tbd.mystcraft.world.biome;

/**
 * Minimal, thread-safe port of the pre-1.13 {@code GenLayer} stack used by the Mystcraft "Tiny…Huge" biome
 * distributions: a random-biome layer, integer zooms, a smoothing pass and the final Voronoi zoom. Values are indices
 * into the controller's biome list. Seed mixing matches the original ({@code initWorldGenSeed} / {@code initChunkSeed}
 * / {@code nextInt}) so shapes look the same; the per-call chunk seed lives on the stack instead of the instance.
 */
final class LegacyLayers {
    private LegacyLayers() {}

    private static final long MUL = 6364136223846793005L;
    private static final long ADD = 1442695040888963407L;

    /** Per-call random state (the original stored this on the layer instance). */
    static final class Cursor {
        long chunkSeed;
        final long worldGenSeed;

        Cursor(long worldGenSeed) {
            this.worldGenSeed = worldGenSeed;
        }

        void init(long x, long z) {
            long s = worldGenSeed;
            s *= s * MUL + ADD;
            s += x;
            s *= s * MUL + ADD;
            s += z;
            s *= s * MUL + ADD;
            s += x;
            s *= s * MUL + ADD;
            s += z;
            chunkSeed = s;
        }

        int nextInt(int bound) {
            int i = (int) ((chunkSeed >> 24) % bound);
            if (i < 0) i += bound;
            chunkSeed *= chunkSeed * MUL + ADD;
            chunkSeed += worldGenSeed;
            return i;
        }

        int select(int a, int b) {
            return nextInt(2) == 0 ? a : b;
        }

        int select(int a, int b, int c, int d) {
            return switch (nextInt(4)) {
                case 0 -> a;
                case 1 -> b;
                case 2 -> c;
                default -> d;
            };
        }

        int modeOrRandom(int a, int b, int c, int d) {
            if (b == c && c == d) return b;
            if (a == b && a == c) return a;
            if (a == b && a == d) return a;
            if (a == c && a == d) return a;
            if (a == b && c != d) return a;
            if (a == c && b != d) return a;
            if (a == d && b != c) return a;
            if (b == c && a != d) return b;
            if (b == d && a != c) return b;
            if (c == d && a != b) return c;
            return select(a, b, c, d);
        }
    }

    abstract static class Layer {
        protected final long baseSeed;
        protected long worldGenSeed;
        protected final Layer parent;

        Layer(long seed, Layer parent) {
            long s = seed;
            s *= s * MUL + ADD;
            s += seed;
            s *= s * MUL + ADD;
            s += seed;
            s *= s * MUL + ADD;
            s += seed;
            this.baseSeed = s;
            this.parent = parent;
        }

        final void initWorldGenSeed(long seed) {
            if (parent != null) parent.initWorldGenSeed(seed);
            long s = seed;
            s *= s * MUL + ADD;
            s += baseSeed;
            s *= s * MUL + ADD;
            s += baseSeed;
            s *= s * MUL + ADD;
            s += baseSeed;
            this.worldGenSeed = s;
        }

        abstract int[] getInts(int areaX, int areaZ, int width, int height);
    }

    /** Picks a uniformly random index in {@code [0, count)} per cell (the original {@code GenLayerBiomeMyst}). */
    static final class RandomBiome extends Layer {
        private final int count;

        RandomBiome(long seed, int count) {
            super(seed, null);
            this.count = count;
        }

        @Override
        int[] getInts(int areaX, int areaZ, int width, int height) {
            int[] out = new int[width * height];
            Cursor c = new Cursor(worldGenSeed);
            for (int z = 0; z < height; z++) {
                for (int x = 0; x < width; x++) {
                    c.init(x + areaX, z + areaZ);
                    out[x + z * width] = c.nextInt(count);
                }
            }
            return out;
        }
    }

    /** 2× zoom with random cell selection ({@code GenLayerZoom}). */
    static final class Zoom extends Layer {
        Zoom(long seed, Layer parent) {
            super(seed, parent);
        }

        static Layer magnify(long seedShift, Layer parent, int count) {
            Layer layer = parent;
            for (int i = 0; i < count; ++i) layer = new Zoom(seedShift + i, layer);
            return layer;
        }

        @Override
        int[] getInts(int areaX, int areaZ, int width, int height) {
            int px = areaX >> 1;
            int pz = areaZ >> 1;
            int pw = (width >> 1) + 3;
            int ph = (height >> 1) + 3;
            int[] in = parent.getInts(px, pz, pw, ph);
            int ow = pw << 1;
            int[] tmp = new int[ow * (ph << 1)];
            Cursor c = new Cursor(worldGenSeed);
            for (int z = 0; z < ph - 1; ++z) {
                int idx = (z << 1) * ow;
                int a = in[(z) * pw];
                int b = in[(z + 1) * pw];
                for (int x = 0; x < pw - 1; ++x) {
                    c.init((long) (x + px) << 1, (long) (z + pz) << 1);
                    int d = in[x + 1 + (z) * pw];
                    int e = in[x + 1 + (z + 1) * pw];
                    tmp[idx] = a;
                    tmp[idx++ + ow] = c.select(a, b);
                    tmp[idx] = c.select(a, d);
                    tmp[idx++ + ow] = c.modeOrRandom(a, d, b, e);
                    a = d;
                    b = e;
                }
            }
            int[] out = new int[width * height];
            for (int z = 0; z < height; ++z) {
                System.arraycopy(tmp, (z + (areaZ & 1)) * ow + (areaX & 1), out, z * width, width);
            }
            return out;
        }
    }

    /** Smooths single-cell noise ({@code GenLayerSmooth}). */
    static final class Smooth extends Layer {
        Smooth(long seed, Layer parent) {
            super(seed, parent);
        }

        @Override
        int[] getInts(int areaX, int areaZ, int width, int height) {
            int px = areaX - 1;
            int pz = areaZ - 1;
            int pw = width + 2;
            int ph = height + 2;
            int[] in = parent.getInts(px, pz, pw, ph);
            int[] out = new int[width * height];
            Cursor c = new Cursor(worldGenSeed);
            for (int z = 0; z < height; ++z) {
                for (int x = 0; x < width; ++x) {
                    int a = in[x + (z + 1) * pw];
                    int b = in[x + 2 + (z + 1) * pw];
                    int d = in[x + 1 + (z) * pw];
                    int e = in[x + 1 + (z + 2) * pw];
                    int f = in[x + 1 + (z + 1) * pw];
                    if (a == b && d == e) {
                        c.init(x + areaX, z + areaZ);
                        f = c.nextInt(2) == 0 ? a : d;
                    } else {
                        if (a == b) f = a;
                        if (d == e) f = d;
                    }
                    out[x + z * width] = f;
                }
            }
            return out;
        }
    }

    /** 4× Voronoi zoom producing the block-resolution map ({@code GenLayerVoronoiZoom}). */
    static final class VoronoiZoom extends Layer {
        VoronoiZoom(long seed, Layer parent) {
            super(seed, parent);
        }

        @Override
        int[] getInts(int areaX, int areaZ, int width, int height) {
            areaX -= 2;
            areaZ -= 2;
            int px = areaX >> 2;
            int pz = areaZ >> 2;
            int pw = (width >> 2) + 2;
            int ph = (height >> 2) + 2;
            int[] in = parent.getInts(px, pz, pw, ph);
            int ow = (pw - 1) << 2;
            int oh = (ph - 1) << 2;
            int[] tmp = new int[ow * oh];
            Cursor c = new Cursor(worldGenSeed);
            for (int z = 0; z < ph - 1; ++z) {
                int a = in[(z) * pw];
                int b = in[(z + 1) * pw];
                for (int x = 0; x < pw - 1; ++x) {
                    c.init((long) (x + px) << 2, (long) (z + pz) << 2);
                    double d0 = (c.nextInt(1024) / 1024.0D - 0.5D) * 3.6D;
                    double d1 = (c.nextInt(1024) / 1024.0D - 0.5D) * 3.6D;
                    c.init((long) (x + px + 1) << 2, (long) (z + pz) << 2);
                    double d2 = (c.nextInt(1024) / 1024.0D - 0.5D) * 3.6D + 4.0D;
                    double d3 = (c.nextInt(1024) / 1024.0D - 0.5D) * 3.6D;
                    c.init((long) (x + px) << 2, (long) (z + pz + 1) << 2);
                    double d4 = (c.nextInt(1024) / 1024.0D - 0.5D) * 3.6D;
                    double d5 = (c.nextInt(1024) / 1024.0D - 0.5D) * 3.6D + 4.0D;
                    c.init((long) (x + px + 1) << 2, (long) (z + pz + 1) << 2);
                    double d6 = (c.nextInt(1024) / 1024.0D - 0.5D) * 3.6D + 4.0D;
                    double d7 = (c.nextInt(1024) / 1024.0D - 0.5D) * 3.6D + 4.0D;
                    int d = in[x + 1 + (z) * pw];
                    int e = in[x + 1 + (z + 1) * pw];
                    for (int zz = 0; zz < 4; ++zz) {
                        int idx = ((z << 2) + zz) * ow + (x << 2);
                        for (int xx = 0; xx < 4; ++xx) {
                            double e0 = (zz - d1) * (zz - d1) + (xx - d0) * (xx - d0);
                            double e1 = (zz - d3) * (zz - d3) + (xx - d2) * (xx - d2);
                            double e2 = (zz - d5) * (zz - d5) + (xx - d4) * (xx - d4);
                            double e3 = (zz - d7) * (zz - d7) + (xx - d6) * (xx - d6);
                            if (e0 < e1 && e0 < e2 && e0 < e3) {
                                tmp[idx++] = a;
                            } else if (e1 < e0 && e1 < e2 && e1 < e3) {
                                tmp[idx++] = d;
                            } else if (e2 < e0 && e2 < e1 && e2 < e3) {
                                tmp[idx++] = b;
                            } else {
                                tmp[idx++] = e;
                            }
                        }
                    }
                    a = d;
                    b = e;
                }
            }
            int[] out = new int[width * height];
            for (int z = 0; z < height; ++z) {
                System.arraycopy(tmp, (z + (areaZ & 3)) * ow + (areaX & 3), out, z * width, width);
            }
            return out;
        }
    }

    /**
     * Builds the Mystcraft stack: random biome → zoom×2 → zoom×{@code zoom} → smooth → Voronoi. Returns the
     * block-resolution layer.
     */
    static Layer build(long worldSeed, int zoom, int biomeCount) {
        Layer layer = new RandomBiome(200L, biomeCount);
        layer = Zoom.magnify(1000L, layer, 2);
        for (int i = 0; i < zoom; i++) layer = new Zoom(1000L + i, layer);
        layer = new Smooth(1000L, layer);
        Layer voronoi = new VoronoiZoom(10L, layer);
        voronoi.initWorldGenSeed(worldSeed);
        return voronoi;
    }
}

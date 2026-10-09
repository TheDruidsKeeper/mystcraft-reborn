package com.tbd.mystcraft.world.feature;

import com.tbd.mystcraft.api.symbol.logic.TerrainAlteration;
import com.tbd.mystcraft.api.symbol.logic.TerrainContext;
import com.tbd.mystcraft.world.gen.ChunkBlocks;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Random;

/**
 * "Huge Trees" (original spec §4.3.7): per chunk (range 8) 50% chance of a giant tree — 2×2 trunk from y 4..11 up to
 * a leaf blob at y ∈ [128,180] of height 40–69, 4 leaf nodes per layer, {@code height/4} roots. Port of
 * {@code WorldGenMystBigTree}; all tree state is local to one call so the instance is thread-safe.
 */
public final class HugeTreesAlteration extends AbstractMapGen implements TerrainAlteration {
    private static final byte[] OTHER_COORD_PAIRS = {2, 0, 0, 1, 2, 1};
    private static final int[] COORD_MAXIMUMS = {16, 255, 16};
    private static final double SCALE_WIDTH = 0.2D;
    private static final int MAX_Y = 180;
    private static final int MIN_Y = 128;
    private static final int LEAF_DISTANCE_LIMIT = 3;
    private static final int TRUNK_SIZE = 2;

    private final BlockState log;
    private final BlockState leaves;

    public HugeTreesAlteration(long seed) {
        super(seed, Blocks.OAK_LOG.defaultBlockState(), 8);
        this.log = Blocks.OAK_LOG.defaultBlockState();
        this.leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    @Override
    public void alterTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {
        generate(new ChunkBlocks(chunk), chunkX, chunkZ);
    }

    @Override
    protected void recursiveGenerate(Random rand, int x, int z, int chunkX, int chunkZ, ChunkBlocks blocks) {
        if (rand.nextInt(2) != 0) return;
        int pX = x * 16 + rand.nextInt(16) - chunkX * 16;
        int pY = rand.nextInt(8) + 4;
        int pZ = z * 16 + rand.nextInt(16) - chunkZ * 16;
        Tree tree = new Tree(rand, blocks);
        tree.rootPos[0] = tree.blobPos[0] = pX;
        tree.rootPos[1] = tree.blobPos[1] = pY;
        tree.rootPos[2] = tree.blobPos[2] = pZ;
        tree.blobPos[1] = rand.nextInt(MAX_Y - MIN_Y + 1) + MIN_Y;
        tree.blobHeight = rand.nextInt(30) + 40;
        tree.generateLeafNodeList();
        tree.generateLeaves();
        tree.generateTrunk();
        tree.generateRoots();
        tree.generateLeafNodeBases();
    }

    /** Per-call tree state. */
    private final class Tree {
        final Random rand;
        final ChunkBlocks blocks;
        final int[] blobPos = {0, 0, 0};
        final int[] rootPos = {0, 0, 0};
        int blobHeight;
        int[][] leafNodes = new int[0][];

        Tree(Random rand, ChunkBlocks blocks) {
            this.rand = rand;
            this.blocks = blocks;
        }

        void generateLeafNodeList() {
            int perLayer = 4;
            int[][] ai = new int[perLayer * blobHeight][4];
            int j = (blobPos[1] + blobHeight) - LEAF_DISTANCE_LIMIT;
            int k = 1;
            int l = (int) (blobPos[1] + blobHeight * 0.9D);
            int i1 = j - blobPos[1];
            ai[0][0] = blobPos[0];
            ai[0][1] = j;
            ai[0][2] = blobPos[2];
            ai[0][3] = l;
            --j;
            double d = 0.5D;
            while (i1 >= 0) {
                float f = layerSize(i1);
                if (f < 0.0F) {
                    --j;
                    --i1;
                } else {
                    for (int j1 = 0; j1 < perLayer; ++j1) {
                        double d1 = SCALE_WIDTH * (f * (rand.nextFloat() + 3.0D));
                        double d2 = rand.nextFloat() * 2D * 3.14159D;
                        int k1 = Mth.floor(d1 * Math.sin(d2) + blobPos[0] + d);
                        int l1 = Mth.floor(d1 * Math.cos(d2) + blobPos[2] + d);
                        ai[k][0] = k1;
                        ai[k][1] = j;
                        ai[k][2] = l1;
                        ai[k][3] = j - 2;
                        ++k;
                    }
                    --j;
                    --i1;
                }
            }
            leafNodes = new int[k][4];
            System.arraycopy(ai, 0, leafNodes, 0, k);
        }

        void genTreeLayer(int x, int y, int z, float f, byte axis0, BlockState block) {
            int radius = (int) (f + 0.618D);
            float f2 = f * f;
            byte axis1 = OTHER_COORD_PAIRS[axis0];
            byte axis2 = OTHER_COORD_PAIRS[axis0 + 3];
            int[] basePos = {x, y, z};
            int[] localPos = {0, 0, 0};
            localPos[axis0] = basePos[axis0];
            if (localPos[axis0] < 0 || localPos[axis0] >= COORD_MAXIMUMS[axis0]) return;
            for (int axis1Offset = -radius; axis1Offset <= radius; ++axis1Offset) {
                localPos[axis1] = basePos[axis1] + axis1Offset;
                if (localPos[axis1] < 0 || localPos[axis1] >= COORD_MAXIMUMS[axis1]) continue;
                for (int axis2Offset = -radius; axis2Offset <= radius; ++axis2Offset) {
                    localPos[axis2] = basePos[axis2] + axis2Offset;
                    if (localPos[axis2] < 0 || localPos[axis2] >= COORD_MAXIMUMS[axis2]) continue;
                    if (Math.pow(axis1Offset + 0.5D, 2D) + Math.pow(axis2Offset + 0.5D, 2D) < f2) {
                        placeBlock(blocks, localPos[0], localPos[1], localPos[2], block);
                    }
                }
            }
        }

        float layerSize(int i) {
            if (i < blobHeight * 0.75D) return -1.618F;
            float f = blobHeight * 0.5F;
            float f1 = blobHeight * 0.5F - i;
            float f2;
            if (f1 == 0.0F) {
                f2 = f;
            } else if (Math.abs(f1) >= f) {
                f2 = 0.0F;
            } else {
                f2 = (float) fastSqrt(Math.pow(f, 2D) - Math.pow(f1, 2D));
            }
            f2 *= 0.5F;
            return f2;
        }

        float leafSize(int i) {
            if (i < 0 || i >= LEAF_DISTANCE_LIMIT) return -1F;
            return i != 0 && i != LEAF_DISTANCE_LIMIT - 1 ? 3F : 2.0F;
        }

        void generateLeafNode(int i, int j, int k) {
            for (int l = j; l < j + LEAF_DISTANCE_LIMIT; l++) {
                float f = leafSize(l - j);
                if (f < 0) continue;
                genTreeLayer(i, l, k, f, (byte) 1, leaves);
            }
        }

        void placeBlockLine(int[] start, int[] end, BlockState block) {
            int[] delta = {0, 0, 0};
            int major = 0;
            for (byte b = 0; b < 3; ++b) {
                delta[b] = end[b] - start[b];
                if (Math.abs(delta[b]) > Math.abs(delta[major])) major = b;
            }
            if (delta[major] == 0) return;
            byte a1 = OTHER_COORD_PAIRS[major];
            byte a2 = OTHER_COORD_PAIRS[major + 3];
            byte step = (byte) (delta[major] > 0 ? 1 : -1);
            double d = (double) delta[a1] / (double) delta[major];
            double d1 = (double) delta[a2] / (double) delta[major];
            int[] localPos = {0, 0, 0};
            int k = 0;
            for (int l = delta[major] + step; k != l; k += step) {
                localPos[major] = Mth.floor((start[major] + k) + 0.5D);
                localPos[a1] = Mth.floor(start[a1] + k * d + 0.5D);
                localPos[a2] = Mth.floor(start[a2] + k * d1 + 0.5D);
                if (localPos[0] < 0 || localPos[0] >= 16) continue;
                if (localPos[1] < 0 || localPos[1] >= 255) continue;
                if (localPos[2] < 0 || localPos[2] >= 16) continue;
                placeBlock(blocks, localPos[0], localPos[1], localPos[2], block);
            }
        }

        void generateLeaves() {
            for (int[] node : leafNodes) generateLeafNode(node[0], node[1], node[2]);
        }

        boolean leafNodeNeedsBase(int i) {
            return i >= blobHeight * 0.2D;
        }

        void generateTrunk() {
            int i = rootPos[0];
            int j = rootPos[1];
            int k = (int) (blobPos[1] + blobHeight * 0.9D);
            int l = rootPos[2];
            int[] ai = {i, j, l};
            int[] ai1 = {i, k, l};
            placeBlockLine(ai, ai1, log);
            if (TRUNK_SIZE == 2) {
                ai[0]++;
                ai1[0]++;
                if (inFootprint(ai)) placeBlockLine(ai, ai1, log);
                ai[2]++;
                ai1[2]++;
                if (inFootprint(ai)) placeBlockLine(ai, ai1, log);
                ai[0]--;
                ai1[0]--;
                if (inFootprint(ai)) placeBlockLine(ai, ai1, log);
            }
        }

        private boolean inFootprint(int[] p) {
            return p[0] >= 0 && p[0] < COORD_MAXIMUMS[0] && p[2] >= 0 && p[2] < COORD_MAXIMUMS[2];
        }

        void generateRoots() {
            int i = rootPos[0];
            int j = rootPos[1];
            int k = rootPos[2];
            int[] ai = {i, j + 1, k};
            int[] ai1 = {i, j, k};
            int range = rootPos[1];
            int count = blobHeight >> 2;
            for (int c = 0; c < count; ++c) {
                ai[0] = i + c % 2;
                ai[2] = k + (c > 2 ? 1 : 0);
                ai1[0] = i + rand.nextInt(13) - 6;
                ai1[1] = j - rand.nextInt(range + 1) - 3;
                ai1[2] = k + rand.nextInt(13) - 6;
                placeBlockLine(ai, ai1, log);
            }
        }

        void generateLeafNodeBases() {
            int[] ai = {blobPos[0], blobPos[1], blobPos[2]};
            for (int[] node : leafNodes) {
                int[] ai2 = {node[0], node[1], node[2]};
                ai[1] = node[3];
                int k = ai[1] - blobPos[1];
                if (leafNodeNeedsBase(k)) placeBlockLine(ai, ai2, log);
            }
        }
    }

    /** The original's fast approximate sqrt (kept so the canopy shapes match). */
    private static double fastSqrt(double a) {
        long x = Double.doubleToLongBits(a) >> 32;
        return Double.longBitsToDouble((x + 1072632448) << 31);
    }
}

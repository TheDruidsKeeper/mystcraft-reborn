package com.techbucketdivision.mystcraft.world.feature;

import com.techbucketdivision.mystcraft.world.gen.ChunkBlocks;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Random;

/**
 * Shared "cave node" walker of the original caves / tendrils / spheres / floating-island generators: a randomly
 * bending tube of varying radius that branches once and stamps ellipsoids into the chunk.
 */
public abstract class AbstractTunnelGen extends AbstractMapGen {

    /** How each ellipsoid slice is stamped. */
    protected enum Shape {
        /** Classic cave: horizontal disc test plus {@code yfactor > -0.7} floor clamp. */
        CAVE,
        /** Full ellipsoid (spheres). */
        SPHERE,
        /** Full ellipsoid without the horizontal pre-check, recording touched columns (floating islands). */
        BLOB
    }

    protected AbstractTunnelGen(long seed, @Nullable BlockState state, int range) {
        super(seed, state, range);
    }

    /** Large starting node (called 25% of the time by caves). */
    protected void generateLargeNode(Random rand, int chunkX, int chunkZ, ChunkBlocks blocks, double baseX, double baseY, double baseZ,
                                     double squash, Shape shape) {
        generateNode(rand.nextLong(), chunkX, chunkZ, blocks, null, baseX, baseY, baseZ, 1.0F + rand.nextFloat() * 6F, 0.0F, 0.0F,
                -1, -1, squash, shape, 2.0F);
    }

    /**
     * @param modified    optional 16×16 column flags updated for every placed block (index {@code x | z << 4})
     * @param scalar      radius scalar
     * @param angleB      yaw
     * @param angleC      pitch
     * @param loopc       start step (-1 = single-step "room")
     * @param maxLoops    total steps (≤0 = random from range)
     * @param squash      vertical squash of the tube
     * @param pitchNoise  pitch jitter multiplier (2 for caves/spheres, 1 for islands)
     */
    protected void generateNode(long nodeSeed, int chunkX, int chunkZ, ChunkBlocks blocks, boolean @Nullable [] modified,
                                double baseX, double baseY, double baseZ, float scalar, float angleB, float angleC,
                                int loopc, int maxLoops, double squash, Shape shape, float pitchNoise) {
        double chunkXmid = chunkX * 16 + 8;
        double chunkZmid = chunkZ * 16 + 8;
        float f = 0.0F;
        float f1 = 0.0F;
        Random random = new Random(nodeSeed);

        if (maxLoops <= 0) {
            int i = range * 16 - 16;
            maxLoops = i - random.nextInt(i / 4);
        }
        boolean single = false;
        if (loopc == -1) {
            loopc = maxLoops / 2;
            single = true;
        }
        int branchAt = random.nextInt(maxLoops / 2) + maxLoops / 4;
        boolean slowPitchDecay = random.nextInt(6) == 0;

        for (; loopc < maxLoops; ++loopc) {
            double d2 = 1.5D + (Mth.sin((loopc * (float) Math.PI) / maxLoops) * scalar * 1.0F);
            double d3 = d2 * squash;
            float f2 = Mth.cos(angleC);
            float f3 = Mth.sin(angleC);
            baseX += Mth.cos(angleB) * f2;
            baseY += f3;
            baseZ += Mth.sin(angleB) * f2;

            angleC *= slowPitchDecay ? 0.92F : 0.7F;
            angleC += f1 * 0.1F;
            angleB += f * 0.1F;
            f1 *= 0.9F;
            f *= 0.75F;
            f1 += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * pitchNoise;
            f += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4F;

            if (!single && loopc == branchAt && scalar > 1.0F && maxLoops > 0) {
                double branchSquash = shape == Shape.BLOB ? squash : 1.0D;
                generateNode(random.nextLong(), chunkX, chunkZ, blocks, modified, baseX, baseY, baseZ, random.nextFloat() * 0.5F + 0.5F,
                        angleB - ((float) Math.PI / 2F), angleC / 3F, loopc, maxLoops, branchSquash, shape, pitchNoise);
                generateNode(random.nextLong(), chunkX, chunkZ, blocks, modified, baseX, baseY, baseZ, random.nextFloat() * 0.5F + 0.5F,
                        angleB + ((float) Math.PI / 2F), angleC / 3F, loopc, maxLoops, branchSquash, shape, pitchNoise);
                return;
            }
            if (!single && random.nextInt(4) == 0) continue;

            double xoffset = baseX - chunkXmid;
            double zoffset = baseZ - chunkZmid;
            double remaining = maxLoops - loopc;
            double d7 = scalar + 2.0F + 16F;
            if ((xoffset * xoffset + zoffset * zoffset) - remaining * remaining > d7 * d7) return;
            if (baseX < chunkXmid - 16D - d2 * 2D || baseZ < chunkZmid - 16D - d2 * 2D
                    || baseX > chunkXmid + 16D + d2 * 2D || baseZ > chunkZmid + 16D + d2 * 2D) continue;

            int minX = Mth.floor(baseX - d2) - chunkX * 16 - 1;
            int maxX = (Mth.floor(baseX + d2) - chunkX * 16) + 1;
            int minY = Mth.floor(baseY - d3) - 1;
            int maxY = Mth.floor(baseY + d3) + 1;
            int minZ = Mth.floor(baseZ - d2) - chunkZ * 16 - 1;
            int maxZ = (Mth.floor(baseZ + d2) - chunkZ * 16) + 1;
            if (minX < 0) minX = 0;
            if (maxX > 16) maxX = 16;
            if (minY < 1) minY = 1;
            if (maxY > LAYERS) maxY = LAYERS;
            if (minZ < 0) minZ = 0;
            if (maxZ > 16) maxZ = 16;

            for (int localY = minY; localY < maxY; ++localY) {
                double yfactor = ((localY + 0.5D) - baseY) / d3;
                double yfactorSq = yfactor * yfactor;
                for (int localZ = minZ; localZ < maxZ; ++localZ) {
                    double zfactor = (((localZ + chunkZ * 16) + 0.5D) - baseZ) / d2;
                    double zfactorSq = zfactor * zfactor;
                    for (int localX = minX; localX < maxX; ++localX) {
                        double xfactor = (((localX + chunkX * 16) + 0.5D) - baseX) / d2;
                        double xfactorSq = xfactor * xfactor;
                        boolean place;
                        switch (shape) {
                            case CAVE -> place = xfactorSq + zfactorSq < 1.0D && yfactor > -0.7D && xfactorSq + yfactorSq + zfactorSq < 1.0D;
                            case SPHERE -> place = xfactorSq + zfactorSq < 1.0D && xfactorSq + yfactorSq + zfactorSq < 1.0D;
                            default -> place = xfactorSq + yfactorSq + zfactorSq < 1.0D;
                        }
                        if (place && placeBlock(blocks, localX, localY, localZ) && modified != null) {
                            modified[localX | localZ << 4] = true;
                        }
                    }
                }
            }
            if (single) break;
        }
    }
}

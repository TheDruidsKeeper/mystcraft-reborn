package com.techbucketdivision.mystcraft.world.feature;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.api.symbol.logic.Populator;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * "Star Fissure" (original spec §4.3.9): only in the spawn chunk, a fissure (10–17 rows, widths random-walked) of
 * {@code mystcraft:star_fissure} at y=0 with everything above cleared to the top of the world. Returns {@code true}
 * so later populators (lakes) leave the fissure alone.
 *
 * <p>The spawn chunk is the chunk of {@code AgeData.spawn()}; when no spawn has been determined yet (the Age has
 * never been entered) chunk (0,0) is used, matching the original's default spawn point.
 */
public final class StarFissurePopulator implements Populator {
    public StarFissurePopulator() {}

    /**
     * The fissure always generates in chunk (0,0). The spawn chunk is not usable as the anchor: the spawn is
     * determined by probing heightmaps (which generates the chunk, running this populator) *before*
     * {@code AgeData.spawn()} is set, so the two could never agree. Instead {@link AgeSpawn} keeps the spawn search
     * near the origin for Ages that have this populator, matching the original (spawn within ±64 of 0,0).
     */
    @Override
    public boolean populate(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag) {
        if (chunkX != 0 || chunkZ != 0) return false;
        // Kept within ±1 chunk of the anchor chunk (the decoration region's write radius): centred origin + centred rows.
        int x = (chunkX << 4) + 4 + random.nextInt(8);
        int z = (chunkZ << 4) + 4 + random.nextInt(8);
        BlockPos origin = new BlockPos(x, 0, z);
        generate(level, random, origin);
        AgeController controller = AgeControllers.server(level.getLevel());
        Mystcraft.LOGGER.info("[worldgen] star fissure generated at {} in Age '{}'", origin.toShortString(),
                controller == null ? level.getLevel().dimension().identifier() : controller.ageData().name());
        return true;
    }

    private static void generate(WorldGenLevel level, RandomSource random, BlockPos pos) {
        int[][] rows = generateNoise(random);
        int zStart = -rows.length;
        for (int row = 0; row < rows.length; ++row) {
            for (int x = rows[row][0]; x <= rows[row][1]; ++x) {
                set(level, pos.offset(x, 0, zStart + row * 2));
                set(level, pos.offset(x, 0, zStart + row * 2 + 1));
            }
        }
    }

    private static void set(WorldGenLevel level, BlockPos pos) {
        BlockState fissure = ModBlocks.STAR_FISSURE.get().defaultBlockState();
        level.setBlock(pos, fissure, 3);
        BlockState air = Blocks.AIR.defaultBlockState();
        int top = level.getMinY() + level.getHeight() - 1;
        BlockPos.MutableBlockPos at = pos.mutable();
        while (at.getY() < top) {
            at.move(0, 1, 0);
            if (!level.getBlockState(at).isAir()) level.setBlock(at, air, 2);
        }
    }

    private static int[][] generateNoise(RandomSource rand) {
        int length = rand.nextInt(8) + 10;
        int[][] noise = new int[length][2];
        noise[0][0] = noise[0][1] = 0;
        for (int row = 1; row < noise.length; ++row) {
            int scale = noise.length + Math.min(row, noise.length - row) + 1;
            noise[row][1] = rand.nextInt(scale) - (scale >> 1);
            noise[row][1] = (noise[row][1] + noise[row - 1][1]) / 2;
            noise[row][0] = rand.nextInt(scale) - (scale >> 1);
            noise[row][0] = (noise[row][0] + noise[row - 1][0]) / 2;
            if (noise[row][0] > noise[row][1]) {
                int temp = noise[row][0];
                noise[row][0] = noise[row][1];
                noise[row][1] = temp;
            }
            if (noise[row][0] > noise[row - 1][1]) noise[row][0] = noise[row - 1][1];
            if (noise[row][1] < noise[row - 1][0]) noise[row][1] = noise[row - 1][0];
        }
        return noise;
    }
}

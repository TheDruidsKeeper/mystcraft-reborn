package com.techbucketdivision.mystcraft.world.feature;

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
 * "Star Fissure" (REQUIREMENTS §4.3.9): only in the spawn chunk, a fissure (10–17 rows, widths random-walked) of
 * {@code mystcraft:star_fissure} at y=0 with everything above cleared to the top of the world. Returns {@code true}
 * so later populators (lakes) leave the fissure alone.
 *
 * <p>The spawn chunk is the chunk of {@code AgeData.spawn()}; when no spawn has been determined yet (the Age has
 * never been entered) chunk (0,0) is used, matching the original's default spawn point.
 */
public final class StarFissurePopulator implements Populator {
    public StarFissurePopulator() {}

    @Override
    public boolean populate(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag) {
        BlockPos spawn = spawnPos(level);
        int spawnChunkX = spawn == null ? 0 : spawn.getX() >> 4;
        int spawnChunkZ = spawn == null ? 0 : spawn.getZ() >> 4;
        if (chunkX != spawnChunkX || chunkZ != spawnChunkZ) return false;
        // Kept within ±1 chunk of the spawn chunk (the decoration region's write radius): centred origin + centred rows.
        int x = (chunkX << 4) + 4 + random.nextInt(8);
        int z = (chunkZ << 4) + 4 + random.nextInt(8);
        generate(level, random, new BlockPos(x, 0, z));
        return true;
    }

    private static @Nullable BlockPos spawnPos(WorldGenLevel level) {
        AgeController controller = AgeControllers.server(level.getLevel());
        return controller == null ? null : controller.ageData().spawn();
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

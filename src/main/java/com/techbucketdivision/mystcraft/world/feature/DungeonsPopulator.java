package com.techbucketdivision.mystcraft.world.feature;

import com.techbucketdivision.mystcraft.api.symbol.logic.Populator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** "Dungeons" (REQUIREMENTS §4.3.8): 8 vanilla monster-room attempts per chunk at random y 0–255. */
public final class DungeonsPopulator implements Populator {
    private final ConfiguredFeature<NoneFeatureConfiguration, ?> monsterRoom =
            new ConfiguredFeature<>(Feature.MONSTER_ROOM, NoneFeatureConfiguration.INSTANCE);

    public DungeonsPopulator() {}

    @Override
    public boolean populate(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag) {
        ChunkGenerator generator = level.getLevel().getChunkSource().getGenerator();
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        for (int i = 0; i < 8; i++) {
            int x = bx + random.nextInt(16) + 8;
            int y = random.nextInt(256);
            int z = bz + random.nextInt(16) + 8;
            monsterRoom.place(level, generator, random, new BlockPos(x, y, z));
        }
        return false;
    }
}

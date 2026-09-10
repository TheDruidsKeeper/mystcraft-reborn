package com.techbucketdivision.mystcraft.api.symbol.logic;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;

/** Decoration-stage feature placement (lakes, obelisks, spikes, dense ores, star fissure...). */
public interface Populator {
    /**
     * @param flag {@code true} if an earlier populator in this chunk returned {@code true}
     * @return {@code true} if this populator changed the chunk in a way later populators should respect (e.g. lakes)
     */
    boolean populate(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag);
}

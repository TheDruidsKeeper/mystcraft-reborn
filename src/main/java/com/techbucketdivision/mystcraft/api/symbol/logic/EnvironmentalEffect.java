package com.techbucketdivision.mystcraft.api.symbol.logic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Runs every tick for every loaded chunk of the Age (meteors, lightning, scorching, decay...). */
public interface EnvironmentalEffect {
    void tick(ServerLevel level, LevelChunk chunk);
}

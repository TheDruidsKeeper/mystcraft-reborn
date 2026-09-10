package com.techbucketdivision.mystcraft.instability.effects;

import com.techbucketdivision.mystcraft.api.symbol.logic.EnvironmentalEffect;
import com.techbucketdivision.mystcraft.entity.MeteorEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** 1/50000 per chunk tick: a meteor (scale 1, penetration 0) falls from y=500 over a random column. */
public final class MeteorEffect implements EnvironmentalEffect {
    private final ChunkLcg lcg = new ChunkLcg();

    public MeteorEffect() {}

    @Override
    public void tick(ServerLevel level, LevelChunk chunk) {
        if (level.getRandom().nextInt(50000) != 0) return;
        int coords = lcg.next();
        int x = chunk.getPos().getMinBlockX() + ChunkLcg.localX(coords);
        int z = chunk.getPos().getMinBlockZ() + ChunkLcg.localZ(coords);
        MeteorEntity.spawn(level, x + 0.5, z + 0.5, 1.0f, 0);
    }
}

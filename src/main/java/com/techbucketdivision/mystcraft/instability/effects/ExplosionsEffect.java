package com.techbucketdivision.mystcraft.instability.effects;

import com.techbucketdivision.mystcraft.api.symbol.logic.EnvironmentalEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/** 1/1000 per chunk tick: a flaming explosion of power 3 at a random column (original spec §4.3.10 / §6.5). */
public final class ExplosionsEffect implements EnvironmentalEffect {
    private final ChunkLcg lcg = new ChunkLcg();

    public ExplosionsEffect() {}

    @Override
    public void tick(ServerLevel level, LevelChunk chunk) {
        if (level.getRandom().nextInt(1000) != 0) return;
        int coords = lcg.next();
        int x = chunk.getPos().getMinBlockX() + ChunkLcg.localX(coords);
        int z = chunk.getPos().getMinBlockZ() + ChunkLcg.localZ(coords);
        int y = ChunkLcg.y255(coords) + level.getMinY() + 1;
        level.explode(null, x + 0.5, y, z + 0.5, 3.0f, true, Level.ExplosionInteraction.TNT);
    }
}

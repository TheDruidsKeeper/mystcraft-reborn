package com.techbucketdivision.mystcraft.instability.effects;

import com.techbucketdivision.mystcraft.api.symbol.logic.EnvironmentalEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 1/10 per chunk tick: a random entity in the chunk that can see the sky is set on fire for {@code 4 * level}
 * seconds (REQUIREMENTS §4.3.10). With {@code global} the sky check is skipped.
 */
public final class ScorchedEffect implements EnvironmentalEffect {
    private final int level;
    private final boolean global;

    public ScorchedEffect(int level) {
        this(level, false);
    }

    public ScorchedEffect(int level, boolean global) {
        this.level = Math.max(1, level);
        this.global = global;
    }

    @Override
    public void tick(ServerLevel world, LevelChunk chunk) {
        if (world.getRandom().nextInt(10) != 0) return;
        Entity entity = ChunkEntities.random(world, chunk);
        if (entity == null) return;
        if (global || world.canSeeSky(entity.blockPosition())) {
            entity.igniteForSeconds(4f * level);
        }
    }
}

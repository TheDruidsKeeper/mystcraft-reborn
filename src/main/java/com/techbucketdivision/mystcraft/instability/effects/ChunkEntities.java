package com.tbd.mystcraft.instability.effects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Replacement for the original per-chunk entity lists: picks a random entity inside a chunk column. */
final class ChunkEntities {
    private ChunkEntities() {}

    static AABB columnBox(ServerLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        return new AABB(pos.getMinBlockX(), level.getMinY(), pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1, level.getMaxY() + 1, pos.getMaxBlockZ() + 1);
    }

    static @Nullable Entity random(ServerLevel level, LevelChunk chunk) {
        List<Entity> entities = level.getEntities((Entity) null, columnBox(level, chunk));
        if (entities.isEmpty()) return null;
        return entities.get(level.getRandom().nextInt(entities.size()));
    }
}

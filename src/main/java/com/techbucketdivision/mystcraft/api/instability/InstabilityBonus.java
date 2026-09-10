package com.techbucketdivision.mystcraft.api.instability;

import net.minecraft.server.level.ServerLevel;

/** A temporary per-Age instability adjustment (REQUIREMENTS §6.6). Negative values stabilise the Age. */
public interface InstabilityBonus {
    String name();

    int value();

    /** Called once per level tick. */
    void tick(ServerLevel level);
}

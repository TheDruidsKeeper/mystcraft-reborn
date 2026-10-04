package com.techbucketdivision.mystcraft.api.symbol.logic;

import com.techbucketdivision.mystcraft.api.symbol.CreatureDifficulty;
import com.techbucketdivision.mystcraft.api.symbol.CreatureGroup;

/**
 * Age logic for one creature group (world-building plan §10): how often its mobs spawn, how many may exist and - for
 * hostile mobs - how dangerous they are. Registered by the {@code creatures_*} symbols; without one the group spawns
 * exactly as in the biomes.
 */
public interface CreatureController {
    CreatureGroup group();

    /** Multiplier on the biome spawn weights of the group (0 = the group never spawns naturally). */
    float rate();

    /** Multiplier on the vanilla mob cap of the group's categories (0 = no cap at all, i.e. nothing may spawn). */
    float capFactor();

    /** Only meaningful for {@link CreatureGroup#HOSTILE}; the others report {@link CreatureDifficulty#NORMAL}. */
    CreatureDifficulty difficulty();
}

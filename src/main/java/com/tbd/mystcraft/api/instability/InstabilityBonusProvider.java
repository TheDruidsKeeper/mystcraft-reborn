package com.tbd.mystcraft.api.instability;

import com.tbd.mystcraft.instability.InstabilityBonusManager;
import net.minecraft.server.level.ServerLevel;

/** Registered globally; asked to contribute {@link InstabilityBonus}es to every Age's bonus manager. */
@FunctionalInterface
public interface InstabilityBonusProvider {
    void register(InstabilityBonusManager manager, ServerLevel level);
}

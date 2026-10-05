package com.tbd.mystcraft.api.instability;

import com.tbd.mystcraft.api.symbol.logic.EnvironmentalEffect;

/** Handed to {@link InstabilityProvider}s so they can register effects against the Age's instability controller. */
public interface InstabilityDirector {
    /** Current (quantised) instability score of the Age. */
    int getInstabilityScore();

    /** Registers an effect that runs every chunk tick while the provider is active. */
    void registerEffect(EnvironmentalEffect effect);
}

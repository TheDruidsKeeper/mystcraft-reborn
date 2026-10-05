package com.techbucketdivision.mystcraft.api.instability;

/**
 * A card in the instability decks (original spec §6.4). Registered with
 * {@code instability.InstabilityManager#register(String id, InstabilityProvider, int activationCost)}.
 */
@FunctionalInterface
public interface InstabilityProvider {
    /**
     * Adds this provider's effects to the controller.
     *
     * @param level number of copies of this card drawn from the decks (>= 1)
     */
    void addEffects(InstabilityDirector director, int level);
}

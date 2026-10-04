package com.techbucketdivision.mystcraft.api.symbol;

/** How dangerous an Age's hostile creatures are (plan §10): health and damage multipliers applied when they spawn. */
public enum CreatureDifficulty {
    EASY(0.75f, 0.75f),
    NORMAL(1f, 1f),
    HARD(1.5f, 1.25f),
    BRUTAL(2f, 1.5f);

    public final float healthFactor;
    public final float damageFactor;

    CreatureDifficulty(float healthFactor, float damageFactor) {
        this.healthFactor = healthFactor;
        this.damageFactor = damageFactor;
    }

    /** One step harder (Frenzy); BRUTAL stays BRUTAL. */
    public CreatureDifficulty harder() {
        return this == BRUTAL ? BRUTAL : values()[ordinal() + 1];
    }
}

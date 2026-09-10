package com.techbucketdivision.mystcraft.symbol.symbols;

import com.techbucketdivision.mystcraft.api.symbol.AgeDirector;
import org.jspecify.annotations.Nullable;

/**
 * Symbol without logic (dark sun/moon/stars, "Lacking ... Features"). Adds a fixed instability per occurrence.
 */
public final class DummySymbol extends SimpleSymbol {
    private final int instability;

    public DummySymbol(String path, @Nullable Integer cardRank, int instability, String... poemWords) {
        super(path, cardRank, poemWords);
        this.instability = instability;
    }

    public DummySymbol(String path, @Nullable Integer cardRank, String... poemWords) {
        this(path, cardRank, 0, poemWords);
    }

    @Override
    public void registerLogic(AgeDirector director, long seed) {}

    @Override
    public int instabilityModifier(int count) {
        return instability;
    }
}

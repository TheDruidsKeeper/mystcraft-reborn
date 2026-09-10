package com.techbucketdivision.mystcraft.symbol.symbols;

import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.util.MystIds;
import org.jspecify.annotations.Nullable;

/** Convenience base for built-in symbols: id {@code mystcraft:<path>}. */
public abstract class SimpleSymbol extends AgeSymbol {

    protected SimpleSymbol(String path, @Nullable Integer cardRank, String... poemWords) {
        super(MystIds.id(path), cardRank, poemWords);
    }
}

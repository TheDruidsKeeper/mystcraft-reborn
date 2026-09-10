package com.techbucketdivision.mystcraft.symbol.grammar;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A context-free production {@code parent -> values}. Tokens are plain strings: non-terminals use the original
 * mixed-case names ({@code "Age"}, {@code "TerrainGen"}, {@code "BlockTerrain"}...), terminals are symbol ids in
 * {@code "namespace:path"} form (REQUIREMENTS §4.4.1).
 * <p>
 * The rank is turned into a weight per parent token by {@link Grammar} (highest rank present gets weight 1, each lower
 * rank at least the total weight of the rank above it + 1). A {@code null} rank means weight 0: the rule is never chosen
 * when expanding randomly but still usable to connect written symbols.
 */
public final class Rule {
    private final String parent;
    private final List<String> values;
    private final @Nullable Integer rank;

    public Rule(String parent, List<String> values, @Nullable Integer rank) {
        this.parent = Objects.requireNonNull(parent, "rule parent");
        this.values = List.copyOf(values);
        this.rank = rank;
    }

    public String parent() {
        return parent;
    }

    /** The produced tokens in order; empty for an epsilon rule. */
    public List<String> values() {
        return values;
    }

    public @Nullable Integer rank() {
        return rank;
    }

    public int size() {
        return values.size();
    }

    /** Selection weight (0 when rank is {@code null}). */
    public float weight() {
        if (rank == null) return 0f;
        return Grammar.weightFor(parent, rank);
    }

    @Override
    public String toString() {
        return parent + " -> " + String.join(" ", values) + " (" + rank + ")";
    }
}

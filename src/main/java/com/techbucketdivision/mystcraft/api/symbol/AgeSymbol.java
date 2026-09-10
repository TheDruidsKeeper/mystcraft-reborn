package com.techbucketdivision.mystcraft.api.symbol;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A symbol that can be written on a page and contributes logic to an Age.
 * <p>
 * Symbols are registered into {@link com.techbucketdivision.mystcraft.symbol.SymbolRegistry}. Each symbol has a
 * four-word "poem" (rendered as the page glyph), a card rank (rarity tier, {@code null} = never generated randomly or
 * traded) and an instability contribution.
 */
public abstract class AgeSymbol {
    private final Identifier id;
    private final @Nullable Integer cardRank;
    private final List<String> poem;

    protected AgeSymbol(Identifier id, @Nullable Integer cardRank, String... poemWords) {
        if (poemWords.length != 4) {
            throw new IllegalArgumentException("Symbol " + id + " must have exactly 4 poem words, got " + poemWords.length);
        }
        this.id = Objects.requireNonNull(id);
        this.cardRank = cardRank;
        this.poem = List.of(poemWords);
    }

    public final Identifier id() {
        return id;
    }

    /** Rarity tier: 0 = very common ... 5 = rarest; {@code null} = not obtainable through generation/trade. */
    public @Nullable Integer cardRank() {
        return cardRank;
    }

    /** The four words drawn on the page, in order top, right, bottom, left. */
    public List<String> poem() {
        return poem;
    }

    /** Translation key. Override for symbols whose names are computed. */
    public String descriptionId() {
        return "symbol." + id.getNamespace() + "." + id.getPath();
    }

    public Component displayName() {
        return Component.translatable(descriptionId());
    }

    /**
     * Register this symbol's logic with the director. Called once per occurrence of the symbol in an Age, in page order.
     *
     * @param director the Age under construction
     * @param seed     a per-symbol-instance random seed derived from the Age seed
     */
    public abstract void registerLogic(AgeDirector director, long seed);

    /** Instability added when this symbol is added to an Age for the {@code count}-th time (1-based). */
    public int instabilityModifier(int count) {
        return 0;
    }

    /** Whether a config toggle {@code symbols.<id>.enabled} is generated for this symbol. */
    public boolean generatesConfigOption() {
        return true;
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof AgeSymbol other && other.id.equals(id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AgeSymbol[" + id + "]";
    }
}

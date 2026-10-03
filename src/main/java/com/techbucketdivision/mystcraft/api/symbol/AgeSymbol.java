package com.techbucketdivision.mystcraft.api.symbol;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A symbol that can be written on a page and contributes logic to an Age.
 * <p>
 * Symbols are registered into {@link com.techbucketdivision.mystcraft.symbol.SymbolRegistry}. Each symbol has a
 * four-word "poem" (rendered as the page glyph), a card rank (rarity tier, {@code null} = never traded) and an
 * instability contribution.
 * <p>
 * World-building schema: every registered symbol has a {@link #category()} (set with {@link #withCategory} before
 * registration, otherwise inferred from the logic it registers), the modifier slots it {@link #accepts()} and, for
 * modifier symbols, the slot it {@link #fills()}. The registry derives these from a dry run of {@link #registerLogic}.
 */
public abstract class AgeSymbol {
    private final Identifier id;
    private final @Nullable Integer cardRank;
    private final List<String> poem;
    private @Nullable SymbolCategory category;
    private Set<ModifierSlot> accepts = Set.of();
    private @Nullable ModifierSlot fills;
    private Set<BlockCategory> blockCategories = Set.of();

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

    // --- world-building schema -------------------------------------------------------------------------------

    /** Sets the category before registration; returns this for chaining. */
    public final AgeSymbol withCategory(SymbolCategory category) {
        this.category = category;
        return this;
    }

    /** The category this symbol belongs to. Only valid once the symbol is registered. */
    public final SymbolCategory category() {
        if (category == null) throw new IllegalStateException("Symbol " + id + " has no category (not registered)");
        return category;
    }

    public final boolean hasCategory() {
        return category != null;
    }

    /** Modifier slots a page of this symbol takes (empty for modifiers and symbols without options). */
    public final Set<ModifierSlot> accepts() {
        return accepts;
    }

    /** The slot this symbol fills when attached to another page; {@code null} for primary symbols. */
    public final @Nullable ModifierSlot fills() {
        return fills;
    }

    /**
     * Block categories a {@link ModifierSlot#BLOCK} modifier supplies (override in block symbols), or a primary symbol
     * takes (recorded by the registry). Empty otherwise.
     */
    public Set<BlockCategory> blockCategories() {
        return blockCategories;
    }

    /** Whether {@code modifier} can be attached to a page of this symbol. */
    public final boolean takes(AgeSymbol modifier) {
        ModifierSlot slot = modifier.fills();
        if (slot == null || !accepts.contains(slot)) return false;
        if (slot != ModifierSlot.BLOCK) return true;
        for (BlockCategory c : modifier.blockCategories()) {
            if (blockCategories().contains(c) || blockCategories().contains(BlockCategory.ANY)) return true;
        }
        return false;
    }

    /** Registry hook: records what the dry run of {@link #registerLogic} found. */
    public final void bindSchema(SymbolCategory category, Set<ModifierSlot> accepts, @Nullable ModifierSlot fills,
                                 Set<BlockCategory> takesBlocks) {
        this.category = category;
        this.accepts = Set.copyOf(accepts);
        this.fills = fills;
        this.blockCategories = Set.copyOf(takesBlocks);
    }

    /** Translation key. Override for symbols whose names are computed. */
    public String descriptionId() {
        return "symbol." + id.getNamespace() + "." + id.getPath();
    }

    public Component displayName() {
        return Component.translatable(descriptionId());
    }

    /** What the symbol does, for the book view and tooltips ({@code <descriptionId>.desc}); override for computed symbols. */
    public Component description() {
        return Component.translatable(descriptionId() + ".desc");
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

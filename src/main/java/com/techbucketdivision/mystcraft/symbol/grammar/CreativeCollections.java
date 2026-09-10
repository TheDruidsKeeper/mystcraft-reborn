package com.techbucketdivision.mystcraft.symbol.grammar;

import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.BlockCategory;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.item.PortfolioItem;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Spawned (...)" symbol portfolios for the creative tab (REQUIREMENTS §8.7). Contents = every symbol appearing as a
 * value of a rule whose parent is one of the collection's tokens, sorted by id.
 */
public final class CreativeCollections {
    private CreativeCollections() {}

    private record Collection(String title, List<String> tokens) {}

    private static List<Collection> collections() {
        List<String> blockTokens = new ArrayList<>();
        for (BlockCategory c : BlockCategory.all().values()) blockTokens.add(c.grammarToken());
        return List.of(
                new Collection("All Symbols", List.of()),
                new Collection("Biome Distributions", List.of(GrammarRules.BIOME_CONTROLLER)),
                new Collection("Celestials", List.of(GrammarRules.SUN, GrammarRules.MOON, GrammarRules.STARFIELD, GrammarRules.DOODAD)),
                new Collection("Effects", List.of(GrammarRules.EFFECT)),
                new Collection("Lighting", List.of(GrammarRules.LIGHTING)),
                new Collection("Modifiers, Basic", List.of(GrammarRules.ANGLE_BASIC, GrammarRules.PERIOD_BASIC, GrammarRules.PHASE_BASIC)),
                new Collection("Modifiers, Biomes", List.of(GrammarRules.BIOME)),
                new Collection("Modifiers, Block", blockTokens),
                new Collection("Modifiers, Colors", List.of(GrammarRules.COLOR_BASIC, GrammarRules.COLOR, GrammarRules.GRADIENT_BASIC,
                        GrammarRules.GRADIENT, GrammarRules.SUNSET)),
                new Collection("World Features", List.of(GrammarRules.FEATURE_SMALL, GrammarRules.FEATURE_MEDIUM, GrammarRules.FEATURE_LARGE)),
                new Collection("World Landscapes", List.of(GrammarRules.TERRAIN)),
                new Collection("Visuals", List.of(GrammarRules.VISUAL)),
                new Collection("Weather", List.of(GrammarRules.WEATHER)));
    }

    /** Never call from a static initialiser (creates ItemStacks). */
    public static List<ItemStack> portfolios() {
        List<ItemStack> out = new ArrayList<>();
        for (Collection c : collections()) {
            List<AgeSymbol> symbols = symbolsFor(c.tokens());
            if (symbols.isEmpty()) continue;
            List<ItemStack> pages = new ArrayList<>(symbols.size());
            for (AgeSymbol s : symbols) pages.add(PageItem.createSymbolPage(s));
            out.add(PortfolioItem.create("Spawned (" + c.title() + ")", pages));
        }
        return out;
    }

    /** Symbols for the tokens (all registered symbols when the token list is empty), sorted by id. */
    public static List<AgeSymbol> symbolsFor(List<String> tokens) {
        Set<AgeSymbol> set = new LinkedHashSet<>();
        if (tokens.isEmpty()) {
            set.addAll(SymbolRegistry.all());
        } else {
            for (String token : tokens) set.addAll(Grammar.symbolsExpandingToken(token));
        }
        List<AgeSymbol> list = new ArrayList<>(set);
        list.sort(Comparator.comparing(s -> s.id().toString()));
        return list;
    }
}

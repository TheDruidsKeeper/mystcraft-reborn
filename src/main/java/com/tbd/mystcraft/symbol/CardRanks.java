package com.tbd.mystcraft.symbol;

import com.tbd.mystcraft.api.symbol.AgeSymbol;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Card-rank → item weight table (original spec §4.1). Rebuilt when the symbol registry freezes. Also the source of
 * truth for loot/trade weights and booster generation.
 */
public final class CardRanks {
    private CardRanks() {}

    private static final Map<Integer, Integer> WEIGHT_BY_RANK = new TreeMap<>();
    private static final Map<Identifier, Integer> WEIGHT_OVERRIDES = new HashMap<>();
    private static final Map<Identifier, Integer> MAX_STACK_OVERRIDES = new HashMap<>();
    private static final Map<Identifier, Boolean> TRADEABLE_OVERRIDES = new HashMap<>();
    private static final Map<Identifier, Integer> TRADE_PRICE_OVERRIDES = new HashMap<>();
    private static final Map<Integer, List<AgeSymbol>> BY_RANK = new TreeMap<>();

    static synchronized void rebuild(Collection<AgeSymbol> symbols) {
        WEIGHT_BY_RANK.clear();
        BY_RANK.clear();
        Map<Integer, Integer> counts = new TreeMap<>();
        for (AgeSymbol s : symbols) {
            Integer rank = s.cardRank();
            if (rank == null) continue;
            counts.merge(rank, 1, Integer::sum);
            BY_RANK.computeIfAbsent(rank, r -> new ArrayList<>()).add(s);
        }
        List<Integer> ranks = new ArrayList<>(counts.keySet());
        ranks.sort((a, b) -> Integer.compare(b, a)); // highest first
        int weight = 1;
        int lastTotal = 0;
        boolean first = true;
        for (int rank : ranks) {
            if (!first) {
                weight = Math.max(weight + 1, lastTotal / counts.get(rank) + 1);
            }
            WEIGHT_BY_RANK.put(rank, weight);
            lastTotal = counts.get(rank) * weight;
            first = false;
        }
    }

    public static int weightForRank(@Nullable Integer rank) {
        if (rank == null) return 0;
        return WEIGHT_BY_RANK.getOrDefault(rank, 0);
    }

    public static int itemWeight(AgeSymbol symbol) {
        Integer override = WEIGHT_OVERRIDES.get(symbol.id());
        return override != null ? override : weightForRank(symbol.cardRank());
    }

    /** Default max stack in treasure per rank: 0→16, 1→8, 2→4, 3→2, else 1. */
    public static int maxTreasureStack(AgeSymbol symbol) {
        Integer override = MAX_STACK_OVERRIDES.get(symbol.id());
        if (override != null) return override;
        Integer rank = symbol.cardRank();
        if (rank == null) return 1;
        return switch (rank) {
            case 0 -> 16;
            case 1 -> 8;
            case 2 -> 4;
            case 3 -> 2;
            default -> 1;
        };
    }

    public static boolean isTradeable(AgeSymbol symbol) {
        Boolean override = TRADEABLE_OVERRIDES.get(symbol.id());
        return override != null ? override : itemWeight(symbol) > 0;
    }

    /** Emerald price: {@code max(1, 12 * rank)} unless overridden. */
    public static int tradePrice(AgeSymbol symbol) {
        Integer override = TRADE_PRICE_OVERRIDES.get(symbol.id());
        if (override != null) return override;
        Integer rank = symbol.cardRank();
        return rank == null ? 100 : Math.max(1, 12 * rank);
    }

    public static void overrideWeight(Identifier id, int weight) {
        WEIGHT_OVERRIDES.put(id, weight);
    }

    public static void overrideMaxStack(Identifier id, int max) {
        MAX_STACK_OVERRIDES.put(id, max);
    }

    public static void overrideTradeable(Identifier id, boolean tradeable) {
        TRADEABLE_OVERRIDES.put(id, tradeable);
    }

    public static void overrideTradePrice(Identifier id, int emeralds) {
        TRADE_PRICE_OVERRIDES.put(id, emeralds);
    }

    /** All symbols with exactly this rank. */
    public static List<AgeSymbol> ofRank(int rank) {
        return BY_RANK.getOrDefault(rank, List.of());
    }

    /** Symbols with rank {@code >= minRank}. */
    public static List<AgeSymbol> ofRankAtLeast(int minRank) {
        List<AgeSymbol> out = new ArrayList<>();
        BY_RANK.forEach((r, list) -> {
            if (r >= minRank) out.addAll(list);
        });
        return out;
    }

    /** Weighted random pick among candidates by item weight; {@code null} if none has weight. */
    public static @Nullable AgeSymbol weightedRandom(List<AgeSymbol> candidates, RandomSource random) {
        int total = 0;
        for (AgeSymbol s : candidates) total += itemWeight(s);
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (AgeSymbol s : candidates) {
            roll -= itemWeight(s);
            if (roll < 0) return s;
        }
        return candidates.getLast();
    }
}

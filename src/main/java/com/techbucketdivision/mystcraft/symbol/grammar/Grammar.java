package com.techbucketdivision.mystcraft.symbol.grammar;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * The Age grammar (REQUIREMENTS §4.4): a registry of {@link Rule}s, rank weights per parent token, a precomputed
 * shortest-path table (BFS over reverse rules) and the random/written-page expansion entry points.
 * <p>
 * Ports {@code GrammarGenerator} of the original. Tokens are strings; symbol terminals are {@code id.toString()}.
 * Rules may be added at any time; the derived tables are rebuilt lazily on next use (this is what lets biome and fluid
 * symbols register their rules per server after {@link #bootstrap()} ran).
 */
public final class Grammar {
    private Grammar() {}

    private static final class RankData {
        final List<Integer> rankSizes = new ArrayList<>();
        final Map<Integer, Integer> rankWeights = new HashMap<>();
    }

    /** Rules the token expands to. */
    private static final Map<String, List<Rule>> MAPPINGS = new LinkedHashMap<>();
    /** Rules that produce the token. */
    private static final Map<String, List<Rule>> REVERSE = new LinkedHashMap<>();
    private static final Map<String, RankData> RANKS = new HashMap<>();
    /** Symbol rules waiting for their symbol to be present in the registry. */
    private static final Map<Identifier, List<Rule>> PENDING_SYMBOL_RULES = new LinkedHashMap<>();
    private static final Set<Rule> REGISTERED = new LinkedHashSet<>();

    private static Map<String, Map<String, List<List<Rule>>>> shortestPaths = new HashMap<>();
    private static boolean coreRegistered;
    private static boolean dirty = true;

    // --- registration ------------------------------------------------------------------------------------------

    /** Registers the core rules and every pending symbol rule, then builds the derived tables. */
    public static synchronized void bootstrap() {
        if (!coreRegistered) {
            GrammarRules.registerCore();
            coreRegistered = true;
        }
        rebuild();
        Mystcraft.LOGGER.info("Mystcraft grammar: {} rules over {} tokens", REGISTERED.size(), MAPPINGS.size());
    }

    /** Registers {@code parent -> tokens} with the given rank ({@code null} = connect-only). */
    public static synchronized void registerRule(String parent, @Nullable Integer rank, String... tokens) {
        registerRule(new Rule(parent, List.of(tokens), rank));
    }

    public static synchronized void registerRule(Rule rule) {
        if (!REGISTERED.add(rule)) return;
        MAPPINGS.computeIfAbsent(rule.parent(), k -> new ArrayList<>()).add(rule);
        for (String value : rule.values()) {
            REVERSE.computeIfAbsent(value, k -> new ArrayList<>()).add(rule);
        }
        if (rule.rank() != null) {
            RankData data = RANKS.computeIfAbsent(rule.parent(), k -> new RankData());
            while (data.rankSizes.size() <= rule.rank()) data.rankSizes.add(0);
            data.rankSizes.set(rule.rank(), data.rankSizes.get(rule.rank()) + 1);
        }
        dirty = true;
    }

    /**
     * Adds the rule {@code parent -> precedingTokens... symbol} for a symbol. The rule only becomes active once the
     * symbol is present in the {@link SymbolRegistry} (symbols disabled by config never contribute rules).
     */
    public static synchronized void addSymbolRule(AgeSymbol symbol, String parent, @Nullable Integer rank, String... precedingTokens) {
        List<String> values = new ArrayList<>(precedingTokens.length + 1);
        Collections.addAll(values, precedingTokens);
        values.add(symbol.id().toString());
        PENDING_SYMBOL_RULES.computeIfAbsent(symbol.id(), k -> new ArrayList<>()).add(new Rule(parent, values, rank));
        dirty = true;
    }

    /** Symbol token for use in rules. */
    public static String token(AgeSymbol symbol) {
        return symbol.id().toString();
    }

    // --- derived tables ----------------------------------------------------------------------------------------

    private static synchronized void ensureBuilt() {
        if (dirty) rebuild();
    }

    private static synchronized void rebuild() {
        flushPending();
        buildShortestPaths();
        buildRankWeights();
        dirty = false;
        warnUnexpandableTokens();
    }

    private static void flushPending() {
        Iterator<Map.Entry<Identifier, List<Rule>>> it = PENDING_SYMBOL_RULES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Identifier, List<Rule>> e = it.next();
            if (!SymbolRegistry.contains(e.getKey())) continue;
            for (Rule r : e.getValue()) registerRule(r);
            it.remove();
        }
    }

    private static void buildRankWeights() {
        final int step = 1;
        for (RankData data : RANKS.values()) {
            data.rankWeights.clear();
            int weight = 1;
            int lastTotal = 0;
            for (int i = data.rankSizes.size() - 1; i >= 0; --i) {
                int count = data.rankSizes.get(i);
                if (weight != 1 && count > 0) {
                    weight = Math.max(weight, lastTotal / count + step);
                }
                data.rankWeights.put(i, weight);
                lastTotal = count * weight;
                weight += step;
            }
        }
    }

    static synchronized float weightFor(String parent, int rank) {
        RankData data = RANKS.get(parent);
        if (data == null) return 0f;
        Integer w = data.rankWeights.get(rank);
        return w == null ? 0f : w;
    }

    private record Visit(String target, List<Rule> path) {}

    private static void buildShortestPaths() {
        shortestPaths = new HashMap<>();
        for (String token : REVERSE.keySet()) {
            if (!shortestPaths.containsKey(token)) computePaths(token);
        }
    }

    /** BFS upward through producing rules; keeps every path of minimal length per reachable ancestor. */
    private static Map<String, List<List<Rule>>> computePaths(String token) {
        Map<String, List<List<Rule>>> allPaths = new HashMap<>();
        LinkedList<Visit> toVisit = new LinkedList<>();
        List<Rule> producers = REVERSE.get(token);
        if (producers != null) {
            for (Rule rule : producers) toVisit.add(new Visit(rule.parent(), List.of(rule)));
        }
        while (!toVisit.isEmpty()) {
            Visit elem = toVisit.removeFirst();
            String target = elem.target();
            if (target.equals(token)) continue;
            List<Rule> path = elem.path();
            List<List<Rule>> pathsToTarget = allPaths.computeIfAbsent(target, k -> new ArrayList<>());
            if (!pathsToTarget.isEmpty() && pathsToTarget.getFirst().size() > path.size()) {
                pathsToTarget.clear();
            }
            if (pathsToTarget.isEmpty() || pathsToTarget.getFirst().size() == path.size()) {
                pathsToTarget.add(path);
                List<Rule> targetProducers = REVERSE.get(target);
                if (targetProducers != null) {
                    for (Rule producer : targetProducers) {
                        List<Rule> extended = new ArrayList<>(path.size() + 1);
                        extended.addAll(path);
                        extended.add(producer);
                        toVisit.add(new Visit(producer.parent(), Collections.unmodifiableList(extended)));
                    }
                }
            }
        }
        shortestPaths.put(token, allPaths);
        return allPaths;
    }

    // --- queries -------------------------------------------------------------------------------------------------

    /** Rules that produce {@code token} (never null). */
    public static synchronized List<Rule> parentRules(String token) {
        ensureBuilt();
        List<Rule> rules = REVERSE.get(token);
        return rules == null ? List.of() : Collections.unmodifiableList(rules);
    }

    /** Rules {@code token} expands to, or {@code null} if it is a terminal. */
    public static synchronized @Nullable List<Rule> rules(String token) {
        ensureBuilt();
        List<Rule> rules = MAPPINGS.get(token);
        return rules == null ? null : Collections.unmodifiableList(rules);
    }

    /**
     * Picks a rule for random expansion, or {@code null} if the token has no rule that may be chosen randomly.
     * <p>
     * Unlike {@link #pickWeighted} this never falls back to an even pick. A {@code null} rank means weight 0 —
     * "never chosen when expanding randomly" (REQUIREMENTS §4.4.1) — and those connector rules are deliberately
     * cyclic ({@code Angle -> Angle_Ext AngleBasic}, {@code Angle_Ext -> Angle}), so picking one here recurses
     * until the stack overflows.
     */
    public static synchronized @Nullable Rule randomRule(String token, RandomSource rand) {
        ensureBuilt();
        List<Rule> rules = MAPPINGS.get(token);
        if (rules == null || rules.isEmpty()) return null;
        return pickWeightedStrict(rand, rules, Rule::weight);
    }

    /**
     * Depth limit for {@link #explore}. The grammar is intentionally left-recursive ({@code BiomesAdv -> BiomesAdv
     * Biome}), so expansion terminates only probabilistically; this caps the tail instead of overflowing the stack.
     */
    private static final int MAX_EXPLORE_DEPTH = 128;

    /** Recursively expands {@code token} with weighted random rules until only terminals remain. */
    public static synchronized List<String> explore(String token, RandomSource rand) {
        return explore(token, rand, 0);
    }

    private static List<String> explore(String token, RandomSource rand, int depth) {
        List<String> out = new ArrayList<>();
        if (depth >= MAX_EXPLORE_DEPTH) {
            Mystcraft.LOGGER.warn("Grammar expansion reached the depth limit at token '{}'; stopping this branch", token);
            return out;
        }
        Rule rule = randomRule(token, rand);
        if (rule == null) {
            List<Rule> all = MAPPINGS.get(token);
            if (all == null || all.isEmpty()) {
                // A genuine terminal: a symbol id.
                out.add(token);
            } else {
                // Only connect-only (rank null) rules exist, so there is nothing to generate here.
                Mystcraft.LOGGER.debug("Grammar token '{}' has no randomly selectable rule; expanding to nothing", token);
            }
            return out;
        }
        for (String t : rule.values()) out.addAll(explore(t, rand, depth + 1));
        return out;
    }

    /**
     * Logs any non-terminal whose rules are all weight 0. Such a token can never be expanded randomly, which usually
     * means a rule was registered with a {@code null} rank by mistake.
     */
    private static void warnUnexpandableTokens() {
        for (Map.Entry<String, List<Rule>> entry : MAPPINGS.entrySet()) {
            List<Rule> rules = entry.getValue();
            if (rules.isEmpty()) continue;
            boolean any = false;
            for (Rule r : rules) {
                if (r.weight() > 0f) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                Mystcraft.LOGGER.warn("Grammar token '{}' has {} rule(s) but none can be chosen randomly (all rank null)",
                        entry.getKey(), rules.size());
            }
        }
    }

    /**
     * Shortest connecting paths of rules from {@code subtreeToken} up to {@code nodeToken}, in order from the subtree
     * upward, or {@code null} if unreachable. All paths have the same (minimal) length.
     */
    public static synchronized @Nullable List<List<Rule>> shortestPaths(String subtreeToken, String nodeToken) {
        ensureBuilt();
        Map<String, List<List<Rule>>> all = shortestPaths.get(subtreeToken);
        if (all == null) return null;
        List<List<Rule>> paths = all.get(nodeToken);
        return paths == null ? null : Collections.unmodifiableList(paths);
    }

    /** Every registered symbol appearing as a value of a rule whose parent is {@code token}. */
    public static synchronized List<AgeSymbol> symbolsExpandingToken(String token) {
        ensureBuilt();
        Set<AgeSymbol> symbols = new LinkedHashSet<>();
        List<Rule> rules = MAPPINGS.get(token);
        if (rules != null) {
            for (Rule rule : rules) {
                for (String value : rule.values()) {
                    Identifier id = Identifier.tryParse(value);
                    if (id == null) continue;
                    AgeSymbol symbol = SymbolRegistry.get(id);
                    if (symbol != null) symbols.add(symbol);
                }
            }
        }
        return new ArrayList<>(symbols);
    }

    /** Parent tokens of every rule producing {@code token}. */
    public static synchronized Set<String> tokensProducingToken(String token) {
        Set<String> out = new LinkedHashSet<>();
        for (Rule r : parentRules(token)) out.add(r.parent());
        return out;
    }

    /** All known tokens (parents and values). */
    public static synchronized Set<String> allTokens() {
        ensureBuilt();
        Set<String> out = new LinkedHashSet<>(MAPPINGS.keySet());
        out.addAll(REVERSE.keySet());
        return out;
    }

    // --- generation ----------------------------------------------------------------------------------------------

    /** Random expansion of {@code token} (creative/random Ages, "myst-create" without pages). */
    public static List<Identifier> generateFromToken(String token, RandomSource rand) {
        ensureBuilt();
        GrammarTree tree = new GrammarTree(token);
        return toIdentifiers(tree.getExpanded(rand));
    }

    /** Expansion of {@code token} around already-written symbols. */
    public static List<Identifier> generateFromToken(String token, RandomSource rand, List<Identifier> written) {
        ensureBuilt();
        GrammarTree tree = new GrammarTree(token);
        tree.parseTerminals(written.stream().map(Identifier::toString).toList(), rand);
        return toIdentifiers(tree.getExpanded(rand));
    }

    /** Full Age symbol list from the written pages (REQUIREMENTS §4.4.3), root token {@code Age}. */
    public static List<Identifier> expandAge(List<Identifier> written, RandomSource rand) {
        return generateFromToken(GrammarRules.AGE, rand, written);
    }

    private static List<Identifier> toIdentifiers(List<String> tokens) {
        List<Identifier> out = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            Identifier id = t.indexOf(':') > 0 ? Identifier.tryParse(t) : null;
            if (id == null) {
                Mystcraft.LOGGER.debug("Grammar produced non-symbol token {} (dropped)", t);
                continue;
            }
            out.add(id);
        }
        return out;
    }

    // --- weighted selection (port of WeightedItemSelector) --------------------------------------------------------

    public static <T> float totalWeight(List<T> items, ToDoubleFunction<T> weight) {
        float total = 0f;
        for (T item : items) total += (float) weight.applyAsDouble(item);
        return total;
    }

    /** Weighted pick; falls back to an even pick when no item has weight. */
    public static <T> @Nullable T pickWeighted(RandomSource rand, List<T> items, ToDoubleFunction<T> weight) {
        if (items.isEmpty()) return null;
        float max = totalWeight(items, weight);
        if (max <= 0f) return pickEvenly(rand, items);
        T last = null;
        float selection = rand.nextFloat() * max;
        for (T item : items) {
            float w = (float) weight.applyAsDouble(item);
            selection -= w;
            if (w > 0f) {
                if (selection <= 0f) return item;
                last = item;
            }
        }
        return last;
    }

    /** Like {@link #pickWeighted} but returns {@code null} instead of falling back to an even pick. */
    public static <T> @Nullable T pickWeightedStrict(RandomSource rand, List<T> items, ToDoubleFunction<T> weight) {
        if (items.isEmpty()) return null;
        float max = totalWeight(items, weight);
        if (max <= 0f) return null;
        T last = null;
        float selection = rand.nextFloat() * max;
        for (T item : items) {
            float w = (float) weight.applyAsDouble(item);
            selection -= w;
            if (w > 0f) {
                if (selection <= 0f) return item;
                last = item;
            }
        }
        return last;
    }

    public static <T> @Nullable T pickEvenly(RandomSource rand, List<T> items) {
        if (items.isEmpty()) return null;
        return items.get(rand.nextInt(items.size()));
    }
}

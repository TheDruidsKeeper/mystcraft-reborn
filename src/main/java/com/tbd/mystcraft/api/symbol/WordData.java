package com.tbd.mystcraft.api.symbol;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The Narayan word list used for symbol poems, mapped to glyph component indices into
 * {@code textures/symbolcomponents.png} (512x512, 8x8 grid of 64px components; index = x + y*8).
 * Unknown words receive a deterministic pseudo-random glyph. Words are case-insensitive.
 */
public final class WordData {
    private WordData() {}

    public static final String BALANCE = "Balance", BELIEVE = "Believe", CHANGE = "Change", CHAOS = "Chaos",
            CIVILIZATION = "Civilization", CONSTRAINT = "Constraint", CONTRADICT = "Contradict", CONTROL = "Control",
            CONVEY = "Convey", CREATIVITY = "Creativity", CYCLE = "Cycle", DEPENDENCE = "Dependence",
            DISCOVER = "Discover", DYNAMIC = "Dynamic", ELEVATE = "Elevate", ENCOURAGE = "Encourage",
            ENERGY = "Energy", ENTROPY = "Entropy", ETHEREAL = "Ethereal", EXIST = "Exist", EXPLORE = "Explore",
            FLOW = "Flow", FORCE = "Force", FORM = "Form", FUTURE = "Future", GROWTH = "Growth", HARMONY = "Harmony",
            HONOR = "Honor", INFINITE = "Infinite", INHIBIT = "Inhibit", INTELLIGENCE = "Intelligence", LOVE = "Love",
            MACHINE = "Machine", MERGE = "Merge", MOMENTUM = "Momentum", MOTION = "Motion", MUTUAL = "Mutual",
            NATURE = "Nature", NURTURE = "Nurture", POSSIBILITY = "Possibility", POWER = "Power", QUESTION = "Question",
            REBIRTH = "Rebirth", REMEMBER = "Remember", RESILIENCE = "Resilience", RESURRECT = "Resurrect",
            SACRIFICE = "Sacrifice", SOCIETY = "Society", SPUR = "Spur", STATIC = "Static", STIMULATE = "Stimulate",
            SURVIVAL = "Survival", SUSTAIN = "Sustain", SYSTEM = "System", TIME = "Time", TRADITION = "Tradition",
            TRANSFORM = "Transform", WEAVE = "Weave", WISDOM = "Wisdom", VOID = "Void",
            CHAIN = "Chain", CELESTIAL = "Celestial", IMAGE = "Image", TERRAIN = "Terrain", ORDER = "Order";

    // Suggested aliases
    public static final String MODIFIER = TRANSFORM, ENVIRONMENT = SURVIVAL, STRUCTURE = STATIC, ORE = MACHINE, SEA = FLOW;

    private static final Map<String, List<Integer>> WORDS = new HashMap<>();

    static {
        reg(NATURE, 5, 6, 8, 10, 11, 12, 15, 16, 17, 22);
        reg(LOVE, 4, 6, 9, 10, 11, 14, 16, 17, 19, 20);
        reg(FORCE, 4, 5, 8, 16, 17, 19);
        reg(TRANSFORM, 4, 5, 6, 8, 11, 14, 18, 20, 21);
        reg(CHANGE, 4, 7, 10, 11, 12, 16, 17, 18, 19, 21);
        reg(MACHINE, 4, 6, 7, 8, 14, 17, 18, 20, 21);
        reg(FUTURE, 4, 5, 8, 12, 14, 15, 18, 19, 20);
        reg(CYCLE, 4, 5, 7, 16, 17, 18);
        reg(MERGE, 4, 5, 6, 7, 16, 19, 20, 21);
        reg(DEPENDENCE, 4, 7, 8, 12, 13, 14, 15, 19, 20, 23);
        reg(VOID, 4, 5, 6, 16, 17, 18, 23);
        reg(ENERGY, 4, 5, 9, 13, 16, 18, 19, 22, 23);
        reg(MUTUAL, 7, 8, 11, 15, 17, 18, 21);
        reg(CONTRADICT, 6, 12, 13, 17, 18, 20, 21, 22, 23);
        reg(POWER, 5, 8, 9, 10, 22, 23);
        reg(POSSIBILITY, 4, 6, 8, 9, 10, 14, 16, 17, 19, 20, 23);
        reg(CONVEY, 4, 6, 9, 14, 15, 19, 22, 23);
        reg(ENCOURAGE, 5, 6, 9, 13, 14, 18, 20, 21, 22, 23);
        reg(WISDOM, 5, 7, 8, 9, 10, 13, 14, 19, 23);
        reg(DYNAMIC, 4, 5, 6, 12, 13, 17, 18, 22, 23);
        reg(INTELLIGENCE, 4, 9, 12, 13, 15, 18, 19, 20, 21);
        reg(ENTROPY, 4, 6, 9, 10, 13, 14, 15, 16, 17, 18, 23);
        reg(SOCIETY, 4, 5, 6, 7, 8, 11, 14, 15, 17, 18, 20, 21);
        reg(CHAOS, 4, 6, 9, 11, 12, 13, 18, 23);
        reg(GROWTH, 4, 5, 6, 12, 13, 15, 21, 22, 23);
        reg(CIVILIZATION, 4, 8, 9, 11, 14, 15, 17, 18, 20, 21, 23);
        reg(SPUR, 5, 9, 13, 14, 16, 20, 21, 22, 23);
        reg(INFINITE, 5, 6, 8, 19, 20, 21, 22, 23);
        reg(MOTION, 6, 7, 9, 16, 19, 20, 21);
        reg(HARMONY, 5, 7, 9, 10, 14, 15, 16, 19, 20, 21);
        reg(RESURRECT, 5, 7, 8, 9, 12, 13, 14, 15, 19, 23);
        reg(WEAVE, 8, 10, 13, 14, 20, 21, 22, 23);
        reg(REBIRTH, 4, 5, 6, 9, 10, 14, 16, 17, 19, 23);
        reg(CONTROL, 4, 6, 9, 10, 13, 19, 22, 23);
        reg(SACRIFICE, 5, 7, 9, 10, 14, 15, 17, 19);
        reg(TIME, 4, 5, 7, 12, 13, 19, 20, 21, 22, 23);
        reg(CONSTRAINT, 5, 6, 9, 11, 13, 20, 22, 23);
        reg(INHIBIT, 10, 11, 12, 15, 18, 19, 20, 23);
        reg(CREATIVITY, 6, 8, 9, 12, 13, 14, 18, 20, 21, 22, 23);
        reg(STIMULATE, 4, 5, 8, 9, 12, 13, 15, 20, 21, 22);
        reg(MOMENTUM, 6, 7, 12, 13, 17, 18, 19, 20, 23);
        reg(BALANCE, 4, 6, 9, 10, 16, 19);
        reg(RESILIENCE, 5, 6, 16, 17, 18, 19, 20, 23);
        reg(FLOW, 4, 7, 12, 13, 17, 18);
        reg(BELIEVE, 5, 6, 9, 10, 20, 22, 23);
        reg(TRADITION, 4, 6, 7, 8, 9, 14, 17, 18, 20);
        reg(NURTURE, 4, 7, 8, 12, 13, 15, 19, 21, 22);
        reg(HONOR, 4, 6, 9, 10, 18, 20, 21);
        reg(FORM, 4, 6, 8, 9, 10, 11, 13, 14, 16, 23);
        reg(QUESTION, 4, 11, 13, 19, 20, 21, 22, 23);
        reg(STATIC, 4, 6, 8, 11, 13, 14, 15, 19, 23);
        reg(EXIST, 4, 5, 6, 9, 13, 16, 17, 19, 20, 23);
        reg(ELEVATE, 5, 6, 9, 10, 19, 20, 21, 22, 23);
        reg(SURVIVAL, 5, 6, 8, 9, 16, 17, 18, 19, 23);
        reg(SYSTEM, 10, 12, 14, 15, 16, 20, 21, 22);
        reg(REMEMBER, 4, 6, 13, 16, 19, 20);
        reg(SUSTAIN, 6, 16, 19, 20, 21, 23);
        reg(ETHEREAL, 4, 6, 8, 13, 14, 16, 18, 21, 23);
        reg(DISCOVER, 4, 5, 8, 10, 12, 13, 14, 16, 22);
        reg(EXPLORE, 6, 8, 12, 13, 20, 21, 23);
        reg(CHAIN, 4, 5, 6, 7, 24, 25, 27, 33, 34, 35, 40, 41, 42, 43);
        reg(IMAGE, 7, 8, 9, 10, 11, 19, 21, 23);
        reg(CELESTIAL, 6, 8, 9, 10, 14, 18, 20, 21, 22, 23, 24);
        reg(TERRAIN, 6, 10, 12, 13, 16, 19, 24, 25, 27);
        reg(ORDER, 11, 14, 15, 17, 20, 21);
        for (int i = 0; i < 26; i++) {
            registerWord(Integer.toString(i), numeral(i));
        }
    }

    private static void reg(String word, Integer... components) {
        registerWord(word, List.of(components));
    }

    /** Numeral glyphs 0..25 used on notebook tabs. */
    private static List<Integer> numeral(int num) {
        if (num == 0) return List.of(1);
        if (num >= 25) return List.of(2);
        int first = num >= 20 ? 63 : num >= 15 ? 62 : num >= 10 ? 61 : num >= 5 ? 60 : 0;
        int second = num % 5 > 0 ? num % 5 + 55 : 0;
        if (first > 0) return second > 0 ? List.of(first, second) : List.of(first);
        return List.of(second);
    }

    public static synchronized void registerWord(String word, List<Integer> components) {
        WORDS.putIfAbsent(word.toLowerCase(Locale.ROOT), List.copyOf(components));
    }

    /** Component indices for a word; unknown words get a deterministic random glyph (3..12 components from 4..23). */
    public static synchronized List<Integer> components(String word) {
        String key = word.toLowerCase(Locale.ROOT);
        List<Integer> list = WORDS.get(key);
        if (list == null) {
            Random rand = new Random(key.hashCode());
            int count = rand.nextInt(10) + 3;
            Integer[] comps = new Integer[count];
            for (int i = 0; i < count; i++) comps[i] = rand.nextInt(20) + 4;
            list = List.of(comps);
            WORDS.put(key, list);
        }
        return list;
    }

    public static Map<String, List<Integer>> all() {
        return Collections.unmodifiableMap(WORDS);
    }
}

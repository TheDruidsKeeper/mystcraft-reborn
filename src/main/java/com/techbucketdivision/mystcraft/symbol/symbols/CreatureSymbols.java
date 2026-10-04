package com.techbucketdivision.mystcraft.symbol.symbols;

import com.techbucketdivision.mystcraft.api.symbol.AgeDirector;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.CreatureDifficulty;
import com.techbucketdivision.mystcraft.api.symbol.CreatureGroup;
import com.techbucketdivision.mystcraft.api.symbol.Modifier;
import com.techbucketdivision.mystcraft.api.symbol.SymbolCategory;
import com.techbucketdivision.mystcraft.api.symbol.logic.CreatureController;

import static com.techbucketdivision.mystcraft.api.symbol.WordData.*;

/**
 * Creature symbols (world-building plan §10). {@code creatures_passive / neutral / hostile} each register a
 * {@link CreatureController} for their group, taking the pending {@code rate}, {@code cap} and (hostile only)
 * {@code difficulty} modifiers; {@code creatures_none} silences every group. The modifiers themselves live in the
 * Modifiers category: {@code mod_rate_*}, {@code mod_cap_*}, {@code mod_difficulty_*}. None of them carries
 * instability - a dangerous Age comes from the Frenzy instability card instead.
 */
public final class CreatureSymbols {
    private CreatureSymbols() {}

    public static void registerAll() {
        BuiltinSymbols.add(new GroupSymbol(CreatureGroup.PASSIVE, "creatures_passive", NURTURE, NATURE, SOCIETY, HARMONY), SymbolCategory.CREATURES);
        BuiltinSymbols.add(new GroupSymbol(CreatureGroup.NEUTRAL, "creatures_neutral", NURTURE, NATURE, BALANCE, QUESTION), SymbolCategory.CREATURES);
        BuiltinSymbols.add(new GroupSymbol(CreatureGroup.HOSTILE, "creatures_hostile", NURTURE, NATURE, CHAOS, FORCE), SymbolCategory.CREATURES);
        BuiltinSymbols.add(new NoneSymbol(), SymbolCategory.CREATURES);

        BuiltinSymbols.add(new RateSymbol("mod_rate_none", 0f, VOID), SymbolCategory.MODIFIERS);
        BuiltinSymbols.add(new RateSymbol("mod_rate_sparse", 0.25f, INHIBIT), SymbolCategory.MODIFIERS);
        BuiltinSymbols.add(new RateSymbol("mod_rate_dense", 2f, STIMULATE), SymbolCategory.MODIFIERS);
        BuiltinSymbols.add(new RateSymbol("mod_rate_swarm", 4f, CHAOS), SymbolCategory.MODIFIERS);
        BuiltinSymbols.add(new CapSymbol("mod_cap_few", 0.25f, INHIBIT), SymbolCategory.MODIFIERS);
        BuiltinSymbols.add(new CapSymbol("mod_cap_many", 2f, STIMULATE), SymbolCategory.MODIFIERS);
        BuiltinSymbols.add(new CapSymbol("mod_cap_horde", 4f, INFINITE), SymbolCategory.MODIFIERS);
        BuiltinSymbols.add(new DifficultySymbol("mod_difficulty_easy", CreatureDifficulty.EASY, HARMONY), SymbolCategory.MODIFIERS);
        BuiltinSymbols.add(new DifficultySymbol("mod_difficulty_hard", CreatureDifficulty.HARD, FORCE), SymbolCategory.MODIFIERS);
        BuiltinSymbols.add(new DifficultySymbol("mod_difficulty_brutal", CreatureDifficulty.BRUTAL, SACRIFICE), SymbolCategory.MODIFIERS);
    }

    /** The resolved settings of one group. */
    public record Settings(CreatureGroup group, float rate, float capFactor, CreatureDifficulty difficulty) implements CreatureController {}

    /** One creature group: pops rate / cap (/ difficulty) and registers the controller. */
    public static final class GroupSymbol extends SimpleSymbol {
        private final CreatureGroup group;

        GroupSymbol(CreatureGroup group, String path, String... words) {
            super(path, 3, words);
            this.group = group;
        }

        public CreatureGroup group() {
            return group;
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            float rate = director.popModifier(Modifier.RATE).asFloat(1f);
            float cap = director.popModifier(Modifier.CAP).asFloat(1f);
            CreatureDifficulty difficulty = CreatureDifficulty.NORMAL;
            if (group == CreatureGroup.HOSTILE) {
                CreatureDifficulty popped = director.popModifier(Modifier.DIFFICULTY).as(CreatureDifficulty.class);
                if (popped != null) difficulty = popped;
            }
            director.registerInterface(new Settings(group, rate, cap, difficulty));
        }
    }

    /** No creatures at all: every group silenced. Takes no modifier. */
    public static final class NoneSymbol extends SimpleSymbol {
        NoneSymbol() {
            super("creatures_none", 3, NURTURE, NATURE, VOID, STATIC);
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            for (CreatureGroup group : CreatureGroup.values()) {
                director.registerInterface(new Settings(group, 0f, 0f, CreatureDifficulty.NORMAL));
            }
        }
    }

    public static final class RateSymbol extends SimpleSymbol {
        private final float factor;

        RateSymbol(String path, float factor, String fourthWord) {
            super(path, 0, MODIFIER, NURTURE, MOTION, fourthWord);
            this.factor = factor;
        }

        public float factor() {
            return factor;
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.setModifier(Modifier.RATE, new Modifier(factor));
        }
    }

    public static final class CapSymbol extends SimpleSymbol {
        private final float factor;

        CapSymbol(String path, float factor, String fourthWord) {
            super(path, 0, MODIFIER, NURTURE, CONSTRAINT, fourthWord);
            this.factor = factor;
        }

        public float factor() {
            return factor;
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.setModifier(Modifier.CAP, new Modifier(factor));
        }
    }

    public static final class DifficultySymbol extends SimpleSymbol {
        private final CreatureDifficulty difficulty;

        DifficultySymbol(String path, CreatureDifficulty difficulty, String fourthWord) {
            super(path, 0, MODIFIER, NURTURE, POWER, fourthWord);
            this.difficulty = difficulty;
        }

        public CreatureDifficulty difficulty() {
            return difficulty;
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.setModifier(Modifier.DIFFICULTY, new Modifier(difficulty));
        }
    }
}

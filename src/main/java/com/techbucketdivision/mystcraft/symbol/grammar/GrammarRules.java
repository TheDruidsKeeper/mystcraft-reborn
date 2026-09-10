package com.techbucketdivision.mystcraft.symbol.grammar;

import com.techbucketdivision.mystcraft.api.symbol.BlockCategory;

/**
 * Token names and the core (non-symbol) rules of the Age grammar (REQUIREMENTS §4.4.2). Token names are kept from the
 * original so add-ons and remapped data stay compatible.
 */
public final class GrammarRules {
    private GrammarRules() {}

    // Root and category tokens (GrammarData in the original)
    public static final String AGE = "Age";
    public static final String BIOME = "Biome";
    public static final String BIOME_LIST = "Biomes";
    public static final String BIOME_CONTROLLER = "BiomeController";
    public static final String LIGHTING = "Lighting";
    public static final String WEATHER = "Weather";
    public static final String TERRAIN = "TerrainGen";
    public static final String VISUAL = "Visual";
    public static final String FEATURE_SMALL = "FeatureSmall";
    public static final String FEATURE_MEDIUM = "FeatureMedium";
    public static final String FEATURE_LARGE = "FeatureLarge";
    public static final String EFFECT = "Effect";
    public static final String SUN = "Sun";
    public static final String MOON = "Moon";
    public static final String STARFIELD = "Starfield";
    public static final String DOODAD = "Doodad";

    public static final String BLOCK_TERRAIN = BlockCategory.TERRAIN.grammarToken();
    public static final String BLOCK_SOLID = BlockCategory.SOLID.grammarToken();
    public static final String BLOCK_STRUCTURE = BlockCategory.STRUCTURE.grammarToken();
    public static final String BLOCK_ORGANIC = BlockCategory.ORGANIC.grammarToken();
    public static final String BLOCK_CRYSTAL = BlockCategory.CRYSTAL.grammarToken();
    public static final String BLOCK_SEA = BlockCategory.SEA.grammarToken();
    public static final String BLOCK_FLUID = BlockCategory.FLUID.grammarToken();
    public static final String BLOCK_GAS = BlockCategory.GAS.grammarToken();
    public static final String BLOCK_ANY = BlockCategory.ANY.grammarToken();
    public static final String BLOCK_NONSOLID = "BLOCK_NONSOLID";

    public static final String SUNSET_UNCOMMON = "SunsetUncommon";
    public static final String SUNSET = "Sunset";
    public static final String SUNSET_EXT = "Sunset_Ext";

    public static final String ANGLE = "Angle";
    public static final String PERIOD = "Period";
    public static final String PHASE = "Phase";
    public static final String COLOR = "Color";
    public static final String GRADIENT = "Gradient";
    public static final String ANGLE_BASIC = "AngleBasic";
    public static final String PERIOD_BASIC = "PeriodBasic";
    public static final String PHASE_BASIC = "PhaseBasic";
    public static final String COLOR_BASIC = "ColorBasic";
    public static final String GRADIENT_BASIC = "GradientBasic";
    public static final String ANGLE_EXT = "Angle_Ext";
    public static final String PERIOD_EXT = "Period_Ext";
    public static final String PHASE_EXT = "Phase_Ext";
    public static final String COLOR_EXT = "Color_Ext";
    public static final String GRADIENT_EXT = "Gradient_Ext";

    // Sequence tokens
    public static final String SPAWNING_0 = "Spawning0";
    public static final String SUNS_0 = "Suns0";
    public static final String MOONS_0 = "Moons0";
    public static final String STARFIELDS_0 = "Starfields0";
    public static final String DOODADS_0 = "Doodads0";
    public static final String VISUALS_0 = "Visuals0";
    public static final String FEATURE_SMALLS_0 = "FeatureSmalls0";
    public static final String FEATURE_MEDIUMS_0 = "FeatureMediums0";
    public static final String FEATURE_LARGES_0 = "FeatureLarges0";
    public static final String EFFECTS_0 = "Effects0";

    public static final String BIOME_ADV = "BiomesAdv", BIOME_EXT = "BiomesExt";
    public static final String VISUAL_ADV = "VisualsAdv", VISUAL_EXT = "VisualsExt";
    public static final String FEATURE_LARGE_ADV = "FeatureLargeAdv", FEATURE_LARGE_EXT = "FeatureLargeExt";
    public static final String FEATURE_MEDIUM_ADV = "FeatureMediumAdv", FEATURE_MEDIUM_EXT = "FeatureMediumExt";
    public static final String FEATURE_SMALL_ADV = "FeatureSmallAdv", FEATURE_SMALL_EXT = "FeatureSmallExt";
    public static final String EFFECT_ADV = "EffectsAdv", EFFECT_EXT = "EffectsExt";
    public static final String SUN_ADV = "SunsAdv", SUN_EXT = "SunsExt";
    public static final String MOON_ADV = "MoonsAdv", MOON_EXT = "MoonsExt";
    public static final String STARFIELD_ADV = "StarfieldsAdv", STARFIELD_EXT = "StarfieldsExt";
    public static final String DOODAD_ADV = "DoodadsAdv", DOODAD_EXT = "DoodadsExt";
    public static final String ANGLE_ADV = "AngleAdv", PERIOD_ADV = "PeriodAdv", PHASE_ADV = "PhaseAdv",
            COLOR_ADV = "ColorAdv", GRADIENT_ADV = "GradientAdv";

    /** Registers every core rule of REQUIREMENTS §4.4.2. Called once by {@link Grammar#bootstrap()}. */
    static void registerCore() {
        rule(0, AGE, TERRAIN, BIOME_CONTROLLER, WEATHER, LIGHTING, SPAWNING_0, SUNS_0, MOONS_0, STARFIELDS_0, DOODADS_0,
                VISUALS_0, FEATURE_SMALLS_0, FEATURE_MEDIUMS_0, FEATURE_LARGES_0, EFFECTS_0);

        rule(10, SPAWNING_0);

        rule(1, BIOME_LIST, BIOME_ADV);
        rule(2, BIOME_ADV, BIOME_ADV, BIOME);
        rule(3, BIOME_ADV, BIOME);
        rule(null, BIOME_LIST, BIOME_EXT, BIOME);
        rule(null, BIOME_EXT, BIOME_EXT, BIOME_LIST);
        rule(1, BIOME_EXT);

        sequence(SUNS_0, SUN_ADV, SUN_EXT, SUN, 4, 2);
        sequence(MOONS_0, MOON_ADV, MOON_EXT, MOON, 2, 2);
        sequence(STARFIELDS_0, STARFIELD_ADV, STARFIELD_EXT, STARFIELD, 3, 2);
        rule(1, STARFIELD);
        sequence(DOODADS_0, DOODAD_ADV, DOODAD_EXT, DOODAD, 5, 2);
        rule(0, DOODAD);
        sequence(VISUALS_0, VISUAL_ADV, VISUAL_EXT, VISUAL, 3, 2);
        rule(1, VISUAL);
        sequence(FEATURE_LARGES_0, FEATURE_LARGE_ADV, FEATURE_LARGE_EXT, FEATURE_LARGE, 2, 2);
        rule(4, FEATURE_LARGE);
        sequence(FEATURE_MEDIUMS_0, FEATURE_MEDIUM_ADV, FEATURE_MEDIUM_EXT, FEATURE_MEDIUM, 2, 3);
        rule(4, FEATURE_MEDIUM);
        sequence(FEATURE_SMALLS_0, FEATURE_SMALL_ADV, FEATURE_SMALL_EXT, FEATURE_SMALL, 2, 4);
        rule(4, FEATURE_SMALL);
        sequence(EFFECTS_0, EFFECT_ADV, EFFECT_EXT, EFFECT, 3, 2);
        rule(1, EFFECT);

        rule(2, SUNSET_UNCOMMON);
        rule(3, SUNSET_UNCOMMON, SUNSET);
        rule(1, SUNSET);
        rule(null, SUNSET_EXT, SUNSET);
        rule(1, SUNSET_EXT);

        modifierSequence(ANGLE, ANGLE_ADV, ANGLE_EXT, ANGLE_BASIC, 2, 3);
        modifierSequence(PERIOD, PERIOD_ADV, PERIOD_EXT, PERIOD_BASIC, 2, 3);
        modifierSequence(PHASE, PHASE_ADV, PHASE_EXT, PHASE_BASIC, 2, 3);
        modifierSequence(COLOR, COLOR_ADV, COLOR_EXT, COLOR_BASIC, 2, 3);
        modifierSequence(GRADIENT, GRADIENT_ADV, GRADIENT_EXT, GRADIENT_BASIC, 2, 2);

        for (BlockCategory category : BlockCategory.all().values()) {
            rule(0, category.grammarToken());
        }
        rule(1, BLOCK_NONSOLID, BLOCK_FLUID);
        rule(2, BLOCK_NONSOLID, BLOCK_GAS);
    }

    /**
     * {@code seq0 -> adv (1); adv -> adv item (moreRank) | item (oneRank); seq0 -> ext item (null); ext -> ext item (null)
     * | eps (1)}.
     */
    private static void sequence(String seq0, String adv, String ext, String item, int moreRank, int oneRank) {
        rule(1, seq0, adv);
        rule(moreRank, adv, adv, item);
        rule(oneRank, adv, item);
        rule(null, seq0, ext, item);
        rule(null, ext, ext, item);
        rule(1, ext);
    }

    /**
     * {@code seq -> adv (1); adv -> adv basic (moreRank) | basic (oneRank); seq -> ext basic (null); ext -> seq (null) |
     * eps (1)}.
     */
    private static void modifierSequence(String seq, String adv, String ext, String basic, int moreRank, int oneRank) {
        rule(1, seq, adv);
        rule(moreRank, adv, adv, basic);
        rule(oneRank, adv, basic);
        rule(null, seq, ext, basic);
        rule(null, ext, seq);
        rule(1, ext);
    }

    private static void rule(Integer rank, String parent, String... values) {
        Grammar.registerRule(parent, rank, values);
    }
}

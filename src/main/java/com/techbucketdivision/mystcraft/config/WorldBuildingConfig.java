package com.techbucketdivision.mystcraft.config;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.symbol.SymbolCategory;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * World-building config ({@code mystcraft-worldbuilding.toml}): how an incomplete Descriptive Book is filled at the
 * first link (plan §2, §5). Every number of the fill tables is a key here. Weights are {@code "id=weight"} entries;
 * a weight of 0 removes the symbol from random fill; ids without a namespace are {@code mystcraft:}.
 */
public final class WorldBuildingConfig {
    private WorldBuildingConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    /** Per-category fill settings. */
    public static final class Category {
        public final ModConfigSpec.IntValue chance;
        public final ModConfigSpec.IntValue min;
        public final ModConfigSpec.IntValue max;
        public final ModConfigSpec.ConfigValue<List<? extends String>> defaults;
        public final ModConfigSpec.ConfigValue<List<? extends String>> weights;

        Category(SymbolCategory category, int chance, List<String> defaults, List<String> weights) {
            B.push("fill").push(category.id());
            this.chance = B.comment(category.isRequired() ? "Always filled when empty (required category)" : "Chance in percent that an empty category gets random symbols")
                    .defineInRange("chance", chance, 0, 100);
            this.min = B.comment("Minimum number of pages when the category is filled").defineInRange("min", category.defaultMin(), 0, 16);
            this.max = B.comment("Maximum number of pages (defaults included)").defineInRange("max", category.defaultMax(), 0, 16);
            this.defaults = B.comment("Symbols always added when the category is empty (what 'normal Minecraft' means here)")
                    .defineListAllowEmpty("defaults", defaults, () -> "", o -> o instanceof String);
            this.weights = B.comment("Random pick weights, \"id=weight\"; 0 removes the symbol")
                    .defineListAllowEmpty("weights", weights, () -> "", o -> o instanceof String);
            B.pop(2);
        }
    }

    public static final ModConfigSpec.IntValue INSTABILITY_BUDGET = B.comment(
            "Largest total instability the discovered (auto-filled) symbols may add; picks over budget are re-rolled or dropped")
            .defineInRange("fill.instabilityBudget", 500, -100_000, 100_000);
    public static final ModConfigSpec.IntValue STAR_FISSURE_CHANCE = B.comment("Chance in percent that an Age gets a Star Fissure (a way home)")
            .defineInRange("fill.starFissureChance", 10, 0, 100);

    public static final Map<SymbolCategory, Category> CATEGORIES = new EnumMap<>(SymbolCategory.class);

    static {
        CATEGORIES.put(SymbolCategory.TERRAIN, new Category(SymbolCategory.TERRAIN, 100, List.of(),
                List.of("terrain_normal=70", "terrain_amplified=12", "terrain_flat=8", "terrain_nether=6", "terrain_end=4", "terrain_void=0")));
        CATEGORIES.put(SymbolCategory.BIOME_LAYOUT, new Category(SymbolCategory.BIOME_LAYOUT, 100, List.of(),
                List.of("biome_native=45", "biome_medium=15", "biome_large=12", "biome_small=10", "biome_huge=6", "biome_tiny=4",
                        "biome_tiled=4", "biome_grid=2", "biome_single=2")));
        CATEGORIES.put(SymbolCategory.BIOMES, new Category(SymbolCategory.BIOMES, 100, List.of(), List.of()));
        CATEGORIES.put(SymbolCategory.LIGHTING, new Category(SymbolCategory.LIGHTING, 100, List.of(),
                List.of("lighting_normal=85", "lighting_bright=10", "lighting_dark=5")));
        CATEGORIES.put(SymbolCategory.CELESTIALS, new Category(SymbolCategory.CELESTIALS, 100, List.of(),
                List.of("sun_normal=96", "sun_dark=4", "moon_normal=100", "moon_dark=0", "stars_normal=60", "stars_twinkle=25",
                        "stars_end_sky=5", "stars_dark=0", "rainbow=8")));
        CATEGORIES.put(SymbolCategory.SKY_COLORS, new Category(SymbolCategory.SKY_COLORS, 25, List.of(),
                List.of("color_sky=40", "color_sky_night=40", "color_fog=40", "color_cloud=40", "no_horizon=5")));
        CATEGORIES.put(SymbolCategory.WORLD_COLORS, new Category(SymbolCategory.WORLD_COLORS, 15, List.of(),
                List.of("color_foliage=50", "color_grass=50", "color_water=50")));
        CATEGORIES.put(SymbolCategory.WEATHER, new Category(SymbolCategory.WEATHER, 35, List.of(),
                List.of("weather_fast=25", "weather_slow=25", "weather_off=15", "weather_cloudy=15", "weather_rain=8", "weather_snow=8",
                        "weather_on=3", "weather_storm=1")));
        CATEGORIES.put(SymbolCategory.STRUCTURES, new Category(SymbolCategory.STRUCTURES, 40, List.of(),
                List.of("villages=30", "mineshafts=25", "dungeons=25", "strongholds=10", "ravines=10", "nether_fortress=5", "vault=15")));
        CATEGORIES.put(SymbolCategory.FEATURES, new Category(SymbolCategory.FEATURES, 50, List.of("caves", "lakes_surface"),
                List.of("lakes_deep=10", "huge_trees=10", "floating_islands=8", "skylands=6", "tendrils=5", "crystal_formations=5",
                        "obelisks=4", "spheres=3", "spikes=3", "dense_ores=2")));
        CATEGORIES.put(SymbolCategory.EFFECTS, new Category(SymbolCategory.EFFECTS, 12, List.of(),
                List.of("env_accelerated=40", "env_lightning=25", "pvp_off=20", "env_meteors=8", "env_explosions=4", "env_scorched=3")));
        CATEGORIES.put(SymbolCategory.CREATURES, new Category(SymbolCategory.CREATURES, 15, List.of(),
                List.of("creatures_passive=30", "creatures_neutral=20", "creatures_hostile=45", "creatures_none=5")));
    }

    // --- biomes ----------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_COUNT_WEIGHTS = B.comment("How many biomes a filled Age gets, \"count=weight\"")
            .defineListAllowEmpty("fill.biomes.countWeights", List.of("1=35", "2=35", "3=20", "4=10"), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.IntValue BIOME_OVERWORLD_WEIGHT = B.comment("Weight of every overworld biome").defineInRange("fill.biomes.overworldWeight", 10, 0, 1000);
    public static final ModConfigSpec.IntValue BIOME_NETHER_WEIGHT = B.comment("Weight of every nether biome (only with Nether terrain)").defineInRange("fill.biomes.netherWeight", 1, 0, 1000);
    public static final ModConfigSpec.IntValue BIOME_END_WEIGHT = B.comment("Weight of every end biome (only with End terrain)").defineInRange("fill.biomes.endWeight", 1, 0, 1000);

    // --- celestials ------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOON_COUNT_WEIGHTS = B.comment("Number of moons, \"count=weight\"")
            .defineListAllowEmpty("fill.celestials.moonCountWeights", List.of("0=20", "1=60", "2=20"), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STARFIELD_COUNT_WEIGHTS = B.comment("Number of starfields, \"count=weight\"")
            .defineListAllowEmpty("fill.celestials.starfieldCountWeights", List.of("0=10", "1=75", "2=15"), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.IntValue CELESTIAL_DIRECTION_CHANCE = B.comment("Chance in percent that a celestial gets a direction modifier")
            .defineInRange("fill.celestials.modifierChance.direction", 30, 0, 100);
    public static final ModConfigSpec.IntValue CELESTIAL_PHASE_CHANCE = B.comment("Chance in percent that a celestial gets a phase modifier")
            .defineInRange("fill.celestials.modifierChance.phase", 30, 0, 100);
    public static final ModConfigSpec.IntValue CELESTIAL_SUNSET_CHANCE = B.comment("Chance in percent that a sun or moon gets a sunset colour")
            .defineInRange("fill.celestials.modifierChance.sunset", 20, 0, 100);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CELESTIAL_LENGTH_WEIGHTS = B.comment("Length modifier of a celestial, \"none|mod_half|mod_double|mod_zero=weight\"")
            .defineListAllowEmpty("fill.celestials.lengthWeights", List.of("none=70", "mod_half=10", "mod_double=15", "mod_zero=5"), () -> "", o -> o instanceof String);

    // --- colours ---------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue COLOR_GRADIENT_CHANCE = B.comment("Chance in percent that a colour page gets a two-colour gradient instead of one colour")
            .defineInRange("fill.colors.gradientChance", 25, 0, 100);

    // --- materials -------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue MATERIAL_COMMON_CHANCE = B.comment("Chance in percent that a terrain/feature page gets a common block attached")
            .defineInRange("fill.materials.commonChance", 15, 0, 100);
    public static final ModConfigSpec.IntValue MATERIAL_EXOTIC_CHANCE = B.comment("Chance in percent that a terrain/feature page gets an exotic block attached")
            .defineInRange("fill.materials.exoticChance", 5, 0, 100);
    public static final ModConfigSpec.IntValue NO_SEA_CHANCE = B.comment("Chance in percent that the terrain gets No Sea attached")
            .defineInRange("fill.materials.noSeaChance", 3, 0, 100);

    // --- creatures -------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CREATURE_RATE_WEIGHTS = B.comment("Spawn-rate modifier of a creature group, \"none|mod_rate_none|mod_rate_sparse|mod_rate_dense|mod_rate_swarm=weight\" (none = no modifier)")
            .defineListAllowEmpty("fill.creatures.rateWeights", List.of("none=50", "mod_rate_sparse=20", "mod_rate_dense=20", "mod_rate_swarm=5", "mod_rate_none=5"), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CREATURE_CAP_WEIGHTS = B.comment("Population-cap modifier of a creature group, \"none|mod_cap_few|mod_cap_many|mod_cap_horde=weight\"")
            .defineListAllowEmpty("fill.creatures.capWeights", List.of("none=60", "mod_cap_few=15", "mod_cap_many=20", "mod_cap_horde=5"), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CREATURE_DIFFICULTY_WEIGHTS = B.comment("Difficulty modifier of the hostile group, \"none|mod_difficulty_easy|mod_difficulty_hard|mod_difficulty_brutal=weight\"")
            .defineListAllowEmpty("fill.creatures.difficultyWeights", List.of("none=60", "mod_difficulty_easy=20", "mod_difficulty_hard=15", "mod_difficulty_brutal=5"), () -> "", o -> o instanceof String);

    public static final ModConfigSpec SPEC = B.build();

    /** Parses {@code "id=weight"} entries into an ordered map (ids without namespace get {@code mystcraft:}). */
    public static Map<Identifier, Integer> parseWeights(List<? extends String> entries) {
        Map<Identifier, Integer> out = new LinkedHashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            Identifier id = parseId(entry.substring(0, eq).trim());
            if (id == null) continue;
            try {
                out.put(id, Math.max(0, Integer.parseInt(entry.substring(eq + 1).trim())));
            } catch (NumberFormatException e) {
                Mystcraft.LOGGER.warn("[blueprint] ignoring weight entry '{}'", entry);
            }
        }
        return out;
    }

    /** Parses {@code "key=weight"} entries with plain string keys (counts, "none"). */
    public static Map<String, Integer> parseKeyedWeights(List<? extends String> entries) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            try {
                out.put(entry.substring(0, eq).trim(), Math.max(0, Integer.parseInt(entry.substring(eq + 1).trim())));
            } catch (NumberFormatException e) {
                Mystcraft.LOGGER.warn("[blueprint] ignoring weight entry '{}'", entry);
            }
        }
        return out;
    }

    public static Identifier parseId(String id) {
        return id.contains(":") ? Identifier.tryParse(id) : Identifier.tryParse(Mystcraft.MOD_ID + ":" + id);
    }
}

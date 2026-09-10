package com.techbucketdivision.mystcraft.symbol;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps legacy (1.7–1.12, mixed-case) symbol ids to the modern lower-case ids (REQUIREMENTS §19.4 plus the id table of
 * the implementation contract). A legacy id may expand to several modern ids (one page becomes several).
 * <p>
 * Resolution is recursive (the colour-family targets of the original table are themselves legacy names), unlike the
 * original which relied on repeated remapping passes.
 */
public final class SymbolRemapper {
    private SymbolRemapper() {}

    private static final int MAX_DEPTH = 8;

    /** Legacy name (lower-cased) -> legacy target names, as in the original {@code SymbolRemappings}. */
    private static final Map<String, List<String>> LEGACY_TABLE = new LinkedHashMap<>();
    /** Legacy symbol name (lower-cased) -> modern id path. */
    private static final Map<String, String> LEGACY_TO_MODERN = new HashMap<>();
    /** Legacy numeric biome id -> modern biome path. */
    private static final Map<Integer, String> LEGACY_BIOME_IDS = new HashMap<>();
    /** Legacy biome names (pre-numeric symbols such as "Swampland") -> modern biome path. */
    private static final Map<String, String> LEGACY_BIOME_NAMES = new HashMap<>();
    /** Legacy block path -> modern block path (1.12 -> flattening). */
    private static final Map<String, String> LEGACY_BLOCKS = new HashMap<>();

    static {
        initModernNames();
        initLegacyTable();
        initBiomes();
        initBlocks();
    }

    // --- public API ----------------------------------------------------------------------------------------------

    /** True if the id is not a modern id and would be changed by {@link #remap(Identifier)}. */
    public static boolean hasRemapping(Identifier id) {
        if (id.getNamespace().equals("minecraft")) return true;
        String path = id.getPath();
        if (path.startsWith("modmat_")) return true;
        String key = path.toLowerCase(Locale.ROOT);
        if (LEGACY_TABLE.containsKey(key)) return true;
        String modern = LEGACY_TO_MODERN.get(key);
        return modern != null && !modern.equals(path);
    }

    /** Remaps an id (legacy or modern); a modern id maps to itself. */
    public static List<Identifier> remap(Identifier id) {
        return remap(id.toString());
    }

    /**
     * Remaps a raw legacy id string ({@code "TerrainNormal"}, {@code "mystcraft:BioConSingle"},
     * {@code "ModMat_minecraft:stone_0"}, {@code "modmat_minecraft:stone_0"}...). Unknown names are converted to snake case.
     */
    public static List<Identifier> remap(String rawId) {
        List<String> legacy = new ArrayList<>();
        expand(rawId, legacy, 0);
        List<Identifier> out = new ArrayList<>(legacy.size());
        for (String s : legacy) out.add(legacyToModern(s));
        return Collections.unmodifiableList(out);
    }

    private static void expand(String rawId, List<String> out, int depth) {
        String normalised = normaliseLegacy(rawId);
        List<String> targets = LEGACY_TABLE.get(normalised.toLowerCase(Locale.ROOT));
        if (targets == null || depth >= MAX_DEPTH) {
            out.add(normalised);
            return;
        }
        for (String t : targets) expand(t, out, depth + 1);
    }

    /** Converts one legacy name (no further table expansion) to its modern id. */
    public static Identifier legacyToModern(String legacyId) {
        String name = normaliseLegacy(legacyId);
        String lower = name.toLowerCase(Locale.ROOT);
        // Already a modern id?
        Identifier asIs = Identifier.tryParse(legacyId);
        if (asIs != null && asIs.getNamespace().equals(Mystcraft.MOD_ID) && !hasRemapping(asIs)) return asIs;

        String modern = LEGACY_TO_MODERN.get(lower);
        if (modern != null) return MystIds.id(modern);

        if (lower.startsWith("modmat_")) return MystIds.id(blockPath(name.substring("modmat_".length())));

        if (lower.startsWith("biome")) {
            String rest = lower.substring("biome".length());
            try {
                int num = Integer.parseInt(rest);
                String biome = LEGACY_BIOME_IDS.get(num);
                if (biome != null) return MystIds.id("biome_minecraft_" + biome);
            } catch (NumberFormatException ignored) {
            }
        }
        String biome = LEGACY_BIOME_NAMES.get(lower);
        if (biome != null) return MystIds.id("biome_minecraft_" + biome);

        return MystIds.id(snakeCase(name));
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    /**
     * Strips the namespace and turns the old {@code modmat_<modid>:<path>} domain form into {@code modmat_<path>}
     * (the block namespace is kept as a prefix when it is not minecraft/mystcraft).
     */
    private static String normaliseLegacy(String raw) {
        String s = raw.trim();
        int colon = s.indexOf(':');
        if (colon < 0) return s;
        String domain = s.substring(0, colon);
        String path = s.substring(colon + 1);
        String domainLower = domain.toLowerCase(Locale.ROOT);
        if (domainLower.startsWith("modmat_")) {
            String blockNs = domainLower.substring("modmat_".length());
            return "modmat_" + nsPrefix(blockNs) + path;
        }
        if (path.toLowerCase(Locale.ROOT).startsWith("modmat_") && !domainLower.equals("minecraft") && !domainLower.equals(Mystcraft.MOD_ID)) {
            return "modmat_" + nsPrefix(domainLower) + path.substring("modmat_".length());
        }
        return path;
    }

    private static String nsPrefix(String ns) {
        return ns.equals("minecraft") || ns.equals(Mystcraft.MOD_ID) || ns.isEmpty() ? "" : MystIds.pathSafe(ns) + "_";
    }

    /** {@code [<ns>_]<blockpath>_<meta>} (legacy) -> {@code block_<modern block path>}. */
    private static String blockPath(String legacy) {
        String s = legacy;
        int colon = s.indexOf(':');
        String ns = "";
        if (colon >= 0) {
            ns = nsPrefix(s.substring(0, colon).toLowerCase(Locale.ROOT));
            s = s.substring(colon + 1);
        }
        s = s.toLowerCase(Locale.ROOT);
        if (s.startsWith("tile.")) s = s.substring("tile.".length());
        int meta = 0;
        int us = s.lastIndexOf('_');
        if (us > 0) {
            try {
                meta = Integer.parseInt(s.substring(us + 1));
                s = s.substring(0, us);
            } catch (NumberFormatException ignored) {
            }
        }
        String modern = LEGACY_BLOCKS.get(s + "#" + meta);
        if (modern == null) modern = LEGACY_BLOCKS.get(s);
        if (modern == null) modern = MystIds.pathSafe(s.replace('.', '_'));
        return "block_" + ns + modern;
    }

    /** CamelCase / spaced names to snake_case. */
    public static String snakeCase(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 8);
        char prev = 0;
        for (char c : name.toCharArray()) {
            if (c == ' ' || c == '-' || c == '.' || c == ':') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') sb.append('_');
            } else if (Character.isUpperCase(c)) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_' && (Character.isLowerCase(prev) || Character.isDigit(prev))) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
            }
            prev = c;
        }
        return sb.toString();
    }

    // --- tables --------------------------------------------------------------------------------------------------

    private static void modern(String legacy, String modern) {
        LEGACY_TO_MODERN.put(legacy.toLowerCase(Locale.ROOT), modern);
    }

    private static void initModernNames() {
        String[][] names = {
                {"ColorCloud", "color_cloud"}, {"ColorCloudNat", "color_cloud_natural"}, {"ColorFog", "color_fog"},
                {"ColorFogNat", "color_fog_natural"}, {"ColorFoliage", "color_foliage"}, {"ColorFoliageNat", "color_foliage_natural"},
                {"ColorGrass", "color_grass"}, {"ColorGrassNat", "color_grass_natural"}, {"ColorSky", "color_sky"},
                {"ColorSkyNat", "color_sky_natural"}, {"ColorSkyNight", "color_sky_night"}, {"ColorWater", "color_water"},
                {"ColorWaterNat", "color_water_natural"}, {"NoHorizon", "no_horizon"}, {"Rainbow", "rainbow"},
                {"SunNormal", "sun_normal"}, {"SunDark", "sun_dark"}, {"MoonNormal", "moon_normal"}, {"MoonDark", "moon_dark"},
                {"StarsNormal", "stars_normal"}, {"StarsTwinkle", "stars_twinkle"}, {"StarsEndSky", "stars_end_sky"}, {"StarsDark", "stars_dark"},
                {"LightingNormal", "lighting_normal"}, {"LightingBright", "lighting_bright"}, {"LightingDark", "lighting_dark"},
                {"WeatherNorm", "weather_normal"}, {"WeatherNormal", "weather_normal"}, {"WeatherFast", "weather_fast"},
                {"WeatherSlow", "weather_slow"}, {"WeatherOff", "weather_off"}, {"WeatherOn", "weather_on"},
                {"WeatherAlways", "weather_on"}, {"WeatherCloudy", "weather_cloudy"}, {"WeatherRain", "weather_rain"},
                {"WeatherSnow", "weather_snow"}, {"WeatherStorm", "weather_storm"},
                {"BioConNative", "biome_native"}, {"BioConSingle", "biome_single"}, {"BioConTiled", "biome_tiled"},
                {"BioConGrid", "biome_grid"}, {"BioConTiny", "biome_tiny"}, {"BioConSmall", "biome_small"},
                {"BioConMedium", "biome_medium"}, {"BioConLarge", "biome_large"}, {"BioConHuge", "biome_huge"},
                {"TerrainNormal", "terrain_normal"}, {"TerrainAmplified", "terrain_amplified"}, {"TerrainFlat", "terrain_flat"},
                {"TerrainNether", "terrain_nether"}, {"TerrainEnd", "terrain_end"}, {"TerrainVoid", "terrain_void"},
                {"Caves", "caves"}, {"Tendrils", "tendrils"}, {"Skylands", "skylands"}, {"FloatIslands", "floating_islands"},
                {"HugeTrees", "huge_trees"}, {"DenseOres", "dense_ores"}, {"FeatureLargeDummy", "feature_large_dummy"},
                {"Villages", "villages"}, {"Strongholds", "strongholds"}, {"Mineshafts", "mineshafts"}, {"NetherFort", "nether_fortress"},
                {"Ravines", "ravines"}, {"Dungeons", "dungeons"}, {"TerModSpheres", "spheres"}, {"GenSpikes", "spikes"},
                {"FeatureMediumDummy", "feature_medium_dummy"}, {"LakesSurface", "lakes_surface"}, {"LakesDeep", "lakes_deep"},
                {"Obelisks", "obelisks"}, {"CryForm", "crystal_formations"}, {"StarFissure", "star_fissure"},
                {"FeatureSmallDummy", "feature_small_dummy"}, {"EnvAccel", "env_accelerated"}, {"EnvExplosions", "env_explosions"},
                {"EnvLightning", "env_lightning"}, {"EnvMeteor", "env_meteors"}, {"EnvScorch", "env_scorched"},
                {"PvPOff", "pvp_off"}, {"NoSea", "no_sea"}, {"ModClear", "clear_modifiers"},
                {"ModNorth", "mod_north"}, {"ModEast", "mod_east"}, {"ModSouth", "mod_south"}, {"ModWest", "mod_west"},
                {"ModEnd", "mod_end"}, {"ModRising", "mod_rising"}, {"ModNoon", "mod_noon"}, {"ModSetting", "mod_setting"},
                {"ModZero", "mod_zero"}, {"ModHalf", "mod_half"}, {"ModFull", "mod_full"}, {"ModDouble", "mod_double"},
                {"ModGradient", "mod_gradient"}, {"ColorHorizon", "color_horizon"},
                {"ModColorMaroon", "mod_color_maroon"}, {"ModColorRed", "mod_color_red"}, {"ModColorOlive", "mod_color_olive"},
                {"ModColorYellow", "mod_color_yellow"}, {"ModColorDarkGreen", "mod_color_dark_green"}, {"ModColorGreen", "mod_color_green"},
                {"ModColorTeal", "mod_color_teal"}, {"ModColorCyan", "mod_color_cyan"}, {"ModColorNavy", "mod_color_navy"},
                {"ModColorBlue", "mod_color_blue"}, {"ModColorPurple", "mod_color_purple"}, {"ModColorMagenta", "mod_color_magenta"},
                {"ModColorBlack", "mod_color_black"}, {"ModColorGrey", "mod_color_grey"}, {"ModColorSilver", "mod_color_silver"},
                {"ModColorWhite", "mod_color_white"},
        };
        for (String[] pair : names) modern(pair[0], pair[1]);
    }

    private static void legacy(String from, String... to) {
        LEGACY_TABLE.put(from.toLowerCase(Locale.ROOT), List.of(to));
    }

    private static void initLegacyTable() {
        String[] chromatic = {"ModBlack", "ModRed", "ModRed", "ModGradient", "ModBlack", "ModGreen", "ModGreen", "ModGradient",
                "ModBlack", "ModBlue", "ModBlue", "ModGradient"};
        colourFamily("Fog", "ColorFog", chromatic, "ModWhite");
        colourFamily("Cloud", "ColorCloud", chromatic, "ModWhite");
        colourFamily("Sky", "ColorSky", chromatic, "ModBlue");
        colourFamily("Sunset", "ColorHorizon", chromatic, null);
        legacy("ModGradient_HERE", "ModGradient", "ColorSky");

        legacy("ModMat_tile.stone", "ModMat_minecraft:stone_0");
        legacy("ModMat_tile.lava", "ModMat_minecraft:flowing_lava_0");
        legacy("ModMat_tile.water", "ModMat_minecraft:flowing_water_0");
        legacy("modmat_flowing_water_0", "modmat_water_0");

        String[] colours = {"Maroon", "Red", "Olive", "Yellow", "Green", "Teal", "Cyan", "Navy", "Blue", "Purple", "Magenta",
                "Black", "Grey", "Silver", "White"};
        for (String c : colours) legacy("Mod" + c, "ModColor" + c);
        legacy("ModDark Green", "ModColorDarkGreen");
        legacy("ModDarkGreen", "ModColorDarkGreen");

        legacy("LavaLakes", "ModMat_tile.lava", "LakesDeep");
        legacy("Lakes", "ModMat_tile.water", "LakesSurface");
        legacy("CryFormCry", "ModMat_tile.myst.crystal", "CryForm");
        legacy("CryFormGlow", "ModMat_tile.myst.lightgem", "CryForm");
        legacy("CryFormQuartz", "ModMat_tile.myst.netherquartz", "CryForm");
        legacy("Standard Terrain", "TerrainNormal");
        legacy("Star Fissure", "StarFissure");
        legacy("Rain", "WeatherRain");
        legacy("Snow", "WeatherSnow");
        legacy("Huge Trees", "HugeTrees");
        legacy("NormalStars", "StarsNormal");
        legacy("Single Biome", "BioConSingle");
        legacy("Checkerboard Biomes", "BioConTiled");
        legacy("BiomeControllerNative", "BioConNative");
        legacy("Lava Lakes", "LavaLakes");
        legacy("WeatherSun", "WeatherOff");
        legacy("Standard Lighting", "LightingNormal");
        legacy("Storm", "WeatherStorm");
        legacy("Fog", "ColorFog");
        legacy("ModFluid_tile.lava", "ModMat_tile.lava");
        legacy("ModFluid_tile.water", "ModMat_tile.water");
        legacy("ModFluidtile.water", "ModMat_tile.water");
        legacy("ModFluidtile.lava", "ModMat_tile.lava");
        legacy("ModLavaSea", "ModMat_tile.lava");
        legacy("ModNetherTerrain", "ModMat_tile.hellrock");
        legacy("ModMattile.hellrock", "ModMat_tile.hellrock");
        legacy("ModMattile.whiteStone", "ModMat_tile.whiteStone");
        legacy("ModMattile.oreDiamond", "ModMat_tile.oreDiamond");
        legacy("TendrilsIce", "ModMat_tile.ice", "Tendrils");
        legacy("WoodCaves", "Tendrils");
        legacy("SkyDropDark", "StarsDark");
        legacy("FTime", "ModHalf", "SunNormal", "ModHalf", "MoonNormal");
        legacy("STime", "ModDouble", "SunNormal", "ModDouble", "MoonNormal");
        legacy("NTime", "ModFull", "SunNormal", "ModFull", "MoonNormal");
        legacy("Dusk", "ModZero", "ModSetting", "SunNormal", "ModZero", "MoonNormal");
        legacy("Night", "SunDark", "ModZero", "MoonNormal");
        legacy("Day", "MoonDark", "ModZero", "ModNoon", "SunNormal");
        legacy("Heavy Resources", "DenseOres");
        legacy("SunsetNormal", "SunsetRed");
        legacy("CloudNormal", "CloudWhite");
        legacy("Normal Sunset Colors", "SunsetRed");
        legacy("NativeBiomeController", "BioConLarge");
        legacy("Flat Sea", "TerrainFlat");
        legacy("Sky Islands", "Skylands");
        legacy("Tree Age", "Huge Trees", "TerrainFlat", "Swampland", "BioConSingle");
        legacy("DefaultBiome", "BioConSingle");
        legacy("DefaultLighting", "Standard Lighting");
        legacy("DefaultSunrise", "Normal Sunset Colors");
        legacy("DefaultTerrain", "Standard Terrain");
        legacy("Flat", "TerrainFlat");
        legacy("Void", "TerrainVoid");
    }

    /** {@code <family>Chromatic/Red/Green/Blue/Black/White[/Normal]} -> colour symbols followed by the target. */
    private static void colourFamily(String family, String target, String[] chromatic, @Nullable String normalColour) {
        List<String> chroma = new ArrayList<>(List.of(chromatic));
        chroma.add(target);
        LEGACY_TABLE.put((family + "Chromatic").toLowerCase(Locale.ROOT), List.copyOf(chroma));
        for (String c : new String[] {"Red", "Green", "Blue", "Black", "White"}) {
            legacy(family + c, "Mod" + c, target);
        }
        if (normalColour != null) legacy(family + "Normal", normalColour, target);
    }

    private static void initBiomes() {
        String[] byId = {"ocean", "plains", "desert", "windswept_hills", "forest", "taiga", "swamp", "river", "nether_wastes",
                "the_end", "frozen_ocean", "frozen_river", "snowy_plains", "snowy_slopes", "mushroom_fields", "mushroom_fields",
                "beach", "desert", "forest", "taiga", "windswept_hills", "jungle", "jungle", "sparse_jungle", "deep_ocean",
                "stony_shore", "snowy_beach", "birch_forest", "birch_forest", "dark_forest", "snowy_taiga", "snowy_taiga",
                "old_growth_pine_taiga", "old_growth_pine_taiga", "windswept_forest", "savanna", "savanna_plateau", "badlands",
                "wooded_badlands", "badlands"};
        for (int i = 0; i < byId.length; i++) LEGACY_BIOME_IDS.put(i, byId[i]);
        String[][] names = {{"swampland", "swamp"}, {"plains", "plains"}, {"forest", "forest"}, {"desert", "desert"},
                {"taiga", "taiga"}, {"jungle", "jungle"}, {"ocean", "ocean"}, {"river", "river"}, {"beach", "beach"},
                {"extreme hills", "windswept_hills"}, {"extremehills", "windswept_hills"}, {"hell", "nether_wastes"},
                {"sky", "the_end"}, {"ice plains", "snowy_plains"}, {"iceplains", "snowy_plains"}, {"mushroomisland", "mushroom_fields"},
                {"mushroom island", "mushroom_fields"}, {"savanna", "savanna"}, {"mesa", "badlands"}, {"roofed forest", "dark_forest"},
                {"roofedforest", "dark_forest"}, {"birch forest", "birch_forest"}, {"birchforest", "birch_forest"}};
        for (String[] n : names) LEGACY_BIOME_NAMES.put(n[0], n[1]);
    }

    private static void initBlocks() {
        LEGACY_BLOCKS.put("stone#0", "stone");
        LEGACY_BLOCKS.put("stone#1", "granite");
        LEGACY_BLOCKS.put("stone#2", "polished_granite");
        LEGACY_BLOCKS.put("stone#3", "diorite");
        LEGACY_BLOCKS.put("stone#4", "polished_diorite");
        LEGACY_BLOCKS.put("stone#5", "andesite");
        LEGACY_BLOCKS.put("stone#6", "polished_andesite");
        LEGACY_BLOCKS.put("log#0", "oak_log");
        LEGACY_BLOCKS.put("log#1", "spruce_log");
        LEGACY_BLOCKS.put("log#2", "birch_log");
        LEGACY_BLOCKS.put("log#3", "jungle_log");
        LEGACY_BLOCKS.put("log2#0", "acacia_log");
        LEGACY_BLOCKS.put("log2#1", "dark_oak_log");
        LEGACY_BLOCKS.put("flowing_water", "water");
        LEGACY_BLOCKS.put("flowing_lava", "lava");
        LEGACY_BLOCKS.put("quartz_ore", "nether_quartz_ore");
        LEGACY_BLOCKS.put("nether_brick", "nether_bricks");
        LEGACY_BLOCKS.put("snow", "snow_block");
        LEGACY_BLOCKS.put("hellrock", "netherrack");
        LEGACY_BLOCKS.put("whitestone", "end_stone");
        LEGACY_BLOCKS.put("orediamond", "diamond_ore");
        LEGACY_BLOCKS.put("oregold", "gold_ore");
        LEGACY_BLOCKS.put("oreiron", "iron_ore");
        LEGACY_BLOCKS.put("orecoal", "coal_ore");
        LEGACY_BLOCKS.put("oreredstone", "redstone_ore");
        LEGACY_BLOCKS.put("orelapis", "lapis_ore");
        LEGACY_BLOCKS.put("oreemerald", "emerald_ore");
        LEGACY_BLOCKS.put("lightgem", "glowstone");
        LEGACY_BLOCKS.put("netherquartz", "nether_quartz_ore");
        LEGACY_BLOCKS.put("myst.crystal", "crystal");
        LEGACY_BLOCKS.put("myst.lightgem", "glowstone");
        LEGACY_BLOCKS.put("myst.netherquartz", "nether_quartz_ore");
        LEGACY_BLOCKS.put("blockcrystal", "crystal");
        LEGACY_BLOCKS.put("icepacked", "packed_ice");
        LEGACY_BLOCKS.put("packed_ice", "packed_ice");
        LEGACY_BLOCKS.put("sandstone", "sandstone");
        LEGACY_BLOCKS.put("netherrack", "netherrack");
        LEGACY_BLOCKS.put("end_stone", "end_stone");
        LEGACY_BLOCKS.put("dirt", "dirt");
        LEGACY_BLOCKS.put("obsidian", "obsidian");
        LEGACY_BLOCKS.put("glowstone", "glowstone");
        LEGACY_BLOCKS.put("glass", "glass");
        LEGACY_BLOCKS.put("ice", "ice");
        LEGACY_BLOCKS.put("water", "water");
        LEGACY_BLOCKS.put("lava", "lava");
    }
}

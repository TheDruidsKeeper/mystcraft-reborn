package com.techbucketdivision.mystcraft.symbol.symbols;

import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import com.techbucketdivision.mystcraft.symbol.grammar.Grammar;
import org.jspecify.annotations.Nullable;

import static com.techbucketdivision.mystcraft.symbol.grammar.GrammarRules.*;

/**
 * Registers every built-in symbol of REQUIREMENTS §4.3.1–4.3.11 together with its grammar rules. Modifier symbols
 * (§4.3.12) live in {@code ModifierSymbols}, block symbols (§4.3.13) in {@code BlockSymbols}.
 */
public final class BuiltinSymbols {
    private BuiltinSymbols() {}

    public static void registerAll() {
        // --- visuals (§4.3.1)
        rule(add(new ColorSymbols.ColorCloud()), 3, VISUAL, GRADIENT);
        rule(add(new ColorSymbols.ColorCloudNatural()), 2, VISUAL);
        rule(add(new ColorSymbols.ColorFog()), 3, VISUAL, GRADIENT);
        rule(add(new ColorSymbols.ColorFogNatural()), 2, VISUAL);
        rule(add(new ColorSymbols.ColorFoliage()), 3, VISUAL, COLOR);
        rule(add(new ColorSymbols.ColorFoliageNatural()), 2, VISUAL);
        rule(add(new ColorSymbols.ColorGrass()), 3, VISUAL, COLOR);
        rule(add(new ColorSymbols.ColorGrassNatural()), 2, VISUAL);
        rule(add(new ColorSymbols.ColorSky()), 3, VISUAL, GRADIENT);
        rule(add(new ColorSymbols.ColorSkyNatural()), 2, VISUAL);
        rule(add(new ColorSymbols.ColorSkyNight()), 3, VISUAL, GRADIENT);
        rule(add(new ColorSymbols.ColorWater()), 3, VISUAL, COLOR);
        rule(add(new ColorSymbols.ColorWaterNatural()), 2, VISUAL);
        rule(add(new ColorSymbols.NoHorizon()), 2, VISUAL);
        rule(add(new ColorSymbols.Rainbow()), 4, VISUAL, ANGLE);

        // --- celestials (§4.3.2)
        rule(add(new CelestialSymbols.SunNormal()), 1, SUN, SUNSET, PERIOD, ANGLE, PHASE);
        rule(add(CelestialSymbols.sunDark()), 3, SUN);
        rule(add(new CelestialSymbols.MoonNormal()), 1, MOON, SUNSET_UNCOMMON, PERIOD, ANGLE, PHASE);
        rule(add(CelestialSymbols.moonDark()), 3, MOON);
        rule(add(new CelestialSymbols.StarsNormal()), 1, STARFIELD, GRADIENT, PERIOD, ANGLE);
        rule(add(new CelestialSymbols.StarsTwinkle()), 2, STARFIELD, GRADIENT, PERIOD, ANGLE);
        rule(add(new CelestialSymbols.StarsEndSky()), 4, STARFIELD, GRADIENT);
        rule(add(CelestialSymbols.starsDark()), 3, STARFIELD);

        // --- lighting (§4.3.3)
        rule(add(new LightingSymbols.LightingNormal()), 1, LIGHTING);
        rule(add(new LightingSymbols.LightingBright()), 2, LIGHTING);
        rule(add(new LightingSymbols.LightingDark()), 2, LIGHTING);

        // --- weather (§4.3.4)
        rule(add(WeatherSymbols.normal()), 1, WEATHER);
        rule(add(WeatherSymbols.fast()), 2, WEATHER);
        rule(add(WeatherSymbols.slow()), 2, WEATHER);
        rule(add(WeatherSymbols.off()), 2, WEATHER);
        rule(add(WeatherSymbols.on()), 2, WEATHER);
        rule(add(WeatherSymbols.cloudy()), 2, WEATHER);
        rule(add(WeatherSymbols.rain()), 2, WEATHER);
        rule(add(WeatherSymbols.snow()), 2, WEATHER);
        rule(add(WeatherSymbols.storm()), 2, WEATHER);

        // --- biome distribution (§4.3.5)
        rule(add(new BiomeControllerSymbols.Native()), 1, BIOME_CONTROLLER);
        rule(add(new BiomeControllerSymbols.Single()), 1, BIOME_CONTROLLER, BIOME);
        rule(add(new BiomeControllerSymbols.Tiled(false)), 2, BIOME_CONTROLLER, BIOME_LIST, BIOME);
        rule(add(new BiomeControllerSymbols.Tiled(true)), 2, BIOME_CONTROLLER, BIOME_LIST, BIOME);
        rule(add(BiomeControllerSymbols.tiny()), 2, BIOME_CONTROLLER, BIOME_LIST, BIOME, BIOME);
        rule(add(BiomeControllerSymbols.small()), 2, BIOME_CONTROLLER, BIOME_LIST, BIOME, BIOME);
        rule(add(BiomeControllerSymbols.medium()), 1, BIOME_CONTROLLER, BIOME_LIST, BIOME, BIOME);
        rule(add(BiomeControllerSymbols.large()), 2, BIOME_CONTROLLER, BIOME_LIST, BIOME, BIOME);
        rule(add(BiomeControllerSymbols.huge()), 2, BIOME_CONTROLLER, BIOME_LIST, BIOME, BIOME);

        // --- terrain (§4.3.6)
        rule(add(new TerrainSymbols.Normal(false)), 1, TERRAIN, BLOCK_TERRAIN, BLOCK_SEA);
        rule(add(new TerrainSymbols.Normal(true)), 3, TERRAIN, BLOCK_TERRAIN, BLOCK_SEA);
        rule(add(new TerrainSymbols.Flat()), 2, TERRAIN, BLOCK_TERRAIN, BLOCK_SEA);
        rule(add(new TerrainSymbols.Nether()), 3, TERRAIN, BLOCK_TERRAIN, BLOCK_SEA);
        rule(add(new TerrainSymbols.End()), 3, TERRAIN, BLOCK_TERRAIN, BLOCK_SEA);
        rule(add(new TerrainSymbols.Void()), 3, TERRAIN);

        // --- large features (§4.3.7)
        rule(add(new FeatureSymbols.Caves()), 1, FEATURE_LARGE);
        rule(add(new FeatureSymbols.Tendrils()), 4, FEATURE_LARGE, BLOCK_STRUCTURE);
        rule(add(new FeatureSymbols.Skylands()), 5, FEATURE_LARGE);
        rule(add(new FeatureSymbols.FloatingIslands()), 4, FEATURE_LARGE, BIOME, BLOCK_STRUCTURE);
        rule(add(new FeatureSymbols.HugeTrees()), 2, FEATURE_LARGE);
        rule(add(new FeatureSymbols.DenseOres()), null, FEATURE_LARGE);
        AgeSymbol largeDummy = add(FeatureSymbols.largeDummy());
        rule(largeDummy, 5, FEATURE_LARGES_0, FEATURE_LARGE_EXT);
        rule(largeDummy, null, FEATURE_LARGE_EXT);

        // --- medium features (§4.3.8)
        rule(add(FeatureSymbols.villages()), 1, FEATURE_MEDIUM);
        rule(add(FeatureSymbols.strongholds()), 1, FEATURE_MEDIUM);
        rule(add(FeatureSymbols.mineshafts()), 1, FEATURE_MEDIUM);
        rule(add(FeatureSymbols.netherFortress()), 2, FEATURE_MEDIUM);
        rule(add(new FeatureSymbols.Ravines()), 1, FEATURE_MEDIUM);
        rule(add(new FeatureSymbols.Dungeons()), 2, FEATURE_MEDIUM);
        rule(add(new FeatureSymbols.Spheres()), 3, FEATURE_MEDIUM, BLOCK_STRUCTURE);
        rule(add(new FeatureSymbols.Spikes()), 3, FEATURE_MEDIUM, BLOCK_STRUCTURE);
        AgeSymbol mediumDummy = add(FeatureSymbols.mediumDummy());
        rule(mediumDummy, 5, FEATURE_MEDIUMS_0, FEATURE_MEDIUM_EXT);
        rule(mediumDummy, null, FEATURE_MEDIUM_EXT);

        // --- small features (§4.3.9)
        rule(add(new FeatureSymbols.LakesSurface()), 1, FEATURE_SMALL, BLOCK_FLUID);
        rule(add(new FeatureSymbols.LakesDeep()), 1, FEATURE_SMALL, BLOCK_NONSOLID);
        rule(add(new FeatureSymbols.Obelisks()), 3, FEATURE_SMALL, BLOCK_STRUCTURE);
        rule(add(new FeatureSymbols.CrystalFormations()), 3, FEATURE_SMALL, BLOCK_CRYSTAL);
        rule(add(new FeatureSymbols.StarFissure()), 3, FEATURE_SMALL);
        AgeSymbol smallDummy = add(FeatureSymbols.smallDummy());
        rule(smallDummy, null, FEATURE_SMALLS_0, FEATURE_SMALL_EXT);
        rule(smallDummy, null, FEATURE_SMALL_EXT);

        // --- environmental effects (§4.3.10) — never random
        rule(add(new EffectSymbols.Accelerated()), null, EFFECT);
        rule(add(new EffectSymbols.Explosions()), null, EFFECT);
        rule(add(new EffectSymbols.Lightning()), null, EFFECT, GRADIENT);
        rule(add(new EffectSymbols.Meteors()), null, EFFECT);
        rule(add(new EffectSymbols.Scorched()), null, EFFECT);

        // --- misc (§4.3.11)
        add(new MiscSymbols.PvPOff());
        rule(add(new MiscSymbols.NoSea()), 2, BLOCK_SEA);
        add(new MiscSymbols.ClearModifiers());
    }

    /** Registers the symbol (may be rejected by config/blacklist; rules for rejected symbols stay inactive). */
    public static AgeSymbol add(AgeSymbol symbol) {
        SymbolRegistry.register(symbol);
        return symbol;
    }

    /** Adds the grammar rule {@code parent -> preceding... symbol}. */
    public static void rule(AgeSymbol symbol, @Nullable Integer rank, String parent, String... preceding) {
        Grammar.addSymbolRule(symbol, parent, rank, preceding);
    }
}

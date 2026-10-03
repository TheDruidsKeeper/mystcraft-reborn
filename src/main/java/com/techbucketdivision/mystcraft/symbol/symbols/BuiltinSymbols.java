package com.techbucketdivision.mystcraft.symbol.symbols;

import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.SymbolCategory;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;

/**
 * Registers every built-in symbol of REQUIREMENTS §4.3.1–4.3.11 in its world-building category. Modifier symbols
 * (§4.3.12) live in {@code ModifierSymbols}, block symbols (§4.3.13) in {@code BlockSymbols}. The original grammar's
 * "Lacking ... Features" dummies and Clear Modifiers are gone with the grammar (Reborn: the Age blueprint fills books).
 */
public final class BuiltinSymbols {
    private BuiltinSymbols() {}

    public static void registerAll() {
        // --- visuals (§4.3.1)
        add(new ColorSymbols.ColorCloud(), SymbolCategory.SKY_COLORS);
        add(new ColorSymbols.ColorCloudNatural(), SymbolCategory.SKY_COLORS);
        add(new ColorSymbols.ColorFog(), SymbolCategory.SKY_COLORS);
        add(new ColorSymbols.ColorFogNatural(), SymbolCategory.SKY_COLORS);
        add(new ColorSymbols.ColorFoliage(), SymbolCategory.WORLD_COLORS);
        add(new ColorSymbols.ColorFoliageNatural(), SymbolCategory.WORLD_COLORS);
        add(new ColorSymbols.ColorGrass(), SymbolCategory.WORLD_COLORS);
        add(new ColorSymbols.ColorGrassNatural(), SymbolCategory.WORLD_COLORS);
        add(new ColorSymbols.ColorSky(), SymbolCategory.SKY_COLORS);
        add(new ColorSymbols.ColorSkyNatural(), SymbolCategory.SKY_COLORS);
        add(new ColorSymbols.ColorSkyNight(), SymbolCategory.SKY_COLORS);
        add(new ColorSymbols.ColorWater(), SymbolCategory.WORLD_COLORS);
        add(new ColorSymbols.ColorWaterNatural(), SymbolCategory.WORLD_COLORS);
        add(new ColorSymbols.NoHorizon(), SymbolCategory.SKY_COLORS);
        add(new ColorSymbols.Rainbow(), SymbolCategory.SKY_COLORS);

        // --- celestials (§4.3.2)
        add(new CelestialSymbols.SunNormal(), SymbolCategory.CELESTIALS);
        add(CelestialSymbols.sunDark(), SymbolCategory.CELESTIALS);
        add(new CelestialSymbols.MoonNormal(), SymbolCategory.CELESTIALS);
        add(CelestialSymbols.moonDark(), SymbolCategory.CELESTIALS);
        add(new CelestialSymbols.StarsNormal(), SymbolCategory.CELESTIALS);
        add(new CelestialSymbols.StarsTwinkle(), SymbolCategory.CELESTIALS);
        add(new CelestialSymbols.StarsEndSky(), SymbolCategory.CELESTIALS);
        add(CelestialSymbols.starsDark(), SymbolCategory.CELESTIALS);

        // --- lighting (§4.3.3)
        add(new LightingSymbols.LightingNormal(), SymbolCategory.LIGHTING);
        add(new LightingSymbols.LightingBright(), SymbolCategory.LIGHTING);
        add(new LightingSymbols.LightingDark(), SymbolCategory.LIGHTING);

        // --- weather (§4.3.4)
        add(WeatherSymbols.normal(), SymbolCategory.WEATHER);
        add(WeatherSymbols.fast(), SymbolCategory.WEATHER);
        add(WeatherSymbols.slow(), SymbolCategory.WEATHER);
        add(WeatherSymbols.off(), SymbolCategory.WEATHER);
        add(WeatherSymbols.on(), SymbolCategory.WEATHER);
        add(WeatherSymbols.cloudy(), SymbolCategory.WEATHER);
        add(WeatherSymbols.rain(), SymbolCategory.WEATHER);
        add(WeatherSymbols.snow(), SymbolCategory.WEATHER);
        add(WeatherSymbols.storm(), SymbolCategory.WEATHER);

        // --- biome distribution (§4.3.5)
        add(new BiomeControllerSymbols.Native(), SymbolCategory.BIOME_LAYOUT);
        add(new BiomeControllerSymbols.Single(), SymbolCategory.BIOME_LAYOUT);
        add(new BiomeControllerSymbols.Tiled(false), SymbolCategory.BIOME_LAYOUT);
        add(new BiomeControllerSymbols.Tiled(true), SymbolCategory.BIOME_LAYOUT);
        add(BiomeControllerSymbols.tiny(), SymbolCategory.BIOME_LAYOUT);
        add(BiomeControllerSymbols.small(), SymbolCategory.BIOME_LAYOUT);
        add(BiomeControllerSymbols.medium(), SymbolCategory.BIOME_LAYOUT);
        add(BiomeControllerSymbols.large(), SymbolCategory.BIOME_LAYOUT);
        add(BiomeControllerSymbols.huge(), SymbolCategory.BIOME_LAYOUT);

        // --- terrain (§4.3.6)
        add(new TerrainSymbols.Normal(false), SymbolCategory.TERRAIN);
        add(new TerrainSymbols.Normal(true), SymbolCategory.TERRAIN);
        add(new TerrainSymbols.Flat(), SymbolCategory.TERRAIN);
        add(new TerrainSymbols.Nether(), SymbolCategory.TERRAIN);
        add(new TerrainSymbols.End(), SymbolCategory.TERRAIN);
        add(new TerrainSymbols.Void(), SymbolCategory.TERRAIN);

        // --- large features (§4.3.7)
        add(new FeatureSymbols.Caves(), SymbolCategory.FEATURES);
        add(new FeatureSymbols.Tendrils(), SymbolCategory.FEATURES);
        add(new FeatureSymbols.Skylands(), SymbolCategory.FEATURES);
        add(new FeatureSymbols.FloatingIslands(), SymbolCategory.FEATURES);
        add(new FeatureSymbols.HugeTrees(), SymbolCategory.FEATURES);
        add(new FeatureSymbols.DenseOres(), SymbolCategory.FEATURES);

        // --- medium features (§4.3.8)
        add(FeatureSymbols.villages(), SymbolCategory.STRUCTURES);
        add(FeatureSymbols.strongholds(), SymbolCategory.STRUCTURES);
        add(FeatureSymbols.mineshafts(), SymbolCategory.STRUCTURES);
        add(FeatureSymbols.netherFortress(), SymbolCategory.STRUCTURES);
        add(new FeatureSymbols.Ravines(), SymbolCategory.STRUCTURES);
        add(new FeatureSymbols.Dungeons(), SymbolCategory.STRUCTURES);
        add(new FeatureSymbols.Spheres(), SymbolCategory.FEATURES);
        add(new FeatureSymbols.Spikes(), SymbolCategory.FEATURES);

        // --- small features (§4.3.9)
        add(new FeatureSymbols.LakesSurface(), SymbolCategory.FEATURES);
        add(new FeatureSymbols.LakesDeep(), SymbolCategory.FEATURES);
        add(new FeatureSymbols.Obelisks(), SymbolCategory.FEATURES);
        add(new FeatureSymbols.CrystalFormations(), SymbolCategory.FEATURES);
        add(new FeatureSymbols.StarFissure(), SymbolCategory.FEATURES);

        // --- environmental effects (§4.3.10)
        add(new EffectSymbols.Accelerated(), SymbolCategory.EFFECTS);
        add(new EffectSymbols.Explosions(), SymbolCategory.EFFECTS);
        add(new EffectSymbols.Lightning(), SymbolCategory.EFFECTS);
        add(new EffectSymbols.Meteors(), SymbolCategory.EFFECTS);
        add(new EffectSymbols.Scorched(), SymbolCategory.EFFECTS);

        // --- misc (§4.3.11)
        add(new MiscSymbols.PvPOff(), SymbolCategory.EFFECTS);
        add(new MiscSymbols.NoSea(), SymbolCategory.MATERIALS);
    }

    /** Registers the symbol in its category (may be rejected by config/blacklist; rules for rejected symbols stay inactive). */
    public static AgeSymbol add(AgeSymbol symbol, SymbolCategory category) {
        SymbolRegistry.register(symbol.withCategory(category));
        return symbol;
    }
}

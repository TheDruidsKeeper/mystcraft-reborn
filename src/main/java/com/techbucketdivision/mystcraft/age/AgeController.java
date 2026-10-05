package com.tbd.mystcraft.age;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.api.symbol.AgeDirector;
import com.tbd.mystcraft.api.symbol.AgeSymbol;
import com.tbd.mystcraft.api.symbol.BlockCategory;
import com.tbd.mystcraft.api.symbol.BlockDescriptor;
import com.tbd.mystcraft.api.symbol.Modifier;
import com.tbd.mystcraft.api.symbol.logic.BiomeController;
import com.tbd.mystcraft.api.symbol.CreatureGroup;
import com.tbd.mystcraft.api.symbol.logic.Celestial;
import com.tbd.mystcraft.api.symbol.logic.CreatureController;
import com.tbd.mystcraft.api.symbol.logic.ChunkFinalizer;
import com.tbd.mystcraft.api.symbol.logic.ColorKind;
import com.tbd.mystcraft.api.symbol.logic.DynamicColorProvider;
import com.tbd.mystcraft.api.symbol.logic.EnvironmentalEffect;
import com.tbd.mystcraft.api.symbol.logic.LightingController;
import com.tbd.mystcraft.api.symbol.logic.Populator;
import com.tbd.mystcraft.api.symbol.logic.SkyOptions;
import com.tbd.mystcraft.api.symbol.logic.StaticColorProvider;
import com.tbd.mystcraft.api.symbol.logic.TerrainAlteration;
import com.tbd.mystcraft.api.symbol.logic.TerrainContext;
import com.tbd.mystcraft.api.symbol.logic.TerrainGenerator;
import com.tbd.mystcraft.api.symbol.logic.WeatherController;
import com.tbd.mystcraft.symbol.SymbolRegistry;
import com.tbd.mystcraft.util.Colors;
import com.tbd.mystcraft.world.biome.BiomeHeights;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Compiles an {@link AgeData} symbol list into runtime logic. Exists on both sides (the client builds it from synced
 * data for rendering). Construction follows original spec §5.5 and §4.4.4.
 */
public final class AgeController implements AgeDirector, TerrainContext {
    /** Instability charged when a required controller is missing (original: 0) / when an extra one is registered. */
    public static final int MISSING_CONTROLLER_INSTABILITY = 0;
    public static final int EXTRA_CONTROLLER_INSTABILITY = 500;
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;

    private final AgeData data;
    private final HolderLookup.Provider registries;
    private final boolean clientSide;
    private int builtRevision = -1;

    // construction state
    private final Map<String, Modifier> modifiers = new HashMap<>();
    private final LinkedList<BlockDescriptor> blockList = new LinkedList<>();
    private final LinkedList<Holder<Biome>> biomeList = new LinkedList<>();
    private final Map<Identifier, Integer> symbolCounts = new HashMap<>();
    private int symbolInstability;
    private long constructionSeed;

    // compiled logic. The collections are replaced, never cleared, by reconstruct(): chunk generation and renderers
    // iterate them from other threads while a rebuild (AgeData revision bump) may be running.
    private @Nullable BiomeController biomeController;
    private @Nullable TerrainGenerator terrainGenerator;
    private @Nullable LightingController lighting;
    private @Nullable WeatherController weather;
    private volatile List<TerrainAlteration> alterations = new ArrayList<>();
    private volatile List<Populator> populators = new ArrayList<>();
    private volatile List<ChunkFinalizer> finalizers = new ArrayList<>();
    private volatile List<Celestial> celestials = new ArrayList<>();
    private volatile List<EnvironmentalEffect> effects = new ArrayList<>();
    private volatile Map<CreatureGroup, CreatureController> creatures = new EnumMap<>(CreatureGroup.class);
    private volatile Map<ColorKind, List<DynamicColorProvider>> dynamicColors = new EnumMap<>(ColorKind.class);
    private volatile Map<ColorKind, List<StaticColorProvider>> staticColors = new EnumMap<>(ColorKind.class);
    private final SkyOptions sky = new SkyOptions();
    private volatile List<Float> cloudHeights = new ArrayList<>();
    private volatile List<Float> horizons = new ArrayList<>();
    private volatile List<Integer> groundLevels = new ArrayList<>();
    private volatile List<Integer> seaLevels = new ArrayList<>();
    private BlockState terrainBlock = Blocks.STONE.defaultBlockState();
    private BlockState seaBlock = Blocks.WATER.defaultBlockState();

    public AgeController(AgeData data, HolderLookup.Provider registries, boolean clientSide) {
        this.data = data;
        this.registries = registries;
        this.clientSide = clientSide;
        reconstruct();
    }

    public AgeData ageData() {
        return data;
    }

    public boolean isClientSide() {
        return clientSide;
    }

    /** Rebuilds if the underlying data changed since the last build. */
    public synchronized void ensureCurrent() {
        if (builtRevision != data.revision()) reconstruct();
    }

    // --- construction ------------------------------------------------------------------------------------------

    public synchronized void reconstruct() {
        builtRevision = data.revision();
        modifiers.clear();
        blockList.clear();
        biomeList.clear();
        symbolCounts.clear();
        symbolInstability = 0;
        biomeController = null;
        terrainGenerator = null;
        lighting = null;
        weather = null;
        alterations = new ArrayList<>();
        populators = new ArrayList<>();
        finalizers = new ArrayList<>();
        celestials = new ArrayList<>();
        effects = new ArrayList<>();
        creatures = new EnumMap<>(CreatureGroup.class);
        dynamicColors = new EnumMap<>(ColorKind.class);
        staticColors = new EnumMap<>(ColorKind.class);
        cloudHeights = new ArrayList<>();
        horizons = new ArrayList<>();
        groundLevels = new ArrayList<>();
        seaLevels = new ArrayList<>();
        sky.cloudHeight = 192f;
        sky.horizon = 63f;
        sky.drawHorizon = true;
        sky.drawVoid = true;
        sky.pvpEnabled = true;
        terrainBlock = Blocks.STONE.defaultBlockState();
        seaBlock = Blocks.WATER.defaultBlockState();

        Random symbolSeeds = new Random(data.seed());
        List<Identifier> symbols = data.symbols();
        for (Identifier id : symbols) {
            AgeSymbol symbol = SymbolRegistry.get(id);
            if (symbol == null) {
                Mystcraft.LOGGER.error("Age {} references unknown symbol {}", data.name(), id);
                continue;
            }
            addSymbol(symbol, symbolSeeds.nextLong());
        }

        // Fallbacks for missing controllers: the blueprint fills every required category at the first link, so this
        // only triggers for Ages whose symbols failed to register (add-ons); the server writes the pick back.
        Random fallbackRand = new Random(data.seed() ^ 0x5DEECE66DL);
        if (biomeController == null) fallback(BiomeController.class, fallbackRand);
        if (terrainGenerator == null) fallback(TerrainGenerator.class, fallbackRand);
        if (lighting == null) fallback(LightingController.class, fallbackRand);
        if (weather == null) fallback(WeatherController.class, fallbackRand);

        // Charge dangling modifiers
        for (Modifier m : modifiers.values()) symbolInstability += m.dangling;
        modifiers.clear();
        symbolInstability += blockList.size() * 50;
        symbolInstability += biomeList.size() * 100;

        if (weather != null) weather.bindStorage(data.data("weather"));

        if (!cloudHeights.isEmpty()) sky.cloudHeight = average(cloudHeights);
        if (!horizons.isEmpty()) sky.horizon = average(horizons);
        if (!clientSide) data.markVisited();
    }

    private void fallback(Class<?> type, Random rand) {
        List<AgeSymbol> candidates = SymbolRegistry.implementing(type);
        if (candidates.isEmpty()) {
            Mystcraft.LOGGER.error("No symbol provides {}; Age {} will be broken", type.getSimpleName(), data.name());
            return;
        }
        AgeSymbol pick = candidates.get(rand.nextInt(candidates.size()));
        Mystcraft.LOGGER.warn("[blueprint] Age {} has no {}; falling back to {}", data.name(), type.getSimpleName(), pick.id());
        addSymbol(pick, rand.nextLong());
        symbolInstability += MISSING_CONTROLLER_INSTABILITY;
        if (!clientSide) data.addSymbol(pick.id());
        builtRevision = data.revision();
    }

    private void addSymbol(AgeSymbol symbol, long seed) {
        int count = symbolCounts.merge(symbol.id(), 1, Integer::sum);
        constructionSeed = seed;
        try {
            symbol.registerLogic(this, seed);
        } catch (Exception e) {
            Mystcraft.LOGGER.error("Symbol {} failed to register logic for age {}", symbol.id(), data.name(), e);
        }
        symbolInstability += symbol.instabilityModifier(count);
    }

    private static float average(List<Float> values) {
        float sum = 0;
        for (float f : values) sum += f;
        return sum / values.size();
    }

    // --- AgeDirector -------------------------------------------------------------------------------------------

    @Override
    public void registerInterface(Object logic) {
        boolean any = false;
        if (logic instanceof BiomeController bc) {
            if (biomeController != null) symbolInstability += EXTRA_CONTROLLER_INSTABILITY;
            biomeController = bc;
            any = true;
        }
        if (logic instanceof TerrainGenerator tg) {
            if (terrainGenerator != null) symbolInstability += EXTRA_CONTROLLER_INSTABILITY;
            terrainGenerator = tg;
            any = true;
        }
        if (logic instanceof LightingController lc) {
            if (lighting != null) symbolInstability += EXTRA_CONTROLLER_INSTABILITY;
            lighting = lc;
            any = true;
        }
        if (logic instanceof WeatherController wc) {
            if (weather != null) symbolInstability += EXTRA_CONTROLLER_INSTABILITY;
            weather = wc;
            any = true;
        }
        if (logic instanceof TerrainAlteration ta) { alterations.add(ta); any = true; }
        if (logic instanceof Populator p) { populators.add(p); any = true; }
        if (logic instanceof ChunkFinalizer cf) { finalizers.add(cf); any = true; }
        if (logic instanceof Celestial c) { celestials.add(c); any = true; }
        if (logic instanceof EnvironmentalEffect ee) { effects.add(ee); any = true; }
        if (logic instanceof CreatureController cc) {
            if (creatures.put(cc.group(), cc) != null) symbolInstability += EXTRA_CONTROLLER_INSTABILITY;
            any = true;
        }
        if (logic instanceof DynamicColorProvider dc) { dynamicColors.computeIfAbsent(dc.kind(), k -> new ArrayList<>()).add(dc); any = true; }
        if (logic instanceof StaticColorProvider sc) { staticColors.computeIfAbsent(sc.kind(), k -> new ArrayList<>()).add(sc); any = true; }
        if (!any) {
            Mystcraft.LOGGER.warn("Logic object {} implements no known age logic interface", logic.getClass().getName());
        }
    }

    @Override
    public void setModifier(String id, Modifier modifier) {
        Modifier old = modifiers.put(id, modifier);
        if (old != null && !old.isEmpty()) symbolInstability += old.dangling;
    }

    @Override
    public Modifier popModifier(String id) {
        Modifier m = modifiers.remove(id);
        return m == null ? Modifier.EMPTY : m;
    }

    @Override
    public Modifier peekModifier(String id) {
        Modifier m = modifiers.get(id);
        return m == null ? Modifier.EMPTY : m;
    }

    @Override
    public void clearModifiers() {
        for (Modifier m : modifiers.values()) symbolInstability += Math.round(m.dangling * 0.20f);
        modifiers.clear();
        symbolInstability += Math.round(blockList.size() * 50 * 0.20f);
        symbolInstability += Math.round(biomeList.size() * 100 * 0.20f);
        blockList.clear();
        biomeList.clear();
    }

    @Override
    public void pushBlock(BlockDescriptor block) {
        blockList.addFirst(block);
    }

    @Override
    public @Nullable BlockDescriptor popBlockMatching(BlockCategory... categories) {
        var it = blockList.iterator();
        while (it.hasNext()) {
            BlockDescriptor d = it.next();
            if (d.isUsableForAny(categories)) {
                it.remove();
                return d;
            }
        }
        return null;
    }

    @Override
    public void pushBiome(Holder<Biome> biome) {
        biomeList.addLast(biome);
    }

    @Override
    public @Nullable Holder<Biome> popBiome() {
        return biomeList.isEmpty() ? null : biomeList.removeLast();
    }

    @Override
    public List<Holder<Biome>> popAllBiomes() {
        List<Holder<Biome>> out = new ArrayList<>(biomeList);
        biomeList.clear();
        return out;
    }

    @Override public void addInstability(int instability) { symbolInstability += instability; }
    @Override public void setCloudHeight(float height) { cloudHeights.add(height); }
    @Override public void setHorizon(float height) { horizons.add(height); }
    @Override public void setAverageGroundLevel(int level) { groundLevels.add(level); }
    @Override public void setSeaLevel(int level) { seaLevels.add(level); }
    @Override public void setDrawHorizon(boolean draw) { sky.drawHorizon = draw; }
    @Override public void setDrawVoid(boolean draw) { sky.drawVoid = draw; }
    @Override public void setPvPEnabled(boolean enabled) { sky.pvpEnabled = enabled; }
    @Override public long getSeed() { return data.seed(); }
    @Override public HolderLookup.Provider registries() { return registries; }

    /** Seed handed to the symbol currently registering (for symbols that build helpers lazily). */
    public long currentSymbolSeed() {
        return constructionSeed;
    }

    /** Sets the default terrain/sea block chosen by the terrain generator. */
    public void setTerrainBlocks(BlockState terrain, BlockState sea) {
        this.terrainBlock = terrain;
        this.seaBlock = sea;
    }

    // --- TerrainContext ----------------------------------------------------------------------------------------

    @Override public long seed() { return data.seed(); }

    @Override
    public int getSeaLevel() {
        return seaLevel();
    }

    @Override
    public int seaLevel() {
        if (seaLevels.isEmpty()) return 63;
        int sum = 0;
        for (int i : seaLevels) sum += i;
        return sum / seaLevels.size();
    }

    @Override
    public int averageGroundLevel() {
        if (groundLevels.isEmpty()) return 64;
        int sum = 0;
        for (int i : groundLevels) sum += i;
        return sum / groundLevels.size();
    }

    @Override public int minY() { return MIN_Y; }
    @Override public int maxY() { return MIN_Y + HEIGHT - 1; }

    @Override
    public Holder<Biome> biomeAt(int blockX, int blockZ) {
        if (biomeController == null) {
            return registries.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        }
        return biomeController.getBiomeAt(blockX, blockZ);
    }

    @Override public float biomeBaseHeight(Holder<Biome> biome) { return BiomeHeights.baseHeight(biome); }
    @Override public float biomeHeightVariation(Holder<Biome> biome) { return BiomeHeights.heightVariation(biome); }
    @Override public BlockState terrainBlock() { return terrainBlock; }
    @Override public BlockState seaBlock() { return seaBlock; }

    // --- compiled logic accessors ------------------------------------------------------------------------------

    public @Nullable BiomeController biomeController() { return biomeController; }
    public @Nullable TerrainGenerator terrainGenerator() { return terrainGenerator; }
    public @Nullable LightingController lighting() { return lighting; }
    public @Nullable WeatherController weather() { return weather; }
    public List<TerrainAlteration> alterations() { return Collections.unmodifiableList(alterations); }
    public List<Populator> populators() { return Collections.unmodifiableList(populators); }
    public List<ChunkFinalizer> finalizers() { return Collections.unmodifiableList(finalizers); }
    public List<Celestial> celestials() { return Collections.unmodifiableList(celestials); }
    public List<EnvironmentalEffect> effects() { return Collections.unmodifiableList(effects); }
    /** The creature controller written for a group, or {@code null} (the group spawns as in the biomes). */
    public @Nullable CreatureController creatures(CreatureGroup group) { return creatures.get(group); }
    public SkyOptions sky() { return sky; }
    public int symbolInstability() { return symbolInstability; }
    public boolean isPvPEnabled() { return sky.pvpEnabled; }

    /** Averaged dynamic colour or {@code null} when no provider exists (caller falls back to vanilla). */
    public Colors.@Nullable RGB dynamicColor(ColorKind kind, long time, float partialTick, float celestialAngle, float biomeTemp) {
        List<DynamicColorProvider> list = dynamicColors.get(kind);
        if (list == null || list.isEmpty()) return null;
        float r = 0, g = 0, b = 0;
        for (DynamicColorProvider p : list) {
            Colors.RGB c = p.getColor(time, partialTick, celestialAngle, biomeTemp);
            r += c.r(); g += c.g(); b += c.b();
        }
        int n = list.size();
        return new Colors.RGB(r / n, g / n, b / n);
    }

    /** Averaged static colour or {@code null}. */
    public Colors.@Nullable RGB staticColor(ColorKind kind) {
        List<StaticColorProvider> list = staticColors.get(kind);
        if (list == null || list.isEmpty()) return null;
        float r = 0, g = 0, b = 0;
        int n = 0;
        for (StaticColorProvider p : list) {
            Colors.RGB c = p.getStaticColor();
            if (c == null) continue;
            r += c.r(); g += c.g(); b += c.b();
            n++;
        }
        return n == 0 ? null : new Colors.RGB(r / n, g / n, b / n);
    }

    // --- celestial maths (original spec §4.3.2) -----------------------------------------------------------------

    /** Combined celestial angle 0..1 from all light-providing celestials; 0.5 (night) when there are none. */
    public float celestialAngle(long time, float partialTick) {
        float lowest = 0.5f, highest = 0.5f;
        boolean any = false;
        for (Celestial c : celestials) {
            if (!c.providesLight()) continue;
            float a = c.getAltitudeAngle(time, partialTick);
            a = a - (float) Math.floor(a);
            if (!any) {
                lowest = a;
                highest = a;
                any = true;
            } else {
                if (a < lowest) lowest = a;
                if (a > highest) highest = a;
            }
        }
        if (!any) return 0.5f;
        return (1 - highest) < lowest ? highest : lowest;
    }

    /** Ticks until the next sunrise (used when all players sleep). */
    public long timeToSunrise(long time) {
        long min = Long.MAX_VALUE;
        for (Celestial c : celestials) {
            if (c.providesLight()) min = Math.min(min, c.getTimeToDawn(time));
        }
        return min == Long.MAX_VALUE ? 24000L - (time % 24000L) : min;
    }
}

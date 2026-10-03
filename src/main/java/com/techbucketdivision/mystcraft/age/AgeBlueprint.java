package com.techbucketdivision.mystcraft.age;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.BlockCategory;
import com.techbucketdivision.mystcraft.api.symbol.ModifierSlot;
import com.techbucketdivision.mystcraft.api.symbol.SymbolCategory;
import com.techbucketdivision.mystcraft.config.WorldBuildingConfig;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.item.component.SymbolPage;
import com.techbucketdivision.mystcraft.symbol.BiomeSymbols;
import com.techbucketdivision.mystcraft.symbol.BlockSymbols;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import com.techbucketdivision.mystcraft.symbol.modifiers.ModifierSymbols;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Age blueprint (world-building plan §2, §3): fills an incomplete Descriptive Book deterministically from the Age
 * seed, organises the pages by category and flattens them into the symbol list the {@link AgeController} builds
 * from. Pure and server-safe: no registries beyond the symbol registry are touched.
 * <p>
 * Rules: categories the author wrote anything in are never touched; required categories with nothing written are
 * filled; optional categories get their configured defaults and, by chance, random extras; hard compatibility gates
 * (nether biomes need nether terrain, end biomes end terrain, {@code biome_single} takes one biome, a dark sun needs
 * bright lighting); the discovered symbols' instability stays within the budget; every roll comes from
 * {@code ageSeed ^ FILL_SALT} with one stream per category so a config change in one category leaves the others alone.
 * Every discovered page carries the {@link SymbolPage#discovered()} flag. Logged under {@code [blueprint]}.
 */
public final class AgeBlueprint {
    private AgeBlueprint() {}

    public static final long FILL_SALT = 0x4D59535443524146L; // "MYSTCRAF"

    private static final Identifier TERRAIN_NETHER = MystIds.id("terrain_nether");
    private static final Identifier TERRAIN_END = MystIds.id("terrain_end");
    private static final Identifier BIOME_NATIVE = MystIds.id("biome_native");
    private static final Identifier BIOME_SINGLE = MystIds.id("biome_single");
    private static final Identifier NETHER_FORTRESS = MystIds.id("nether_fortress");
    private static final Identifier SUN_DARK = MystIds.id("sun_dark");
    private static final Identifier SUN_NORMAL = MystIds.id("sun_normal");
    private static final Identifier LIGHTING_BRIGHT = MystIds.id("lighting_bright");
    private static final Identifier LIGHTING_NORMAL = MystIds.id("lighting_normal");
    private static final Identifier STAR_FISSURE = MystIds.id("star_fissure");
    private static final Identifier NO_SEA = MystIds.id("no_sea");
    private static final Identifier GRADIENT = MystIds.id("mod_gradient");
    private static final Identifier SUNSET = MystIds.id("color_horizon");
    private static final List<Identifier> DIRECTIONS = List.of(MystIds.id("mod_north"), MystIds.id("mod_east"), MystIds.id("mod_south"), MystIds.id("mod_west"));
    private static final List<Identifier> PHASES = List.of(MystIds.id("mod_end"), MystIds.id("mod_rising"), MystIds.id("mod_noon"), MystIds.id("mod_setting"));
    private static final Set<String> NETHER_BIOMES = Set.of("nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas");

    /** Outcome of a fill: the organised page list and what was discovered. */
    public record Result(List<ItemStack> pages, List<SymbolPage> discovered, int discoveredInstability) {}

    // --- flattening and organising ----------------------------------------------------------------------------------

    /** The symbol ids the Age is built from: pages in list order, each as its modifiers then its symbol. */
    public static List<Identifier> flatten(List<ItemStack> pages) {
        List<Identifier> out = new ArrayList<>();
        for (ItemStack page : pages) {
            SymbolPage symbolPage = PageItem.getSymbolPage(page);
            if (symbolPage != null) out.addAll(symbolPage.flatten());
        }
        return out;
    }

    /**
     * Orders a page list: link panels first, then the symbol pages by category (build order), player pages before
     * discovered ones within a category (each keeping its relative order), unknown symbols and blank pages last.
     */
    public static List<ItemStack> organise(List<ItemStack> pages) {
        List<ItemStack> panels = new ArrayList<>();
        Map<SymbolCategory, List<ItemStack>> byCategory = new EnumMap<>(SymbolCategory.class);
        Map<SymbolCategory, List<ItemStack>> discoveredByCategory = new EnumMap<>(SymbolCategory.class);
        List<ItemStack> unknown = new ArrayList<>();
        List<ItemStack> blanks = new ArrayList<>();
        for (ItemStack page : pages) {
            if (PageItem.isLinkPanel(page)) {
                panels.add(page);
                continue;
            }
            SymbolPage symbolPage = PageItem.getSymbolPage(page);
            if (symbolPage == null) {
                blanks.add(page);
                continue;
            }
            AgeSymbol symbol = symbolPage.resolve();
            if (symbol == null) {
                unknown.add(page);
                continue;
            }
            (symbolPage.discovered() ? discoveredByCategory : byCategory).computeIfAbsent(symbol.category(), c -> new ArrayList<>()).add(page);
        }
        List<ItemStack> out = new ArrayList<>(pages.size());
        out.addAll(panels);
        for (SymbolCategory category : SymbolCategory.values()) {
            out.addAll(byCategory.getOrDefault(category, List.of()));
            out.addAll(discoveredByCategory.getOrDefault(category, List.of()));
        }
        out.addAll(unknown);
        out.addAll(blanks);
        return out;
    }

    /** Pages grouped by category in build order (link panels, blanks and unknown symbols left out). */
    public static Map<SymbolCategory, List<ItemStack>> byCategory(List<ItemStack> pages) {
        Map<SymbolCategory, List<ItemStack>> out = new EnumMap<>(SymbolCategory.class);
        for (ItemStack page : organise(pages)) {
            AgeSymbol symbol = PageItem.getSymbol(page);
            if (symbol != null) out.computeIfAbsent(symbol.category(), c -> new ArrayList<>()).add(page);
        }
        return out;
    }

    /** Which required categories the pages leave empty (advisory, for the desk and the book). */
    public static List<SymbolCategory> missing(List<ItemStack> pages) {
        Map<SymbolCategory, List<ItemStack>> have = byCategory(pages);
        List<SymbolCategory> out = new ArrayList<>();
        for (SymbolCategory category : SymbolCategory.values()) {
            if (!category.isRequired() || have.containsKey(category)) continue;
            if (category == SymbolCategory.BIOMES && !needsBiomes(have)) continue;
            out.add(category);
        }
        return out;
    }

    private static boolean needsBiomes(Map<SymbolCategory, List<ItemStack>> have) {
        List<ItemStack> layout = have.get(SymbolCategory.BIOME_LAYOUT);
        if (layout == null) return true; // the filler decides once it picked a layout
        for (ItemStack page : layout) if (BIOME_NATIVE.equals(PageItem.getSymbolId(page))) return false;
        return true;
    }

    // --- filling ----------------------------------------------------------------------------------------------------

    /** Fills and organises the pages of a book about to be bound to an Age with the given seed. */
    public static Result fill(List<ItemStack> pages, long seed) {
        Filler filler = new Filler(pages, seed);
        filler.run();
        List<ItemStack> all = new ArrayList<>(pages);
        for (SymbolPage page : filler.discovered) all.add(PageItem.createSymbolPage(page));
        List<ItemStack> organised = organise(all);
        Mystcraft.LOGGER.info("[blueprint] seed {}: {} written symbol pages, {} discovered (instability {}): {}", seed,
                pages.stream().filter(PageItem::isSymbolPage).count(), filler.discovered.size(), filler.instability,
                filler.discovered.stream().map(p -> p.flatten().toString()).toList());
        return new Result(organised, List.copyOf(filler.discovered), filler.instability);
    }

    /** One fill run: keeps the state the gates need (terrain, layout, light) while rolling category by category. */
    private static final class Filler {
        final List<ItemStack> written;
        final long seed;
        final Map<SymbolCategory, List<ItemStack>> have;
        final List<SymbolPage> discovered = new ArrayList<>();
        final int budget = WorldBuildingConfig.INSTABILITY_BUDGET.get();
        int instability;
        @Nullable Identifier terrain;
        @Nullable Identifier layout;
        boolean lightingWritten;
        boolean lightingDone;

        Filler(List<ItemStack> written, long seed) {
            this.written = written;
            this.seed = seed;
            this.have = byCategory(written);
        }

        RandomSource random(SymbolCategory category) {
            return RandomSource.create((seed ^ FILL_SALT) + 0x9E3779B97F4A7C15L * (category.ordinal() + 1));
        }

        boolean written(SymbolCategory category) {
            return have.containsKey(category);
        }

        List<Identifier> writtenIds(SymbolCategory category) {
            List<Identifier> out = new ArrayList<>();
            for (ItemStack page : have.getOrDefault(category, List.of())) {
                Identifier id = PageItem.getSymbolId(page);
                if (id != null) out.add(id);
            }
            return out;
        }

        void run() {
            // state from the author's pages
            List<Identifier> writtenTerrain = writtenIds(SymbolCategory.TERRAIN);
            if (!writtenTerrain.isEmpty()) terrain = writtenTerrain.getFirst();
            List<Identifier> writtenLayout = writtenIds(SymbolCategory.BIOME_LAYOUT);
            if (!writtenLayout.isEmpty()) layout = writtenLayout.getFirst();
            lightingWritten = written(SymbolCategory.LIGHTING);

            fillTerrain();
            fillLayout();
            fillBiomes();
            fillCelestials(); // before lighting: a dark sun asks for bright lighting
            fillLighting();
            fillColors(SymbolCategory.SKY_COLORS);
            fillColors(SymbolCategory.WORLD_COLORS);
            fillSimple(SymbolCategory.WEATHER);
            fillSimple(SymbolCategory.STRUCTURES);
            fillFeatures();
            fillSimple(SymbolCategory.EFFECTS);
        }

        // --- helpers --------------------------------------------------------------------------------------------

        /** Candidate weights of a category from the config, restricted to registered symbols of that category. */
        Map<Identifier, Integer> weights(SymbolCategory category) {
            Map<Identifier, Integer> configured = WorldBuildingConfig.parseWeights(WorldBuildingConfig.CATEGORIES.get(category).weights.get());
            Map<Identifier, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<Identifier, Integer> e : configured.entrySet()) {
                AgeSymbol symbol = SymbolRegistry.get(e.getKey());
                if (symbol == null || e.getValue() <= 0) continue;
                if (symbol.category() != category) {
                    Mystcraft.LOGGER.warn("[blueprint] {} is not a {} symbol; ignoring its weight", e.getKey(), category.id());
                    continue;
                }
                out.put(e.getKey(), e.getValue());
            }
            return out;
        }

        static @Nullable Identifier pick(RandomSource random, Map<Identifier, Integer> weights) {
            int total = 0;
            for (int w : weights.values()) total += w;
            if (total <= 0) return null;
            int roll = random.nextInt(total);
            for (Map.Entry<Identifier, Integer> e : weights.entrySet()) {
                roll -= e.getValue();
                if (roll < 0) return e.getKey();
            }
            return null;
        }

        static int pickCount(RandomSource random, Map<String, Integer> weights, int fallback) {
            int total = 0;
            for (int w : weights.values()) total += w;
            if (total <= 0) return fallback;
            int roll = random.nextInt(total);
            for (Map.Entry<String, Integer> e : weights.entrySet()) {
                roll -= e.getValue();
                if (roll < 0) {
                    try {
                        return Integer.parseInt(e.getKey());
                    } catch (NumberFormatException ex) {
                        return fallback;
                    }
                }
            }
            return fallback;
        }

        static boolean chance(RandomSource random, int percent) {
            return percent > 0 && random.nextInt(100) < percent;
        }

        /** Adds a discovered page if its symbol fits the instability budget; returns whether it was added. */
        boolean add(SymbolPage page) {
            page = page.asDiscovered(true);
            int cost = 0;
            for (Identifier id : page.flatten()) {
                AgeSymbol symbol = SymbolRegistry.get(id);
                if (symbol != null) cost += symbol.instabilityModifier(1);
            }
            if (instability + cost > budget) {
                Mystcraft.LOGGER.info("[blueprint] dropping {} (instability {} over budget {})", page.flatten(), instability + cost, budget);
                return false;
            }
            instability += cost;
            discovered.add(page);
            return true;
        }

        /** Rolls up to {@code count} distinct symbols from the weights, honouring the gate, and adds them. */
        int addRandom(SymbolCategory category, RandomSource random, Map<Identifier, Integer> weights, int count,
                      java.util.function.Predicate<Identifier> gate, java.util.function.Function<Identifier, SymbolPage> page) {
            Map<Identifier, Integer> pool = new LinkedHashMap<>(weights);
            pool.keySet().removeIf(id -> !gate.test(id));
            int added = 0;
            for (int i = 0; i < count && !pool.isEmpty(); i++) {
                Identifier id = pick(random, pool);
                if (id == null) break;
                pool.remove(id);
                if (add(page.apply(id))) added++;
            }
            return added;
        }

        // --- categories -----------------------------------------------------------------------------------------

        void fillTerrain() {
            if (written(SymbolCategory.TERRAIN)) return;
            RandomSource random = random(SymbolCategory.TERRAIN);
            Identifier id = pick(random, weights(SymbolCategory.TERRAIN));
            if (id == null) id = MystIds.id("terrain_normal");
            terrain = id;
            add(new SymbolPage(id, materials(random, id, true), true));
        }

        /** Rolls a material (and No Sea for terrain) for a page whose symbol takes blocks. */
        List<Identifier> materials(RandomSource random, Identifier symbolId, boolean isTerrain) {
            AgeSymbol symbol = SymbolRegistry.get(symbolId);
            List<Identifier> out = new ArrayList<>();
            if (symbol == null || !symbol.accepts().contains(ModifierSlot.BLOCK)) return out;
            int roll = random.nextInt(100);
            int common = WorldBuildingConfig.MATERIAL_COMMON_CHANCE.get();
            int exotic = WorldBuildingConfig.MATERIAL_EXOTIC_CHANCE.get();
            boolean wantCommon = roll < common, wantExotic = !wantCommon && roll < common + exotic;
            if (wantCommon || wantExotic) {
                List<Identifier> candidates = new ArrayList<>();
                for (AgeSymbol material : SymbolRegistry.inCategory(SymbolCategory.MATERIALS)) {
                    if (!(material instanceof BlockSymbols.BlockSymbol block) || !symbol.takes(block)) continue;
                    Integer best = null;
                    for (BlockCategory c : symbol.blockCategories()) {
                        Integer rank = block.categoryRanks().get(c);
                        if (rank != null && (best == null || rank < best)) best = rank;
                    }
                    if (best == null) continue; // connect-only in every category the page takes
                    if (wantCommon ? best <= 2 : best > 2) candidates.add(material.id());
                }
                if (!candidates.isEmpty()) out.add(candidates.get(random.nextInt(candidates.size())));
            }
            if (isTerrain && chance(random, WorldBuildingConfig.NO_SEA_CHANCE.get()) && symbol.takes(SymbolRegistry.get(NO_SEA))) out.add(NO_SEA);
            return out;
        }

        void fillLayout() {
            if (written(SymbolCategory.BIOME_LAYOUT)) return;
            RandomSource random = random(SymbolCategory.BIOME_LAYOUT);
            Identifier id = pick(random, weights(SymbolCategory.BIOME_LAYOUT));
            if (id == null) id = BIOME_NATIVE;
            layout = id;
            add(SymbolPage.of(id));
        }

        void fillBiomes() {
            if (written(SymbolCategory.BIOMES) || BIOME_NATIVE.equals(layout) || layout == null) return;
            RandomSource random = random(SymbolCategory.BIOMES);
            WorldBuildingConfig.Category config = WorldBuildingConfig.CATEGORIES.get(SymbolCategory.BIOMES);
            int count = pickCount(random, WorldBuildingConfig.parseKeyedWeights(WorldBuildingConfig.BIOME_COUNT_WEIGHTS.get()), 2);
            count = Math.max(config.min.get(), Math.min(config.max.get(), count));
            if (BIOME_SINGLE.equals(layout)) count = 1;
            else count = Math.max(count, 2); // layouts other than Single/Native want more than one biome
            Map<Identifier, Integer> pool = new LinkedHashMap<>();
            Map<Identifier, Integer> overrides = WorldBuildingConfig.parseWeights(config.weights.get());
            boolean nether = TERRAIN_NETHER.equals(terrain), end = TERRAIN_END.equals(terrain);
            for (AgeSymbol symbol : SymbolRegistry.inCategory(SymbolCategory.BIOMES)) {
                if (!(symbol instanceof BiomeSymbols.BiomeSymbol biome)) continue;
                Identifier key = biome.biomeKey().identifier();
                int weight;
                if (BiomeSymbols.isEndOrVoid(biome.biomeKey())) {
                    weight = end && !key.getPath().equals("the_void") ? WorldBuildingConfig.BIOME_END_WEIGHT.get() : 0;
                } else if (key.getNamespace().equals("minecraft") && NETHER_BIOMES.contains(key.getPath())) {
                    weight = nether ? WorldBuildingConfig.BIOME_NETHER_WEIGHT.get() : 0;
                } else {
                    weight = WorldBuildingConfig.BIOME_OVERWORLD_WEIGHT.get();
                }
                if (overrides.containsKey(symbol.id())) weight = overrides.get(symbol.id());
                if (weight > 0) pool.put(symbol.id(), weight);
            }
            addRandom(SymbolCategory.BIOMES, random, pool, count, id -> true, SymbolPage::of);
        }

        void fillLighting() {
            if (lightingWritten || lightingDone) return;
            RandomSource random = random(SymbolCategory.LIGHTING);
            Identifier id = pick(random, weights(SymbolCategory.LIGHTING));
            if (id == null || !add(SymbolPage.of(id))) add(SymbolPage.of(LIGHTING_NORMAL));
            lightingDone = true;
        }

        void fillCelestials() {
            if (written(SymbolCategory.CELESTIALS)) return;
            RandomSource random = random(SymbolCategory.CELESTIALS);
            Map<Identifier, Integer> weights = weights(SymbolCategory.CELESTIALS);
            int max = WorldBuildingConfig.CATEGORIES.get(SymbolCategory.CELESTIALS).max.get();
            int count = 0;

            // exactly one sun; a dark sun needs bright lighting, which only the filler may change
            Map<Identifier, Integer> suns = subset(weights, "sun_");
            Identifier sun = pick(random, suns);
            if (sun == null) sun = SUN_NORMAL;
            if (SUN_DARK.equals(sun)) {
                // no light from the sky: the Age is lit by bright lighting instead, or the sun stays normal
                boolean brightened = !lightingWritten && add(SymbolPage.of(LIGHTING_BRIGHT));
                if (brightened) lightingDone = true;
                else sun = SUN_NORMAL;
            }
            if (add(new SymbolPage(sun, celestialModifiers(random, sun, true), true))) count++;

            int moons = pickCount(random, WorldBuildingConfig.parseKeyedWeights(WorldBuildingConfig.MOON_COUNT_WEIGHTS.get()), 1);
            Map<Identifier, Integer> moonWeights = subset(weights, "moon_");
            for (int i = 0; i < moons && count < max; i++) {
                Identifier moon = pick(random, moonWeights);
                if (moon != null && add(new SymbolPage(moon, celestialModifiers(random, moon, true), true))) count++;
            }
            int stars = pickCount(random, WorldBuildingConfig.parseKeyedWeights(WorldBuildingConfig.STARFIELD_COUNT_WEIGHTS.get()), 1);
            Map<Identifier, Integer> starWeights = subset(weights, "stars_");
            for (int i = 0; i < stars && count < max; i++) {
                Identifier field = pick(random, starWeights);
                if (field != null && add(new SymbolPage(field, celestialModifiers(random, field, false), true))) count++;
            }
            Identifier rainbow = MystIds.id("rainbow");
            if (count < max && weights.containsKey(rainbow) && chance(random, weights.get(rainbow))) {
                add(new SymbolPage(rainbow, celestialModifiers(random, rainbow, false), true));
            }
        }

        static Map<Identifier, Integer> subset(Map<Identifier, Integer> weights, String prefix) {
            Map<Identifier, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<Identifier, Integer> e : weights.entrySet()) if (e.getKey().getPath().startsWith(prefix)) out.put(e.getKey(), e.getValue());
            return out;
        }

        List<Identifier> celestialModifiers(RandomSource random, Identifier id, boolean sunset) {
            AgeSymbol symbol = SymbolRegistry.get(id);
            List<Identifier> out = new ArrayList<>();
            if (symbol == null) return out;
            if (symbol.accepts().contains(ModifierSlot.DIRECTION) && chance(random, WorldBuildingConfig.CELESTIAL_DIRECTION_CHANCE.get())) {
                out.add(DIRECTIONS.get(random.nextInt(DIRECTIONS.size())));
            }
            if (symbol.accepts().contains(ModifierSlot.PHASE) && chance(random, WorldBuildingConfig.CELESTIAL_PHASE_CHANCE.get())) {
                out.add(PHASES.get(random.nextInt(PHASES.size())));
            }
            if (symbol.accepts().contains(ModifierSlot.LENGTH)) {
                Map<String, Integer> lengths = WorldBuildingConfig.parseKeyedWeights(WorldBuildingConfig.CELESTIAL_LENGTH_WEIGHTS.get());
                int total = 0;
                for (int w : lengths.values()) total += w;
                if (total > 0) {
                    int roll = random.nextInt(total);
                    for (Map.Entry<String, Integer> e : lengths.entrySet()) {
                        roll -= e.getValue();
                        if (roll < 0) {
                            if (!e.getKey().equals("none")) {
                                Identifier length = WorldBuildingConfig.parseId(e.getKey());
                                if (length != null && SymbolRegistry.contains(length)) out.add(length);
                            }
                            break;
                        }
                    }
                }
            }
            if (sunset && symbol.accepts().contains(ModifierSlot.SUNSET) && chance(random, WorldBuildingConfig.CELESTIAL_SUNSET_CHANCE.get())) {
                out.add(randomColor(random));
                out.add(SUNSET);
            }
            return out;
        }

        static Identifier randomColor(RandomSource random) {
            List<String> colors = ModifierSymbols.colorIds();
            return MystIds.id(colors.get(random.nextInt(colors.size())));
        }

        /** Colour categories: each option independently by its weight (as a percent), one colour or a two-colour gradient. */
        void fillColors(SymbolCategory category) {
            if (written(category)) return;
            RandomSource random = random(category);
            WorldBuildingConfig.Category config = WorldBuildingConfig.CATEGORIES.get(category);
            if (!chance(random, config.chance.get())) return;
            int max = config.max.get();
            int added = 0;
            for (Map.Entry<Identifier, Integer> e : weights(category).entrySet()) {
                if (added >= max) break;
                if (!chance(random, e.getValue())) continue;
                AgeSymbol symbol = SymbolRegistry.get(e.getKey());
                List<Identifier> modifiers = new ArrayList<>();
                if (symbol != null && symbol.accepts().contains(ModifierSlot.COLOR)) {
                    modifiers.add(randomColor(random));
                    if (symbol.accepts().contains(ModifierSlot.GRADIENT) && chance(random, WorldBuildingConfig.COLOR_GRADIENT_CHANCE.get())) {
                        modifiers.add(GRADIENT);
                        modifiers.add(randomColor(random));
                        modifiers.add(GRADIENT);
                    }
                }
                if (add(new SymbolPage(e.getKey(), modifiers, true))) added++;
            }
        }

        /** Optional categories with plain weighted picks: defaults when empty, then random extras by chance. */
        void fillSimple(SymbolCategory category) {
            if (written(category)) return;
            RandomSource random = random(category);
            WorldBuildingConfig.Category config = WorldBuildingConfig.CATEGORIES.get(category);
            int added = addDefaults(category, config);
            if (!chance(random, config.chance.get())) return;
            int count = rollCount(random, config, added);
            addRandom(category, random, weights(category), count, this::allowed, SymbolPage::of);
        }

        int addDefaults(SymbolCategory category, WorldBuildingConfig.Category config) {
            int added = 0;
            for (String entry : config.defaults.get()) {
                Identifier id = WorldBuildingConfig.parseId(entry);
                AgeSymbol symbol = id == null ? null : SymbolRegistry.get(id);
                if (symbol == null || symbol.category() != category) {
                    Mystcraft.LOGGER.warn("[blueprint] default '{}' is not a {} symbol; ignored", entry, category.id());
                    continue;
                }
                if (add(SymbolPage.of(id))) added++;
            }
            return added;
        }

        /** 1 (50 %), 2 (35 %), 3 (15 %) extra pages, at least up to the category minimum, within the room left under max. */
        static int rollCount(RandomSource random, WorldBuildingConfig.Category config, int alreadyAdded) {
            int room = Math.max(0, config.max.get() - alreadyAdded);
            if (room == 0) return 0;
            int roll = random.nextInt(100);
            int count = roll < 50 ? 1 : roll < 85 ? 2 : 3;
            count = Math.max(count, config.min.get() - alreadyAdded);
            return Math.min(count, room);
        }

        /** Hard gates for random picks. */
        boolean allowed(Identifier id) {
            if (NETHER_FORTRESS.equals(id)) return TERRAIN_NETHER.equals(terrain);
            return true;
        }

        void fillFeatures() {
            if (written(SymbolCategory.FEATURES)) return;
            RandomSource random = random(SymbolCategory.FEATURES);
            WorldBuildingConfig.Category config = WorldBuildingConfig.CATEGORIES.get(SymbolCategory.FEATURES);
            int added = addDefaults(SymbolCategory.FEATURES, config);
            if (chance(random, WorldBuildingConfig.STAR_FISSURE_CHANCE.get()) && SymbolRegistry.contains(STAR_FISSURE)) {
                if (add(SymbolPage.of(STAR_FISSURE))) added++;
            }
            if (!chance(random, config.chance.get())) return;
            int count = rollCount(random, config, added);
            addRandom(SymbolCategory.FEATURES, random, weights(SymbolCategory.FEATURES), count, this::allowed,
                    id -> new SymbolPage(id, materials(random, id, false), true));
        }
    }

    /** Ids of every symbol the discovered pages introduce (for logs and the summary). */
    public static Set<Identifier> discoveredSymbols(List<ItemStack> pages) {
        Set<Identifier> out = new HashSet<>();
        for (ItemStack page : pages) {
            SymbolPage symbolPage = PageItem.getSymbolPage(page);
            if (symbolPage != null && symbolPage.discovered()) out.addAll(symbolPage.flatten());
        }
        return out;
    }
}

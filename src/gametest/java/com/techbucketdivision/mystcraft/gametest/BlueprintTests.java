package com.techbucketdivision.mystcraft.gametest;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeBlueprint;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.SymbolCategory;
import com.techbucketdivision.mystcraft.config.WorldBuildingConfig;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.item.component.SymbolPage;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The Age blueprint filler and organiser (plan §2, §3). Pure functions: no Age is created here. */
@ForEachTest(groups = "blueprint")
public class BlueprintTests {

    private static List<ItemStack> book(Identifier... symbols) {
        List<ItemStack> pages = new ArrayList<>();
        pages.add(PageItem.createLinkPanel());
        for (Identifier id : symbols) pages.add(PageItem.createSymbolPage(id));
        return pages;
    }

    private static boolean has(List<Identifier> ids, String prefix) {
        return ids.stream().anyMatch(id -> id.getPath().startsWith(prefix));
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Same seed, same book; a different seed gives a different book; 200 seeds never void terrain, always a sun, within the instability budget")
    static void fillIsDeterministicAndSafe(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            List<ItemStack> empty = book();
            List<Identifier> a = AgeBlueprint.flatten(AgeBlueprint.fill(empty, 42L).pages());
            List<Identifier> b = AgeBlueprint.flatten(AgeBlueprint.fill(empty, 42L).pages());
            helper.assertValueEqual(a, b, "same seed -> same symbols");
            int different = 0;
            List<Identifier> previous = a;
            int budget = WorldBuildingConfig.INSTABILITY_BUDGET.get();
            int fissures = 0, extras = 0;
            for (long seed = 1; seed <= 200; seed++) {
                AgeBlueprint.Result r = AgeBlueprint.fill(empty, seed);
                List<Identifier> ids = AgeBlueprint.flatten(r.pages());
                if (!ids.equals(previous)) different++;
                previous = ids;
                helper.assertTrue(!ids.contains(MystIds.id("terrain_void")), "seed " + seed + " picked void terrain");
                helper.assertTrue(has(ids, "terrain_"), "seed " + seed + " has terrain");
                helper.assertTrue(has(ids, "sun_"), "seed " + seed + " has a sun");
                helper.assertTrue(has(ids, "lighting_"), "seed " + seed + " has lighting");
                helper.assertTrue(has(ids, "biome_"), "seed " + seed + " has a biome layout");
                helper.assertTrue(AgeBlueprint.missing(r.pages()).isEmpty(), "seed " + seed + " leaves a required category empty: " + AgeBlueprint.missing(r.pages()));
                helper.assertTrue(r.discoveredInstability() <= budget, "seed " + seed + " over budget: " + r.discoveredInstability());
                helper.assertTrue(r.pages().stream().filter(PageItem::isSymbolPage).allMatch(PageItem::isDiscovered), "seed " + seed + ": every filled page is discovered");
                if (ids.contains(MystIds.id("sun_dark"))) helper.assertTrue(ids.contains(MystIds.id("lighting_bright")), "seed " + seed + ": dark sun without bright lighting");
                if (ids.contains(MystIds.id("nether_fortress"))) helper.assertTrue(ids.contains(MystIds.id("terrain_nether")), "seed " + seed + ": fortress without nether terrain");
                if (ids.contains(MystIds.id("star_fissure"))) fissures++;
                if (ids.stream().anyMatch(id -> SymbolRegistry.get(id) != null && SymbolRegistry.get(id).category() == SymbolCategory.EFFECTS)) extras++;
            }
            Mystcraft.LOGGER.info("[gametest] blueprint stress: {} of 200 differ from the previous seed, {} star fissures, {} Ages with effects", different, fissures, extras);
            helper.assertTrue(different > 150, "seeds produce different Ages (" + different + ")");
            helper.assertTrue(fissures > 5 && fissures < 50, "star fissure in roughly 10 % of Ages (" + fissures + ")");
        });
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Categories the author wrote are never touched; the rest is filled; pages come out organised by category with player pages first")
    static void playerCategoriesAreUntouched(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            Identifier flat = MystIds.id("terrain_flat"), dark = MystIds.id("lighting_dark"), moon = MystIds.id("moon_normal"), rain = MystIds.id("weather_rain");
            List<ItemStack> written = book(rain, moon, flat, dark); // deliberately out of category order
            AgeBlueprint.Result result = AgeBlueprint.fill(written, 7L);
            List<ItemStack> pages = result.pages();
            Map<SymbolCategory, List<ItemStack>> by = AgeBlueprint.byCategory(pages);
            helper.assertValueEqual(by.get(SymbolCategory.TERRAIN).size(), 1, "terrain untouched (one page)");
            helper.assertValueEqual(PageItem.getSymbolId(by.get(SymbolCategory.TERRAIN).getFirst()), flat, "the author's terrain stays");
            helper.assertValueEqual(by.get(SymbolCategory.LIGHTING).size(), 1, "lighting untouched");
            helper.assertValueEqual(by.get(SymbolCategory.CELESTIALS).size(), 1, "celestials untouched: no sun added next to the author's moon");
            helper.assertValueEqual(by.get(SymbolCategory.WEATHER).size(), 1, "weather untouched");
            helper.assertTrue(by.containsKey(SymbolCategory.BIOME_LAYOUT), "biome layout filled");
            for (SymbolCategory c : List.of(SymbolCategory.TERRAIN, SymbolCategory.LIGHTING, SymbolCategory.CELESTIALS, SymbolCategory.WEATHER)) {
                helper.assertTrue(!PageItem.isDiscovered(by.get(c).getFirst()), c + " page is the author's");
            }
            // organised: link panel, then categories in order
            helper.assertTrue(PageItem.isLinkPanel(pages.getFirst()), "link panel first");
            int last = -1;
            for (ItemStack page : pages) {
                AgeSymbol symbol = PageItem.getSymbol(page);
                if (symbol == null) continue;
                helper.assertTrue(symbol.category().ordinal() >= last, "pages in category order (" + symbol.id() + ")");
                last = symbol.category().ordinal();
            }
            helper.assertValueEqual(PageItem.getSymbolId(pages.get(1)), flat, "terrain page right after the panel");
        });
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Gates: nether terrain allows nether biomes, native layout needs no biomes, Single takes one biome, other layouts at least two")
    static void biomeGates(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            int netherAges = 0, netherBiomes = 0;
            for (long seed = 1; seed <= 60; seed++) {
                List<Identifier> ids = AgeBlueprint.flatten(AgeBlueprint.fill(book(MystIds.id("terrain_normal"), MystIds.id("biome_medium")), seed).pages());
                for (Identifier id : ids) {
                    String path = id.getPath();
                    helper.assertTrue(!(path.contains("nether_wastes") || path.contains("soul_sand_valley") || path.contains("crimson_forest")
                            || path.contains("warped_forest") || path.contains("basalt_deltas")), "seed " + seed + ": nether biome with normal terrain: " + id);
                    helper.assertTrue(!path.startsWith("biome_minecraft_the_end") && !path.contains("end_highlands"), "seed " + seed + ": end biome with normal terrain: " + id);
                }
                long biomes = ids.stream().filter(id -> SymbolRegistry.get(id) != null && SymbolRegistry.get(id).category() == SymbolCategory.BIOMES).count();
                helper.assertTrue(biomes >= 2 && biomes <= 4, "seed " + seed + ": medium layout gets 2-4 biomes (" + biomes + ")");
            }
            for (long seed = 1; seed <= 60; seed++) {
                List<Identifier> ids = AgeBlueprint.flatten(AgeBlueprint.fill(book(MystIds.id("terrain_nether"), MystIds.id("biome_medium")), seed).pages());
                netherAges++;
                if (ids.stream().anyMatch(id -> id.getPath().contains("nether_wastes") || id.getPath().contains("crimson_forest")
                        || id.getPath().contains("warped_forest") || id.getPath().contains("soul_sand_valley") || id.getPath().contains("basalt_deltas"))) netherBiomes++;
            }
            Mystcraft.LOGGER.info("[gametest] nether terrain: {} of {} Ages got a nether biome", netherBiomes, netherAges);
            List<ItemStack> single = AgeBlueprint.fill(book(MystIds.id("biome_single")), 3L).pages();
            helper.assertValueEqual(AgeBlueprint.byCategory(single).getOrDefault(SymbolCategory.BIOMES, List.of()).size(), 1, "Single layout takes exactly one biome");
            List<ItemStack> nat = AgeBlueprint.fill(book(MystIds.id("biome_native")), 3L).pages();
            helper.assertTrue(!AgeBlueprint.byCategory(nat).containsKey(SymbolCategory.BIOMES), "Native layout gets no biome pages");
            helper.assertTrue(AgeBlueprint.missing(nat).isEmpty(), "Native layout: biomes not reported missing");
        });
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Organiser: discovered pages follow player pages inside a category, modifiers stay on their page, blanks go last")
    static void organiserKeepsPagesWhole(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            Identifier sun = MystIds.id("sun_normal"), north = MystIds.id("mod_north");
            ItemStack withMods = PageItem.createSymbolPage(new SymbolPage(sun, List.of(north), false));
            ItemStack discoveredMoon = PageItem.createDiscoveredPage(MystIds.id("moon_normal"), List.of());
            ItemStack blank = PageItem.createBlankPage();
            List<ItemStack> organised = AgeBlueprint.organise(List.of(blank, discoveredMoon, PageItem.createLinkPanel(), withMods, PageItem.createSymbolPage(MystIds.id("terrain_flat"))));
            helper.assertTrue(PageItem.isLinkPanel(organised.get(0)), "panel first");
            helper.assertValueEqual(PageItem.getSymbolId(organised.get(1)), MystIds.id("terrain_flat"), "terrain before celestials");
            helper.assertValueEqual(PageItem.getSymbolId(organised.get(2)), sun, "player celestial before the discovered one");
            helper.assertValueEqual(PageItem.getModifiers(organised.get(2)), List.of(north), "modifiers stay on the page");
            helper.assertValueEqual(PageItem.getSymbolId(organised.get(3)), MystIds.id("moon_normal"), "discovered celestial after");
            helper.assertTrue(PageItem.isBlank(organised.get(4)), "blank last");
            helper.assertValueEqual(AgeBlueprint.flatten(organised), List.of(MystIds.id("terrain_flat"), north, sun, MystIds.id("moon_normal")), "flatten order");
        });
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "QA shelf: every symbol of the /myst-qa-shelf matrix is registered and every modifier is taken by its page")
    static void qaShelfCasesResolve(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            for (var qa : com.techbucketdivision.mystcraft.command.QaShelf.cases()) {
                for (SymbolPage page : qa.pages()) {
                    AgeSymbol symbol = page.resolve();
                    helper.assertNotNull(symbol, qa.title() + ": unknown symbol " + page.symbol());
                    for (Identifier id : page.modifiers()) {
                        AgeSymbol modifier = SymbolRegistry.get(id);
                        helper.assertNotNull(modifier, qa.title() + ": unknown modifier " + id);
                        helper.assertTrue(symbol.takes(modifier), qa.title() + ": " + symbol.id() + " does not take " + id);
                    }
                }
            }
        });
        helper.succeed();
    }
}

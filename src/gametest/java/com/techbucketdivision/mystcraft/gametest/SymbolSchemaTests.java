package com.techbucketdivision.mystcraft.gametest;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.BlockCategory;
import com.techbucketdivision.mystcraft.api.symbol.ModifierSlot;
import com.techbucketdivision.mystcraft.api.symbol.SymbolCategory;
import com.techbucketdivision.mystcraft.age.AgeBlueprint;
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

import java.util.List;
import java.util.Set;

/** World-building schema: categories, modifier slots and the symbol page component (plan §2, §3). */
@ForEachTest(groups = "schema")
public class SymbolSchemaTests {

    private static AgeSymbol symbol(ExtendedGameTestHelper helper, String path) {
        AgeSymbol s = SymbolRegistry.get(MystIds.id(path));
        helper.assertNotNull(s, path + " is registered");
        return s;
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Every registered symbol has a category; modifier categories fill a slot, primary ones never do")
    static void everySymbolHasACategory(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            int modifiers = 0, primaries = 0;
            for (AgeSymbol s : SymbolRegistry.all()) {
                SymbolCategory category = s.category();
                if (category.isModifier()) {
                    modifiers++;
                    helper.assertTrue(s.fills() != null, s.id() + " is a modifier but fills no slot");
                } else {
                    primaries++;
                    helper.assertTrue(s.fills() == null, s.id() + " is a primary symbol but fills " + s.fills());
                }
            }
            Mystcraft.LOGGER.info("[gametest] schema: {} primary symbols, {} modifiers", primaries, modifiers);
            helper.assertTrue(primaries > 50 && modifiers > 30, "built-in symbols are all classified");
            for (SymbolCategory c : SymbolCategory.values()) {
                helper.assertTrue(!SymbolRegistry.inCategory(c).isEmpty(), "category " + c + " has symbols");
            }
        });
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Built-in categories and slots: terrain takes materials, suns take direction/phase/length/sunset, sky colour takes colours and gradients, biomes/blocks are classified")
    static void builtinSchemaIsAsDesigned(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            AgeSymbol terrain = symbol(helper, "terrain_normal");
            helper.assertValueEqual(terrain.category(), SymbolCategory.TERRAIN, "terrain category");
            helper.assertTrue(terrain.accepts().contains(ModifierSlot.BLOCK), "terrain takes a material");
            helper.assertTrue(terrain.blockCategories().containsAll(Set.of(BlockCategory.TERRAIN, BlockCategory.SEA)), "terrain takes terrain + sea blocks: " + terrain.blockCategories());

            AgeSymbol sun = symbol(helper, "sun_normal");
            helper.assertValueEqual(sun.category(), SymbolCategory.CELESTIALS, "sun category");
            helper.assertValueEqual(sun.accepts(), Set.of(ModifierSlot.DIRECTION, ModifierSlot.PHASE, ModifierSlot.LENGTH, ModifierSlot.SUNSET,
                    ModifierSlot.GRADIENT, ModifierSlot.COLOR), "sun slots (a sunset is made of colours / a gradient)");

            AgeSymbol sky = symbol(helper, "color_sky");
            helper.assertValueEqual(sky.category(), SymbolCategory.SKY_COLORS, "sky colour category");
            helper.assertValueEqual(symbol(helper, "color_grass").category(), SymbolCategory.WORLD_COLORS, "grass colour category");
            helper.assertTrue(sky.accepts().containsAll(Set.of(ModifierSlot.GRADIENT, ModifierSlot.COLOR, ModifierSlot.LENGTH)), "sky colour takes gradient, colour, length: " + sky.accepts());

            AgeSymbol north = symbol(helper, "mod_north");
            AgeSymbol red = symbol(helper, "mod_color_red");
            AgeSymbol gradient = symbol(helper, "mod_gradient");
            AgeSymbol horizon = symbol(helper, "color_horizon");
            helper.assertValueEqual(north.fills(), ModifierSlot.DIRECTION, "north fills direction");
            helper.assertValueEqual(red.fills(), ModifierSlot.COLOR, "red fills colour");
            helper.assertValueEqual(gradient.fills(), ModifierSlot.GRADIENT, "gradient fills gradient");
            helper.assertValueEqual(horizon.fills(), ModifierSlot.SUNSET, "sunset colour fills sunset");
            helper.assertValueEqual(symbol(helper, "mod_half").fills(), ModifierSlot.LENGTH, "half length fills length");
            helper.assertValueEqual(symbol(helper, "mod_noon").fills(), ModifierSlot.PHASE, "zenith fills phase");

            helper.assertTrue(sky.takes(red) && sky.takes(gradient) && !sky.takes(north), "sky colour takes red + gradient, not north");
            helper.assertTrue(sun.takes(north) && sun.takes(horizon) && sun.takes(red), "sun takes north, sunset and the colour a sunset is made of");
            helper.assertTrue(!terrain.takes(north), "terrain takes no direction");

            AgeSymbol noSea = symbol(helper, "no_sea");
            helper.assertValueEqual(noSea.category(), SymbolCategory.MATERIALS, "no sea is a material");
            helper.assertTrue(terrain.takes(noSea), "terrain takes No Sea");
            helper.assertTrue(!symbol(helper, "lakes_surface").takes(noSea), "lakes do not take a sea block");

            helper.assertValueEqual(symbol(helper, "villages").category(), SymbolCategory.STRUCTURES, "villages are structures");
            helper.assertValueEqual(symbol(helper, "caves").category(), SymbolCategory.FEATURES, "caves are features");
            helper.assertValueEqual(symbol(helper, "env_meteors").category(), SymbolCategory.EFFECTS, "meteors are effects");
            helper.assertValueEqual(symbol(helper, "weather_rain").category(), SymbolCategory.WEATHER, "rain is weather");
            helper.assertValueEqual(symbol(helper, "lighting_dark").category(), SymbolCategory.LIGHTING, "dark lighting");
            helper.assertValueEqual(symbol(helper, "biome_medium").category(), SymbolCategory.BIOME_LAYOUT, "medium biomes is a layout");

            // registry-late symbols: biome wrappers and block wrappers
            AgeSymbol plains = SymbolRegistry.get(MystIds.id("biome_minecraft_plains"));
            if (plains == null) {
                for (AgeSymbol s : SymbolRegistry.all()) if (s.id().getPath().startsWith("biome_")) { plains = s; break; }
            }
            helper.assertNotNull(plains, "a biome wrapper is registered");
            helper.assertValueEqual(plains.category(), SymbolCategory.BIOMES, "biome wrapper category");
            AgeSymbol block = null;
            for (AgeSymbol s : SymbolRegistry.all()) if (s.id().getPath().startsWith("block_")) { block = s; break; }
            helper.assertNotNull(block, "a block wrapper is registered");
            helper.assertValueEqual(block.category(), SymbolCategory.MATERIALS, "block wrapper category");
            helper.assertValueEqual(block.fills(), ModifierSlot.BLOCK, "block wrapper fills the material slot");
        });
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Symbol page component: modifiers and the discovered flag survive a copy; a book flattens each page to modifiers-then-symbol")
    static void symbolPageRoundTripAndFlattening(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            Identifier sun = MystIds.id("sun_normal"), north = MystIds.id("mod_north"), half = MystIds.id("mod_half");
            ItemStack page = PageItem.createSymbolPage(new SymbolPage(sun, List.of(north, half), false));
            ItemStack copy = page.copy();
            SymbolPage read = PageItem.getSymbolPage(copy);
            helper.assertNotNull(read, "copied page has a symbol component");
            helper.assertValueEqual(read.symbol(), sun, "symbol kept");
            helper.assertValueEqual(read.modifiers(), List.of(north, half), "modifiers kept in order");
            helper.assertTrue(!read.discovered(), "player page is not discovered");
            helper.assertTrue(PageItem.isSymbolPage(copy) && !PageItem.isBlank(copy), "still a symbol page");

            ItemStack discovered = PageItem.createDiscoveredPage(MystIds.id("terrain_flat"), List.of());
            helper.assertTrue(PageItem.isDiscovered(discovered), "discovered flag set");
            helper.assertTrue(!PageItem.isDiscovered(page), "player page not discovered");

            // NBT round trip through the level's registry access
            var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var encoded = ItemStack.CODEC.encodeStart(ops, page).getOrThrow();
            ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
            helper.assertValueEqual(PageItem.getSymbolPage(decoded), read, "NBT round trip");

            List<Identifier> flat = AgeBlueprint.flatten(List.of(PageItem.createLinkPanel(), page, discovered));
            helper.assertValueEqual(flat, List.of(north, half, sun, MystIds.id("terrain_flat")), "flattened build order");
        });
        helper.succeed();
    }
}

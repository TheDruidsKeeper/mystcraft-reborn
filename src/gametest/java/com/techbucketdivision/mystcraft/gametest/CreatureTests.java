package com.techbucketdivision.mystcraft.gametest;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeBlueprint;
import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.CreatureDifficulty;
import com.techbucketdivision.mystcraft.api.symbol.CreatureGroup;
import com.techbucketdivision.mystcraft.api.symbol.ModifierSlot;
import com.techbucketdivision.mystcraft.api.symbol.SymbolCategory;
import com.techbucketdivision.mystcraft.api.symbol.logic.CreatureController;
import com.techbucketdivision.mystcraft.creature.CreatureRules;
import com.techbucketdivision.mystcraft.instability.InstabilityManager;
import com.techbucketdivision.mystcraft.item.DescriptiveBookItem;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.item.component.SymbolPage;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import com.techbucketdivision.mystcraft.symbol.symbols.CreatureSymbols;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creature category (plan §10): schema, controllers, spawn scaling, caps, difficulty and the Frenzy card. */
@ForEachTest(groups = "creatures")
public class CreatureTests {

    private static AgeSymbol symbol(ExtendedGameTestHelper helper, String path) {
        AgeSymbol s = SymbolRegistry.get(MystIds.id(path));
        helper.assertNotNull(s, path + " is registered");
        return s;
    }

    private static SymbolPage page(String symbol, String... modifiers) {
        List<Identifier> mods = new ArrayList<>();
        for (String m : modifiers) mods.add(MystIds.id(m));
        return new SymbolPage(MystIds.id(symbol), mods, false);
    }

    /** Binds a fresh Age from the pages and resolves its controller (no level needed). */
    private static AgeController controller(MinecraftServer server, String title, SymbolPage... pages) {
        List<ItemStack> stacks = new ArrayList<>();
        for (SymbolPage p : pages) stacks.add(PageItem.createSymbolPage(p));
        ItemStack book = DescriptiveBookItem.createBound(server, title, 4242L, stacks);
        AgeData data = DescriptiveBookItem.getAgeData(server, book);
        if (data == null) throw new IllegalStateException("book did not bind");
        AgeController controller = new AgeController(data, server.registryAccess(), false);
        controller.ensureCurrent();
        return controller;
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Creature schema: groups take rate + cap (hostile also difficulty), Lifeless takes nothing, modifiers fill their slots, none carries instability")
    static void creatureSchema(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            AgeSymbol hostile = symbol(helper, "creatures_hostile");
            helper.assertValueEqual(hostile.category(), SymbolCategory.CREATURES, "hostile category");
            helper.assertValueEqual(hostile.accepts(), Set.of(ModifierSlot.RATE, ModifierSlot.CAP, ModifierSlot.DIFFICULTY), "hostile slots");
            helper.assertValueEqual(symbol(helper, "creatures_passive").accepts(), Set.of(ModifierSlot.RATE, ModifierSlot.CAP), "passive slots");
            helper.assertValueEqual(symbol(helper, "creatures_neutral").accepts(), Set.of(ModifierSlot.RATE, ModifierSlot.CAP), "neutral slots");
            helper.assertTrue(symbol(helper, "creatures_none").accepts().isEmpty(), "Lifeless takes no modifier");
            helper.assertValueEqual(symbol(helper, "mod_rate_swarm").fills(), ModifierSlot.RATE, "swarm fills rate");
            helper.assertValueEqual(symbol(helper, "mod_cap_horde").fills(), ModifierSlot.CAP, "horde fills cap");
            helper.assertValueEqual(symbol(helper, "mod_difficulty_brutal").fills(), ModifierSlot.DIFFICULTY, "brutal fills difficulty");
            helper.assertTrue(hostile.takes(symbol(helper, "mod_difficulty_brutal")), "hostile takes brutal");
            helper.assertTrue(!symbol(helper, "creatures_passive").takes(symbol(helper, "mod_difficulty_brutal")), "passive does not take a difficulty");
            for (AgeSymbol s : SymbolRegistry.all()) {
                if (s.id().getPath().startsWith("creatures_") || s.id().getPath().startsWith("mod_rate_")
                        || s.id().getPath().startsWith("mod_cap_") || s.id().getPath().startsWith("mod_difficulty_")) {
                    helper.assertValueEqual(s.instabilityModifier(1), 0, s.id() + " carries no instability (danger comes from the Frenzy card)");
                }
            }
        });
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Group pages resolve to controllers: modifiers set rate / cap / difficulty, an unwritten group has none, Lifeless silences all three")
    static void controllersResolve(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            AgeController age = controller(server, "Creatures A",
                    page("creatures_hostile", "mod_rate_swarm", "mod_cap_horde", "mod_difficulty_brutal"),
                    page("creatures_passive", "mod_rate_none"));
            CreatureController hostile = age.creatures(CreatureGroup.HOSTILE);
            helper.assertNotNull(hostile, "hostile controller");
            helper.assertValueEqual(hostile.rate(), 4f, "swarm rate");
            helper.assertValueEqual(hostile.capFactor(), 4f, "horde cap");
            helper.assertValueEqual(hostile.difficulty(), CreatureDifficulty.BRUTAL, "brutal difficulty");
            CreatureController passive = age.creatures(CreatureGroup.PASSIVE);
            helper.assertNotNull(passive, "passive controller");
            helper.assertValueEqual(passive.rate(), 0f, "rate none");
            helper.assertValueEqual(passive.capFactor(), 1f, "cap default");
            helper.assertTrue(age.creatures(CreatureGroup.NEUTRAL) == null, "neutral not written -> vanilla");

            AgeController lifeless = controller(server, "Creatures B", page("creatures_none"));
            for (CreatureGroup group : CreatureGroup.values()) {
                CreatureController c = lifeless.creatures(group);
                helper.assertNotNull(c, group + " silenced");
                helper.assertValueEqual(c.rate(), 0f, group + " rate 0");
            }
        });
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Spawn lists scale by group rate (0 drops the entry), frenzy multiplies hostiles by 1.5 and raises their difficulty one step")
    static void spawnListsScale(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            WeightedList<MobSpawnSettings.SpawnerData> spawns = WeightedList.of(List.of(
                    new Weighted<>(new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 2, 4), 100),
                    new Weighted<>(new MobSpawnSettings.SpawnerData(EntityType.COW, 2, 4), 10),
                    new Weighted<>(new MobSpawnSettings.SpawnerData(EntityType.ENDERMAN, 1, 1), 10)));
            Map<CreatureGroup, CreatureController> controllers = new EnumMap<>(CreatureGroup.class);
            controllers.put(CreatureGroup.HOSTILE, new CreatureSymbols.Settings(CreatureGroup.HOSTILE, 4f, 1f, CreatureDifficulty.NORMAL));
            controllers.put(CreatureGroup.PASSIVE, new CreatureSymbols.Settings(CreatureGroup.PASSIVE, 0f, 1f, CreatureDifficulty.NORMAL));

            helper.assertValueEqual(weight(CreatureRules.scaleSpawns(spawns, controllers::get, false), EntityType.ZOMBIE), 400, "swarm ×4");
            helper.assertValueEqual(weight(CreatureRules.scaleSpawns(spawns, controllers::get, false), EntityType.COW), 0, "rate none drops cows");
            helper.assertValueEqual(weight(CreatureRules.scaleSpawns(spawns, controllers::get, false), EntityType.ENDERMAN), 10, "neutral (tagged) untouched");
            helper.assertTrue(CreatureRules.scaleSpawns(spawns, g -> null, false) == spawns, "no controllers -> same list");
            helper.assertValueEqual(weight(CreatureRules.scaleSpawns(spawns, g -> null, true), EntityType.ZOMBIE), 150, "frenzy ×1.5");
            helper.assertValueEqual(weight(CreatureRules.scaleSpawns(spawns, g -> null, true), EntityType.COW), 10, "frenzy leaves passives alone");
            helper.assertValueEqual(CreatureRules.difficulty(null, true), CreatureDifficulty.HARD, "frenzy: normal -> hard");
            helper.assertValueEqual(CreatureRules.difficulty(controllers.get(CreatureGroup.HOSTILE), false), CreatureDifficulty.NORMAL, "no frenzy: as written");
            helper.assertValueEqual(CreatureDifficulty.BRUTAL.harder(), CreatureDifficulty.BRUTAL, "brutal is the ceiling");
            helper.assertValueEqual(CreatureGroup.of(EntityType.ENDERMAN), CreatureGroup.NEUTRAL, "enderman is neutral by tag");
            helper.assertValueEqual(CreatureGroup.of(EntityType.ZOMBIE), CreatureGroup.HOSTILE, "zombie is hostile");
            helper.assertValueEqual(CreatureGroup.of(EntityType.COW), CreatureGroup.PASSIVE, "cow is passive");
            helper.assertTrue(CreatureGroup.of(EntityType.ARROW) == null, "arrows are no creature");
        });
        helper.succeed();
    }

    private static int weight(WeightedList<MobSpawnSettings.SpawnerData> spawns, EntityType<?> type) {
        for (Weighted<MobSpawnSettings.SpawnerData> entry : spawns.unwrap()) {
            if (entry.value().type() == type) return entry.weight();
        }
        return 0;
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Population caps scale with the cap factor and the loaded spawn area; difficulty doubles a spawned zombie's health once")
    static void capsAndDifficulty(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            int vanilla = MobCategory.MONSTER.getMaxInstancesPerChunk();
            helper.assertValueEqual(CreatureRules.allowed(MobCategory.MONSTER, CreatureRules.SPAWN_AREA_CHUNKS, null), vanilla, "full area, no controller = vanilla cap");
            CreatureController horde = new CreatureSymbols.Settings(CreatureGroup.HOSTILE, 1f, 4f, CreatureDifficulty.NORMAL);
            CreatureController few = new CreatureSymbols.Settings(CreatureGroup.HOSTILE, 1f, 0.25f, CreatureDifficulty.NORMAL);
            helper.assertValueEqual(CreatureRules.allowed(MobCategory.MONSTER, CreatureRules.SPAWN_AREA_CHUNKS, horde), vanilla * 4, "horde ×4");
            helper.assertValueEqual(CreatureRules.allowed(MobCategory.MONSTER, CreatureRules.SPAWN_AREA_CHUNKS, few), Math.round(vanilla / 4f), "few ÷4");
            helper.assertValueEqual(CreatureRules.allowed(MobCategory.MONSTER, CreatureRules.SPAWN_AREA_CHUNKS / 2, null), Math.round(vanilla / 2f), "half the area, half the cap");

            Mob zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
            float base = zombie.getMaxHealth();
            helper.assertTrue(!CreatureRules.hasDifficulty(zombie), "fresh zombie has no difficulty");
            CreatureRules.applyDifficulty(zombie, CreatureDifficulty.BRUTAL);
            helper.assertTrue(CreatureRules.hasDifficulty(zombie), "difficulty applied");
            helper.assertValueEqual(zombie.getMaxHealth(), base * 2f, "brutal doubles max health");
            helper.assertValueEqual(zombie.getHealth(), base * 2f, "spawned at full health");
            CreatureRules.applyDifficulty(zombie, CreatureDifficulty.BRUTAL);
            helper.assertValueEqual(zombie.getMaxHealth(), base * 2f, "idempotent");
            Mystcraft.LOGGER.info("[gametest] creatures: zombie health {} -> {}", base, zombie.getMaxHealth());
        });
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "The blueprint fills creatures within the schema: Lifeless alone, otherwise distinct groups whose modifiers fit; the Frenzy card is in the harsh deck")
    static void blueprintFillsCreatures(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            int filled = 0, lifeless = 0;
            for (long seed = 1; seed <= 120; seed++) {
                List<ItemStack> pages = AgeBlueprint.fill(List.of(PageItem.createLinkPanel()), seed).pages();
                List<ItemStack> creatures = AgeBlueprint.byCategory(pages).getOrDefault(SymbolCategory.CREATURES, List.of());
                if (creatures.isEmpty()) continue;
                filled++;
                Set<Identifier> seen = new java.util.HashSet<>();
                for (ItemStack stack : creatures) {
                    SymbolPage page = PageItem.getSymbolPage(stack);
                    helper.assertNotNull(page, "symbol page");
                    AgeSymbol symbol = page.resolve();
                    helper.assertNotNull(symbol, "resolves");
                    helper.assertTrue(seen.add(page.symbol()), "seed " + seed + ": group written once: " + AgeBlueprint.flatten(creatures));
                    if (page.symbol().getPath().equals("creatures_none")) {
                        lifeless++;
                        helper.assertValueEqual(creatures.size(), 1, "seed " + seed + ": Lifeless stands alone");
                        helper.assertTrue(page.modifiers().isEmpty(), "Lifeless has no modifiers");
                    }
                    Set<ModifierSlot> used = new java.util.HashSet<>();
                    for (Identifier mod : page.modifiers()) {
                        AgeSymbol m = SymbolRegistry.get(mod);
                        helper.assertNotNull(m, mod + " registered");
                        helper.assertTrue(symbol.takes(m), "seed " + seed + ": " + symbol.id() + " takes " + mod);
                        helper.assertTrue(used.add(m.fills()), "seed " + seed + ": one modifier per slot on " + symbol.id());
                    }
                }
            }
            Mystcraft.LOGGER.info("[gametest] creatures: {} of 120 random Ages have creature pages, {} lifeless", filled, lifeless);
            helper.assertTrue(filled >= 5, "some random Ages get creature pages (" + filled + ")");
            helper.assertTrue(InstabilityManager.deckCards(InstabilityManager.DECK_HARSH).contains(CreatureRules.FRENZY_CARD), "frenzy card is in the harsh deck");
        });
        helper.succeed();
    }
}

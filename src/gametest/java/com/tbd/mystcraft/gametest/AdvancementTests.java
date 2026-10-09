package com.tbd.mystcraft.gametest;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.instability.InstabilityDeaths;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.knowledge.SymbolKnowledge;
import com.tbd.mystcraft.registry.ModDamageTypes;
import com.tbd.mystcraft.registry.ModItems;
import com.tbd.mystcraft.util.MystIds;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

/** The advancement chain: shape of the tree, and the action triggers that award it. */
@ForEachTest(groups = "advancements")
public class AdvancementTests {

    /** The visible chain, root first; each entry's parent is the one before it. */
    private static final List<String> CHAIN = List.of("root", "symbol", "ink", "write", "agebook", "linkbook", "dimension", "unstable", "instability_death");

    private static AdvancementHolder advancement(MinecraftServer server, String path) {
        AdvancementHolder holder = server.getAdvancements().get(MystIds.id(path));
        if (holder == null) throw new IllegalStateException("advancement mystcraft:" + path + " not loaded");
        return holder;
    }

    private static boolean done(ServerPlayer player, String path) {
        return player.getAdvancements().getOrStartProgress(advancement(player.level().getServer(), path)).isDone();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "The advancement tree is one chain in the documented order, quinn hangs hidden off agebook, and only quinn and the instability death are hidden")
    static void advancementTreeShape(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (int i = 1; i < CHAIN.size(); i++) {
            AdvancementHolder holder = advancement(server, CHAIN.get(i));
            String parent = holder.value().parent().map(id -> id.getPath()).orElse("<none>");
            helper.assertValueEqual(parent, CHAIN.get(i - 1), "parent of " + CHAIN.get(i));
        }
        helper.assertValueEqual(advancement(server, "quinn").value().parent().map(id -> id.getPath()).orElse("<none>"), "agebook", "quinn hangs off agebook");
        for (String path : CHAIN) {
            boolean hidden = advancement(server, path).value().display().map(d -> d.isHidden()).orElse(false);
            helper.assertValueEqual(hidden, path.equals("instability_death"), "hidden flag of " + path);
        }
        helper.assertTrue(advancement(server, "quinn").value().display().map(d -> d.isHidden()).orElse(false), "quinn is hidden");
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Awards come from actions: holding a page awards nothing, learning a symbol awards 'symbol'; holding books awards nothing, linking an Unlinked Book awards 'linkbook'; the instability death counts tagged damage and a remembered lightning strike")
    static void advancementsAreAwardedByActions(ExtendedGameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        player.getInventory().add(PageItem.createSymbolPage(MystIds.id("terrain_flat")));
        player.getInventory().add(new ItemStack(ModItems.DESCRIPTIVE_BOOK.get()));
        player.getInventory().add(new ItemStack(ModItems.UNLINKED_BOOK.get()));
        player.inventoryMenu.broadcastChanges();
        helper.assertFalse(done(player, "symbol"), "a page in the inventory awards nothing");
        helper.assertFalse(done(player, "agebook"), "a Descriptive Book in the inventory awards nothing");
        helper.assertFalse(done(player, "linkbook"), "an Unlinked Book in the inventory awards nothing");

        SymbolKnowledge.unlock(player, List.of(MystIds.id("terrain_flat")), "test");
        helper.assertTrue(done(player, "symbol"), "learning a symbol awards 'symbol'");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.UNLINKED_BOOK.get()));
        player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().is(ModItems.LINKING_BOOK.get()), "the book linked");
        helper.assertTrue(done(player, "linkbook"), "linking an Unlinked Book awards 'linkbook'");

        helper.assertTrue(InstabilityDeaths.isInstabilityDeath(player, ModDamageTypes.source(level, ModDamageTypes.DECAY, null)), "decay counts");
        helper.assertTrue(InstabilityDeaths.isInstabilityDeath(player, ModDamageTypes.source(level, ModDamageTypes.METEOR, null)), "meteor counts");
        helper.assertFalse(InstabilityDeaths.isInstabilityDeath(player, level.damageSources().lightningBolt()), "plain lightning does not count");
        InstabilityDeaths.rememberStrike(player);
        helper.assertTrue(InstabilityDeaths.isInstabilityDeath(player, level.damageSources().lightningBolt()), "lightning right after an instability strike counts");
        helper.assertFalse(InstabilityDeaths.isInstabilityDeath(player, level.damageSources().magic()), "magic does not count");
        Mystcraft.LOGGER.info("[gametest] advancements: symbol={} linkbook={}", done(player, "symbol"), done(player, "linkbook"));
        helper.succeed();
    }
}

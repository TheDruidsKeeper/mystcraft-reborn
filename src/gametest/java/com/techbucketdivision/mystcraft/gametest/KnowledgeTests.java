package com.tbd.mystcraft.gametest;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.item.LinkingItem;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.item.component.SymbolPage;
import com.tbd.mystcraft.knowledge.SymbolKnowledge;
import com.tbd.mystcraft.linking.LinkController;
import com.tbd.mystcraft.util.MystIds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

/** Symbol knowledge (world-building plan §7.1): pages are studied, Ages teach their symbols, the desk lists what is known. */
@ForEachTest(groups = "knowledge")
public class KnowledgeTests {
    private static final int AGE_TIMEOUT = 20 * 60;

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Using a symbol page teaches its symbol and modifiers and consumes the page; a page of known symbols is kept")
    static void studyingAPageTeachesItsSymbols(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var sun = MystIds.id("sun_normal");
            var north = MystIds.id("mod_north");
            helper.assertTrue(SymbolKnowledge.known(player).isEmpty(), "a new player knows nothing");
            ItemStack page = PageItem.createSymbolPage(new SymbolPage(sun, List.of(north), false));
            page.setCount(2);
            player.setItemInHand(InteractionHand.MAIN_HAND, page);
            InteractionResult result = page.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(), "studying succeeds (" + result + ")");
            helper.assertTrue(SymbolKnowledge.knows(player, sun) && SymbolKnowledge.knows(player, north), "sun and north learned: " + SymbolKnowledge.known(player));
            helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1, "one page consumed");
            result = page.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(!result.consumesAction(), "a known page is not consumed (" + result + ")");
            helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1, "page kept");
            helper.assertTrue(!SymbolKnowledge.knows(player, MystIds.id("terrain_flat")), "nothing else learned");
            helper.assertTrue(SymbolKnowledge.knowsPage(player, page), "knowsPage sees the whole page");
        });
        helper.succeed();
    }

    @GameTest(timeoutTicks = AGE_TIMEOUT)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Arriving in an Age teaches every symbol of that Age (modifiers included)")
    static void arrivingInAnAgeTeachesItsSymbols(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("School", MystIds.id("terrain_flat"));
        AgeData data = TestBooks.bind(book, server);
        LinkInfo info = LinkingItem.getLinkInfo(book);
        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(SymbolKnowledge.known(player).isEmpty(), "a new player knows nothing");
        helper.assertTrue(LinkController.travelEntity(player, info), "player linked into the Age");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(SymbolKnowledge.known(player).containsAll(data.symbols()),
                        "player knows every Age symbol: " + SymbolKnowledge.known(player).size() + " of " + data.symbols().size()))
                .thenExecute(() -> Mystcraft.LOGGER.info("[gametest] arrival taught {} symbols: {}", SymbolKnowledge.known(player).size(), SymbolKnowledge.known(player)))
                .thenSucceed();
    }
}

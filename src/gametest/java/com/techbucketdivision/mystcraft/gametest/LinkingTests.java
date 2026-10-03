package com.techbucketdivision.mystcraft.gametest;

import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.api.linking.LinkEvent;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.block.BookReceptacleBlock;
import com.techbucketdivision.mystcraft.blockentity.BookReceptacleBlockEntity;
import com.techbucketdivision.mystcraft.item.DescriptiveBookItem;
import com.techbucketdivision.mystcraft.item.LinkingItem;
import com.techbucketdivision.mystcraft.linking.LinkController;
import com.techbucketdivision.mystcraft.linking.LinkListeners;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.UUID;

/**
 * Linking: book binding, Age arrival (spawn + platform), crystal portals, Disarm, refusal reasons. These are the
 * server-side halves of the bugs reported in the first playtest; the client-visible halves are on the manual
 * checklist in docs/TESTING.md.
 */
@ForEachTest(groups = "linking")
public class LinkingTests {

    /** Pads tests are slow the first time (Age chunk generation), so every Age test gets a generous timeout. */
    private static final int AGE_TIMEOUT = 20 * 60;

    @GameTest(timeoutTicks = AGE_TIMEOUT)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "An untitled Descriptive Book binds to a new Age and takes the Age's generated name instead of '???'")
    static void untitledBookTakesAgeName(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook(LinkInfo.DEFAULT_NAME);
        AgeData data = TestBooks.bind(book, server);
        LinkInfo info = LinkingItem.getLinkInfo(book);
        helper.assertTrue(info.isBound(), "book is bound after first link");
        helper.assertFalse(LinkInfo.DEFAULT_NAME.equals(data.name()), "Age must not be named '???' (was " + data.name() + ")");
        helper.assertValueEqual(info.displayName(), data.name(), "book display name follows the Age name");
        helper.assertFalse(data.symbols().isEmpty(), "grammar expanded the empty book into symbols");
        helper.succeed();
    }

    @GameTest(timeoutTicks = AGE_TIMEOUT)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Binding writes every generated symbol into the book: afterwards the book's pages describe the whole Age")
    static void bindingWritesGeneratedSymbolsIntoBook(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // partially written: one terrain symbol, the rest is up to the grammar
        ItemStack book = TestBooks.unboundDescriptiveBook("Half written", com.techbucketdivision.mystcraft.util.MystIds.id("terrain_flat"));
        int pagesBefore = com.techbucketdivision.mystcraft.item.DescriptiveBookItem.getPages(book).size();
        AgeData data = TestBooks.bind(book, server);
        var pages = com.techbucketdivision.mystcraft.item.DescriptiveBookItem.getPages(book);
        var written = com.techbucketdivision.mystcraft.item.DescriptiveBookItem.writtenSymbols(pages);
        com.techbucketdivision.mystcraft.Mystcraft.LOGGER.info("[gametest] bound book pages {} -> {}; age symbols {}; book symbols {}",
                pagesBefore, pages.size(), data.symbols(), written);
        helper.assertTrue(pages.size() > pagesBefore, "pages were added to the book");
        helper.assertTrue(com.techbucketdivision.mystcraft.item.PageItem.isLinkPanel(pages.getFirst()), "link panel stays first");
        helper.assertValueEqual(com.techbucketdivision.mystcraft.item.PageItem.getSymbolId(pages.get(1)),
                com.techbucketdivision.mystcraft.util.MystIds.id("terrain_flat"), "author's page keeps its place");
        // multiset equality: every Age symbol occurrence has a page, and no page is for a symbol the Age lacks
        var want = new java.util.ArrayList<>(data.symbols());
        for (var id : written) helper.assertTrue(want.remove(id), "book carries a page for a symbol the Age does not use: " + id);
        helper.assertTrue(want.isEmpty(), "Age symbols without a page in the book: " + want);
        helper.assertValueEqual(data.pages().size(), pages.size(), "Age keeps the same page list");
        helper.succeed();
    }

    @GameTest(timeoutTicks = AGE_TIMEOUT)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "A titled Descriptive Book names its Age after the title")
    static void titledBookNamesAge(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("Riven");
        AgeData data = TestBooks.bind(book, server);
        helper.assertValueEqual(data.name(), "Riven", "Age named after the book title");
        helper.succeed();
    }

    @GameTest(timeoutTicks = AGE_TIMEOUT)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Linking an entity into a new Age lands it on solid ground on a 3x3 platform with head room")
    static void arrivalLandsOnPlatform(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("Arrival");
        TestBooks.bind(book, server);
        LinkInfo info = LinkingItem.getLinkInfo(book);

        var pig = helper.spawn(EntityType.PIG, 1, 1, 1);
        pig.setNoAi(true);
        UUID id = pig.getUUID();
        helper.assertTrue(LinkController.travelEntity(pig, info), "travelEntity succeeded");

        ServerLevel age = server.getLevel(info.dimension().orElseThrow());
        helper.assertNotNull(age, "Age level exists after the link");
        helper.assertTrue(pig.isRemoved(), "original entity removed from the origin level");
        // Entities teleported into a level only become visible through getEntity() once their chunk section is
        // accessible (next tick), so poll.
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertNotNull(age.getEntity(id), "entity arrived in the Age"))
                .thenExecute(() -> {
                    Entity moved = age.getEntity(id);
                    assertPlatform(helper, age, moved.blockPosition());
                })
                .thenSucceed();
    }

    @GameTest(timeoutTicks = AGE_TIMEOUT)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "LinkController.defaultSpawn for an Age is on the ground, never the overworld respawn point")
    static void ageDefaultSpawnIsGrounded(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("Ground");
        AgeData data = TestBooks.bind(book, server);
        ServerLevel age = AgeManager.getOrCreateLevel(server, data);
        BlockPos spawn = LinkController.defaultSpawn(age);
        helper.assertNotNull(data.spawn(), "Age spawn stored");
        BlockPos below = spawn.below();
        boolean grounded = !age.getBlockState(below).getCollisionShape(age, below).isEmpty();
        boolean airborne = true;
        for (int i = 1; i <= 32 && airborne; i++) {
            BlockPos p = spawn.below(i);
            if (p.getY() <= age.getMinY()) break;
            if (!age.getBlockState(p).getCollisionShape(age, p).isEmpty()) airborne = false;
        }
        helper.assertTrue(grounded || airborne, "spawn " + spawn.toShortString() + " floats above terrain (ground within 32 blocks but not directly below)");
        helper.assertTrue(age.getBlockState(spawn).getCollisionShape(age, spawn).isEmpty(), "spawn block is passable");
        helper.succeed();
    }

    @GameTest(timeoutTicks = AGE_TIMEOUT)
    @EmptyTemplate(value = "5x6x5", floor = true)
    @TestHolder(description = "A crystal portal powered by a bound Descriptive Book renders a stable field and links an entity that touches it")
    static void portalLinksEntity(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("Portal");
        TestBooks.bind(book, server);
        runPortalTest(helper, book);
    }

    @GameTest(timeoutTicks = AGE_TIMEOUT)
    @EmptyTemplate(value = "5x6x5", floor = true)
    @TestHolder(description = "An UNBOUND Descriptive Book in a receptacle binds on first portal contact and links the entity (playtest bug: portal did nothing)")
    static void portalBindsUnboundBook(ExtendedGameTestHelper helper) {
        ItemStack book = TestBooks.unboundDescriptiveBook("Portal Unbound");
        runPortalTest(helper, book);
    }

    /**
     * Frame: 3x3 crystal ring in the x/y plane at z=2 (relative), interior (2,2,2); receptacle on top of the ring at
     * (2,4,2) attached to the crystal below it. A pig spawned in the interior must end up in the book's Age.
     */
    private static void runPortalTest(ExtendedGameTestHelper helper, ItemStack book) {
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                if (x == 2 && y == 2) continue;
                helper.setBlock(x, y, 2, ModBlocks.CRYSTAL.get());
            }
        }
        helper.setBlock(2, 4, 2, ModBlocks.BOOK_RECEPTACLE.get().defaultBlockState().setValue(BookReceptacleBlock.ROTATION, Direction.UP));
        BookReceptacleBlockEntity receptacle = helper.getBlockEntity(2, 4, 2, BookReceptacleBlockEntity.class);
        receptacle.setBook(book);
        helper.assertBlockPresent(ModBlocks.LINK_PORTAL.get(), 2, 2, 2);

        var pig = helper.spawn(EntityType.PIG, 2, 2, 2);
        UUID id = pig.getUUID();
        MinecraftServer server = helper.getLevel().getServer();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    LinkInfo info = LinkingItem.getLinkInfo(receptacle.getBook());
                    helper.assertTrue(info.isBound(), "receptacle book is bound");
                    ServerLevel age = server.getLevel(info.dimension().orElseThrow());
                    helper.assertNotNull(age, "Age level exists");
                    helper.assertNotNull(age.getEntity(id), "pig linked through the portal");
                })
                .thenExecute(() -> {
                    LinkInfo info = LinkingItem.getLinkInfo(receptacle.getBook());
                    ServerLevel age = server.getLevel(info.dimension().orElseThrow());
                    Entity moved = age.getEntity(id);
                    assertPlatform(helper, age, moved.blockPosition());
                    helper.assertBlockPresent(ModBlocks.LINK_PORTAL.get(), 2, 2, 2); // field survives the link
                })
                .thenSucceed();
    }

    @GameTest(timeoutTicks = AGE_TIMEOUT)
    @EmptyTemplate(value = "5x6x5", floor = true)
    @TestHolder(description = "Breaking a frame crystal collapses the portal field; removing the book shuts it down")
    static void portalCollapsesWhenFrameBroken(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("Collapse");
        TestBooks.bind(book, server);
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                if (x == 2 && y == 2) continue;
                helper.setBlock(x, y, 2, ModBlocks.CRYSTAL.get());
            }
        }
        helper.setBlock(2, 4, 2, ModBlocks.BOOK_RECEPTACLE.get().defaultBlockState().setValue(BookReceptacleBlock.ROTATION, Direction.UP));
        BookReceptacleBlockEntity receptacle = helper.getBlockEntity(2, 4, 2, BookReceptacleBlockEntity.class);
        receptacle.setBook(book);
        helper.assertBlockPresent(ModBlocks.LINK_PORTAL.get(), 2, 2, 2);
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(2, 1, 2, Blocks.AIR)) // bottom frame crystal
                .thenWaitUntil(() -> helper.assertBlockNotPresent(ModBlocks.LINK_PORTAL.get(), 2, 2, 2))
                .thenExecute(() -> helper.setBlock(2, 1, 2, ModBlocks.CRYSTAL.get()))
                .thenExecute(() -> receptacle.setBook(receptacle.getBook().copy())) // re-pulse by re-inserting
                .thenWaitUntil(() -> helper.assertBlockPresent(ModBlocks.LINK_PORTAL.get(), 2, 2, 2))
                .thenExecute(() -> receptacle.setBook(ItemStack.EMPTY))
                .thenWaitUntil(() -> helper.assertBlockNotPresent(ModBlocks.LINK_PORTAL.get(), 2, 2, 2))
                .thenSucceed();
    }

    @GameTest(timeoutTicks = 200)
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "Disarm ejects a donkey's chest contents on link (horse inventories via Entity#getSlot)")
    static void disarmEjectsHorseInventory(ExtendedGameTestHelper helper) {
        Check.run(helper, () -> disarmBody(helper));
    }

    private static void disarmBody(ExtendedGameTestHelper helper) {
        var donkey = helper.spawn(EntityType.DONKEY, 2, 1, 2);
        donkey.setNoAi(true);
        // Slot 499 is the chest slot (/item uses it): equipping a chest also creates the 15-slot inventory.
        helper.assertTrue(donkey.getSlot(499).set(new ItemStack(Items.CHEST)), "chest equipped");
        helper.assertNotNull(donkey.getSlot(500), "chest inventory slot exists");
        donkey.getSlot(500).set(new ItemStack(Items.APPLE, 7));
        helper.assertValueEqual(donkey.getSlot(500).get().getCount(), 7, "apple stored in chest slot");
        ServerLevel level = helper.getLevel();
        LinkInfo info = LinkInfo.EMPTY.withDimension(level.dimension()).withSpawn(helper.absolutePos(new BlockPos(2, 1, 2)))
                .withFlag(LinkProperty.INTRA_LINKING, true).withFlag(LinkProperty.DISARM, true);
        helper.assertTrue(LinkController.travelEntity(donkey, info), "intra-link succeeded");
        AABB area = helper.getBounds().inflate(2);
        boolean dropped = !level.getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.APPLE)).isEmpty();
        helper.assertTrue(dropped, "apples were ejected as item entities");
        helper.assertTrue(donkey.getSlot(500).get().isEmpty(), "chest slot emptied");
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "A refused link carries a human-readable reason (what the [link] log line shows)")
    static void refusedLinkHasReason(ExtendedGameTestHelper helper) {
        var pig = helper.spawn(EntityType.PIG, 1, 1, 1);
        ServerLevel level = helper.getLevel();
        LinkEvent.Allow event = new LinkEvent.Allow(level, pig, LinkInfo.EMPTY);
        NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(event.isCanceled(), "unbound link is refused");
        helper.assertNotNull(event.getReason(), "refusal has a reason");
        helper.assertFalse(LinkListeners.isLinkPermitted(level, pig, LinkInfo.EMPTY), "isLinkPermitted agrees");

        LinkInfo same = LinkInfo.EMPTY.withDimension(level.dimension());
        LinkEvent.Allow sameEvent = new LinkEvent.Allow(level, pig, same);
        NeoForge.EVENT_BUS.post(sameEvent);
        helper.assertTrue(sameEvent.isCanceled() && sameEvent.getReason() != null && sameEvent.getReason().contains("Intra-Linking"),
                "same-dimension link without Intra-Linking is refused with the matching reason");
        helper.succeed();
    }

    /** 3x3 of collision under the feet, the feet and head blocks passable. */
    static void assertPlatform(ExtendedGameTestHelper helper, ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos below = feet.offset(dx, -1, dz);
                helper.assertTrue(!level.getBlockState(below).getCollisionShape(level, below).isEmpty(),
                        "platform block missing at " + below.toShortString() + " (" + level.getBlockState(below) + ")");
                BlockPos at = feet.offset(dx, 0, dz);
                BlockPos head = feet.offset(dx, 1, dz);
                helper.assertTrue(level.getBlockState(at).getCollisionShape(level, at).isEmpty(),
                        "feet level blocked at " + at.toShortString());
                helper.assertTrue(level.getBlockState(head).getCollisionShape(level, head).isEmpty(),
                        "head room blocked at " + head.toShortString());
            }
        }
        helper.assertFalse(level.getBlockState(feet.below()).is(Blocks.BEDROCK), "not standing on bedrock");
    }
}

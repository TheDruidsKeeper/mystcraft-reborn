package com.tbd.mystcraft.gametest;

import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.age.AgeManager;
import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.block.SequenceDialBlock;
import com.tbd.mystcraft.blockentity.FacilityCacheBlockEntity;
import com.tbd.mystcraft.blockentity.FacilityLockBlockEntity;
import com.tbd.mystcraft.blockentity.WardedDoorBlockEntity;
import com.tbd.mystcraft.facility.FacilityMarkers;
import com.tbd.mystcraft.facility.FacilityProtection;
import com.tbd.mystcraft.facility.FacilityState;
import com.tbd.mystcraft.item.LinkingItem;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.registry.ModBlocks;
import com.tbd.mystcraft.registry.ModItems;
import com.tbd.mystcraft.registry.ModStructures;
import com.tbd.mystcraft.util.MystIds;
import com.tbd.mystcraft.world.structure.FacilityLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

/** Facility puzzle framework (docs/plans/FACILITY_PLAN.md §2.2–2.5): markers, locks, doors, rewards, protection. */
@ForEachTest(groups = "facility")
public class FacilityPuzzleTests {

    /** A mock server player in survival (mock players default to infinite materials). */
    private static ServerPlayer survivor(ExtendedGameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return player;
    }

    private static long piece(ExtendedGameTestHelper helper) {
        return helper.absolutePos(BlockPos.ZERO).asLong();
    }

    private static BoundingBox box(ExtendedGameTestHelper helper) {
        BlockPos a = helper.absolutePos(BlockPos.ZERO), b = helper.absolutePos(new BlockPos(6, 6, 6));
        return BoundingBox.fromCorners(a, b);
    }

    private static FacilityLockBlockEntity lock(ExtendedGameTestHelper helper, BlockPos rel, net.minecraft.world.level.block.Block block, FacilityLockBlockEntity.Kind kind) {
        helper.setBlock(rel, block.defaultBlockState());
        FacilityLockBlockEntity be = helper.getBlockEntity(rel, FacilityLockBlockEntity.class);
        be.configure(kind, piece(helper), box(helper));
        return be;
    }

    private static WardedDoorBlockEntity door(ExtendedGameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, ModBlocks.WARDED_DOOR.get().defaultBlockState());
        WardedDoorBlockEntity be = helper.getBlockEntity(rel, WardedDoorBlockEntity.class);
        be.setLockId(piece(helper));
        return be;
    }

    @GameTest
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Symbol altar: pages of the required symbols are consumed one by one; the last one solves the lock and removes the piece's warded doors")
    static void symbolAltarOpensDoors(ExtendedGameTestHelper helper) {
        FacilityLockBlockEntity altar = lock(helper, new BlockPos(1, 1, 1), ModBlocks.SYMBOL_ALTAR.get(), FacilityLockBlockEntity.Kind.SYMBOL);
        Identifier flat = MystIds.id("terrain_flat"), caves = MystIds.id("caves");
        altar.setRequiredSymbols(List.of(flat, caves));
        door(helper, new BlockPos(3, 1, 3));
        door(helper, new BlockPos(3, 2, 3));
        ServerPlayer player = survivor(helper);

        player.setItemInHand(InteractionHand.MAIN_HAND, PageItem.createSymbolPage(MystIds.id("villages")));
        altar.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(altar.provided().isEmpty() && !player.getMainHandItem().isEmpty(), "a wrong page is refused and kept");

        player.setItemInHand(InteractionHand.MAIN_HAND, PageItem.createSymbolPage(flat));
        altar.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(altar.provided().equals(List.of(flat)) && player.getMainHandItem().isEmpty(), "first page consumed");
        helper.assertTrue(!altar.isSolved(), "not solved after one of two pages");
        helper.assertBlockPresent(ModBlocks.WARDED_DOOR.get(), new BlockPos(3, 1, 3));

        player.setItemInHand(InteractionHand.MAIN_HAND, PageItem.createSymbolPage(caves));
        altar.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(altar.isSolved(), "both pages solve the altar");
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 3));
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Offering pedestal: only the wanted item is accepted (one is consumed); a door in an unloaded-at-the-time state opens on its own tick")
    static void offeringPedestalAndDoorTick(ExtendedGameTestHelper helper) {
        FacilityLockBlockEntity pedestal = lock(helper, new BlockPos(1, 1, 1), ModBlocks.OFFERING_PEDESTAL.get(), FacilityLockBlockEntity.Kind.OFFERING);
        pedestal.setWanted(Identifier.withDefaultNamespace("ender_pearl"));
        ServerPlayer player = survivor(helper);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        pedestal.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!pedestal.isSolved() && player.getMainHandItem().is(Items.DIAMOND), "wrong item refused");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL, 3));
        pedestal.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(pedestal.isSolved(), "the wanted item solves the lock");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "one pearl consumed");

        // A door placed after the lock was solved (its chunk was not loaded when the lock opened the others).
        door(helper, new BlockPos(5, 1, 5));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 1, 5)))
                .thenSucceed();
    }

    @GameTest
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Sequence lock: a bank of dials is solved only when every dial shows its glyph of the Age code; clue markers show those glyphs")
    static void sequenceBankMatchesCode(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> dials = List.of(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(1, 1, 3)), helper.absolutePos(new BlockPos(1, 1, 5)));
        int[] code = FacilityState.code(level, 3);
        FacilityLockBlockEntity first = null;
        for (int i = 0; i < 3; i++) {
            BlockPos rel = new BlockPos(1, 1, 1 + 2 * i);
            FacilityLockBlockEntity dial = lock(helper, rel, ModBlocks.SEQUENCE_DIAL.get(), FacilityLockBlockEntity.Kind.SEQUENCE);
            dial.setDials(dials, i);
            // start every dial one step before its answer so a single click lands on it
            level.setBlockAndUpdate(dials.get(i), ModBlocks.SEQUENCE_DIAL.get().defaultBlockState()
                    .setValue(SequenceDialBlock.GLYPH, (code[i] + FacilityState.glyphCount() - 1) % FacilityState.glyphCount()));
            if (i == 0) first = helper.getBlockEntity(rel, FacilityLockBlockEntity.class);
        }
        door(helper, new BlockPos(5, 1, 5));
        ServerPlayer player = survivor(helper);
        helper.assertTrue(!first.sequenceMatches(level), "bank starts unsolved");
        for (int i = 0; i < 3; i++) {
            helper.getBlockEntity(new BlockPos(1, 1, 1 + 2 * i), FacilityLockBlockEntity.class).interact(player, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(level.getBlockState(dials.get(i)).getValue(SequenceDialBlock.GLYPH), code[i], "dial " + i + " shows its code glyph");
        }
        helper.assertTrue(first.isSolved(), "bank solved once every dial matches");
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 1, 5));

        // the clue marker for digit 1 places the glyph block of code[1]
        BlockPos clue = helper.absolutePos(new BlockPos(3, 1, 1));
        FacilityMarkers.place(level, List.of(new FacilityMarkers.Marker(clue, "clue:1")), piece(helper), box(helper), List.of(),
                BoundingBox.infinite(), RandomSource.create(1));
        helper.assertTrue(level.getBlockState(clue).is(FacilityMarkers.GLYPH_BLOCKS.get(code[1])), "clue block shows glyph " + code[1]);
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Markers resolve to puzzle blocks: door, every lock type, loot chest, trial spawner, vault and the per-player reward cache")
    static void markersResolve(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        List<FacilityMarkers.Marker> markers = List.of(
                new FacilityMarkers.Marker(o.offset(1, 1, 1), "door"),
                new FacilityMarkers.Marker(o.offset(0, 1, 2), "door"),
                new FacilityMarkers.Marker(o.offset(2, 1, 1), "lock:symbol:1"),
                new FacilityMarkers.Marker(o.offset(3, 1, 1), "lock:sequence"),
                new FacilityMarkers.Marker(o.offset(4, 1, 1), "lock:sequence"),
                new FacilityMarkers.Marker(o.offset(5, 1, 1), "lock:offering:minecraft:book"),
                new FacilityMarkers.Marker(o.offset(1, 1, 3), "lock:trial"),
                new FacilityMarkers.Marker(o.offset(2, 1, 3), "trial_spawner:melee/zombie"),
                new FacilityMarkers.Marker(o.offset(3, 1, 3), "vault"),
                new FacilityMarkers.Marker(o.offset(4, 1, 3), "loot:mystcraft:chests/facility_common"),
                new FacilityMarkers.Marker(o.offset(5, 1, 3), "reward:linkbook"),
                new FacilityMarkers.Marker(o.offset(1, 1, 5), "reward:mystcraft:chests/facility_vault"),
                new FacilityMarkers.Marker(o.offset(2, 1, 5), "bogus:marker"));
        // the door at x=0 opens onto an "earlier" piece west of this one: it is the way in and stays open
        BoundingBox parent = BoundingBox.fromCorners(o.offset(-8, 0, 0), o.offset(-1, 6, 6));
        FacilityMarkers.place(level, markers, o.asLong(), box(helper), List.of(parent), BoundingBox.infinite(), RandomSource.create(7));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(0, 1, 2));

        helper.assertBlockPresent(ModBlocks.WARDED_DOOR.get(), new BlockPos(1, 1, 1));
        helper.assertValueEqual(helper.getBlockEntity(new BlockPos(1, 1, 1), WardedDoorBlockEntity.class).lockId(), o.asLong(), "door lock id = piece");
        FacilityLockBlockEntity altar = helper.getBlockEntity(new BlockPos(2, 1, 1), FacilityLockBlockEntity.class);
        helper.assertValueEqual(altar.kind(), FacilityLockBlockEntity.Kind.SYMBOL, "symbol altar");
        helper.assertValueEqual(altar.required().size(), 1, "altar wants one page");
        FacilityLockBlockEntity dialA = helper.getBlockEntity(new BlockPos(3, 1, 1), FacilityLockBlockEntity.class);
        FacilityLockBlockEntity dialB = helper.getBlockEntity(new BlockPos(4, 1, 1), FacilityLockBlockEntity.class);
        helper.assertTrue(dialA.dials().size() == 2 && dialB.dials().equals(dialA.dials()) && dialA.index() == 0 && dialB.index() == 1, "two dials wired as one bank");
        helper.assertValueEqual(helper.getBlockEntity(new BlockPos(5, 1, 1), FacilityLockBlockEntity.class).wanted(), Identifier.withDefaultNamespace("book"), "explicit offering");
        helper.assertValueEqual(helper.getBlockEntity(new BlockPos(1, 1, 3), FacilityLockBlockEntity.class).wanted(), FacilityMarkers.TRIAL_KEY, "trial lock wants a trial key");
        helper.assertBlockPresent(Blocks.TRIAL_SPAWNER, new BlockPos(2, 1, 3));
        helper.assertBlockPresent(Blocks.VAULT, new BlockPos(3, 1, 3));
        helper.assertBlockPresent(Blocks.CHEST, new BlockPos(4, 1, 3));
        helper.assertTrue(helper.getBlockEntity(new BlockPos(4, 1, 3), ChestBlockEntity.class).getLootTable() != null, "loot chest carries its table");
        FacilityCacheBlockEntity home = helper.getBlockEntity(new BlockPos(5, 1, 3), FacilityCacheBlockEntity.class);
        helper.assertTrue(home.isLinkbook(), "reward:linkbook cache");
        FacilityCacheBlockEntity loot = helper.getBlockEntity(new BlockPos(1, 1, 5), FacilityCacheBlockEntity.class);
        helper.assertTrue(!loot.isLinkbook() && loot.lootTable().identifier().equals(FacilityMarkers.VAULT_LOOT), "reward:<table> cache");
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 5));
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Facility cache: each player claims the Linking Book home once (bound to the overworld), which marks the facility solved; a loot cache rolls its table once per player")
    static void cacheIsPerPlayer(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.FACILITY_CACHE.get().defaultBlockState());
        FacilityCacheBlockEntity cache = helper.getBlockEntity(new BlockPos(1, 1, 1), FacilityCacheBlockEntity.class);
        cache.setLootTable(null);
        ServerPlayer a = survivor(helper);
        ServerPlayer b = survivor(helper);
        helper.assertTrue(!FacilityState.isSolved(level), "unsolved before any claim");
        cache.interact(a);
        helper.assertTrue(FacilityState.isSolved(level), "claiming the home book solves the facility");
        helper.assertValueEqual(count(a, ModItems.LINKING_BOOK.get()), 1, "player A got one book");
        cache.interact(a);
        helper.assertValueEqual(count(a, ModItems.LINKING_BOOK.get()), 1, "player A cannot claim twice");
        cache.interact(b);
        helper.assertValueEqual(count(b, ModItems.LINKING_BOOK.get()), 1, "player B gets their own book");
        ItemStack book = null;
        for (ItemStack s : a.getInventory()) if (s.is(ModItems.LINKING_BOOK.get())) book = s;
        LinkInfo info = LinkingItem.getLinkInfo(book);
        helper.assertTrue(info.dimension().orElse(null) == Level.OVERWORLD && info.spawn().isEmpty(), "home book targets the overworld's world spawn: " + info);

        helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.FACILITY_CACHE.get().defaultBlockState());
        FacilityCacheBlockEntity lootCache = helper.getBlockEntity(new BlockPos(3, 1, 1), FacilityCacheBlockEntity.class);
        lootCache.setLootTable(FacilityMarkers.VAULT_LOOT);
        int before = items(a);
        lootCache.interact(a);
        helper.assertTrue(lootCache.hasClaimed(a.getUUID()), "loot cache claimed");
        helper.assertTrue(items(a) > before, "loot cache gave items (" + before + " -> " + items(a) + ")");
        int after = items(a);
        lootCache.interact(a);
        helper.assertValueEqual(items(a), after, "second claim gives nothing more");
        helper.succeed();
    }

    private static int items(ServerPlayer player) {
        int n = 0;
        for (ItemStack s : player.getInventory()) n += s.getCount();
        return n;
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory()) if (s.is(item)) n += s.getCount();
        return n;
    }

    @GameTest(timeoutTicks = 20 * 120)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Protection: inside an unsolved Facility's bounds survival breaking is refused; solving the facility (or standing outside) lifts it")
    static void protectionFollowsFacilityBounds(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("Warded", 4343L, MystIds.id("vault"));
        AgeData data = TestBooks.bind(book, server);
        ServerLevel age = AgeManager.getOrCreateLevel(server, data);
        ChunkPos at = FacilityLocator.facilityChunk(age);
        helper.assertNotNull(at, "facility chunk");
        Structure structure = age.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.FACILITY).value();
        var cs = age.getChunkSource();
        ChunkAccess chunk = age.getChunk(at.x(), at.z(), ChunkStatus.STRUCTURE_STARTS);
        cs.getGenerator().createStructures(age.registryAccess(), cs.getGeneratorState(), age.structureManager(), chunk, server.getStructureManager(), age.dimension());
        StructureStart start = chunk.getStartForStructure(structure);
        helper.assertTrue(start != null && start.isValid(), "facility start created");
        BoundingBox bounds = start.getBoundingBox();
        BlockPos inside = new BlockPos((bounds.minX() + bounds.maxX()) / 2, (bounds.minY() + bounds.maxY()) / 2, (bounds.minZ() + bounds.maxZ()) / 2);
        BlockPos outside = new BlockPos(bounds.maxX() + 64, inside.getY(), bounds.maxZ() + 64);
        age.getChunk(inside.getX() >> 4, inside.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
        helper.assertTrue(FacilityProtection.insideFacility(age, inside), "centre of the start bounds is inside the facility");
        helper.assertTrue(FacilityProtection.protects(age, inside), "unsolved facility protects its blocks");
        helper.assertTrue(!FacilityProtection.protects(age, outside), "blocks outside the bounds are free");
        helper.assertTrue(!FacilityProtection.protects(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)), "non-Age levels are never protected");
        FacilityState.markSolved(age);
        helper.assertTrue(!FacilityProtection.protects(age, inside), "a solved facility is no longer protected");
        helper.assertTrue(FacilityProtection.exempt(helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE)), "creative players are exempt");
        helper.assertTrue(!FacilityProtection.exempt(helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL)), "survival players are not");
        helper.succeed();
    }
}

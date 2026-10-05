package com.tbd.mystcraft.gametest;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.age.AgeController;
import com.tbd.mystcraft.age.AgeControllers;
import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.age.AgeManager;
import com.tbd.mystcraft.registry.ModBlocks;
import com.tbd.mystcraft.registry.ModFluids;
import com.tbd.mystcraft.util.MystIds;
import com.tbd.mystcraft.world.feature.StarFissurePopulator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

/** World generation and fluids. */
@ForEachTest(groups = "world")
public class WorldTests {

    @GameTest(timeoutTicks = 20 * 120)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "An Age written with the Star Fissure symbol generates the fissure in chunk (0,0) and spawns nearby")
    static void starFissureGeneratesAtOrigin(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("Fissure", MystIds.id("star_fissure"));
        AgeData data = TestBooks.bind(book, server);
        helper.assertTrue(data.symbols().contains(MystIds.id("star_fissure")), "written symbol kept after grammar expansion");
        ServerLevel age = AgeManager.getOrCreateLevel(server, data);
        AgeController controller = AgeControllers.server(age);
        helper.assertNotNull(controller, "Age controller");
        helper.assertTrue(controller.populators().stream().anyMatch(p -> p instanceof StarFissurePopulator), "fissure populator registered");

        // Force chunk (0,0) (and its decoration neighbours) to generate.
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) age.getChunk(cx, cz);
        }
        int found = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = -16; x < 32 && found == 0; x++) {
            for (int z = -48; z < 32; z++) {
                at.set(x, 0, z);
                if (age.getBlockState(at).is(ModBlocks.STAR_FISSURE.get())) {
                    found++;
                    break;
                }
            }
        }
        helper.assertTrue(found > 0, "no star_fissure blocks at y=0 around chunk (0,0)");

        BlockPos spawn = com.tbd.mystcraft.linking.LinkController.defaultSpawn(age);
        helper.assertTrue(Math.abs(spawn.getX()) <= 80 && Math.abs(spawn.getZ()) <= 80,
                "spawn " + spawn.toShortString() + " should stay near the fissure at the origin");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 60)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Random Ages are not all born at midnight: starting celestial angles vary and most have daylight (playtest: every world dark)")
    static void randomAgesStartAtVaryingTimes(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int samples = 12;
        int lit = 0;
        float minAngle = 1f, maxAngle = 0f;
        for (int i = 0; i < samples; i++) {
            // fixed seeds: the start time and the blueprint's celestials follow the seed, so the result is reproducible
            ItemStack book = TestBooks.unboundDescriptiveBook("Daylight " + i, 1000L + i);
            AgeData data = TestBooks.bind(book, server);
            AgeController controller = new AgeController(data, server.registryAccess(), false);
            controller.ensureCurrent();
            boolean anyLight = controller.celestials().stream().anyMatch(c -> c.providesLight());
            float angle = controller.celestialAngle(data.worldTime(), 0f);
            float brightness = com.tbd.mystcraft.age.celestial.AgeDayCurves.brightness(angle);
            String celestialSymbols = data.symbols().stream().map(id -> id.getPath())
                    .filter(p -> p.startsWith("sun") || p.startsWith("moon") || p.startsWith("stars") || p.startsWith("mod_"))
                    .toList().toString();
            Mystcraft.LOGGER.info("[gametest] Age '{}' time {} light={} angle={} brightness={} {}", data.name(), data.worldTime(),
                    anyLight, angle, brightness, celestialSymbols);
            if (anyLight) {
                minAngle = Math.min(minAngle, angle);
                maxAngle = Math.max(maxAngle, angle);
            }
            if (brightness > 0.5f) lit++;
        }
        helper.assertTrue(lit >= samples / 3, "at least a third of random Ages start in daylight (" + lit + "/" + samples + ")");
        helper.assertTrue(maxAngle - minAngle > 0.1f, "starting celestial angles vary (" + minAngle + ".." + maxAngle + ")");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 60)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Age time advances on the server and is persisted (playtest: worlds dark, time frozen)")
    static void ageTimeAdvances(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("Clock");
        AgeData data = TestBooks.bind(book, server);
        ServerLevel age = AgeManager.getOrCreateLevel(server, data);
        var pig = helper.spawn(EntityType.PIG, 1, 1, 1); // AgeTicker only advances time with players; use the level tick directly
        long start = data.worldTime();
        helper.startSequence()
                .thenExecuteAfter(40, () -> helper.assertTrue(data.worldTime() >= start + 30,
                        "Age time advanced (" + start + " -> " + data.worldTime() + ")"))
                .thenSucceed();
    }

    @GameTest(timeoutTicks = 20 * 60)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Age spawn search finds solid ground without the fallback (heightmaps usable on fresh Age chunks)")
    static void spawnSearchFindsGround(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("Heightmap");
        AgeData data = TestBooks.bind(book, server);
        ServerLevel age = AgeManager.getOrCreateLevel(server, data);
        age.getChunk(0, 0);
        int h = age.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 8, 8);
        Mystcraft.LOGGER.info("[gametest] heightmap at 8,8 in Age '{}': {} (minY {})", data.name(), h, age.getMinY());
        helper.assertTrue(h > age.getMinY(), "MOTION_BLOCKING_NO_LEAVES heightmap is primed in generated Age chunks (got " + h + ")");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 15)
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "Water flowing onto a star fissure does not wash the block away (playtest: fissure vanished when water reached it)")
    static void fissureSurvivesWater(ExtendedGameTestHelper helper) {
        helper.setBlock(1, 1, 1, ModBlocks.STAR_FISSURE.get().defaultBlockState());
        // water sources above and beside the fissure; everything else open so it flows freely (the link portal
        // shares the FluidProof fix but validates its frame on neighbour updates, so it is not placed bare here)
        helper.setBlock(1, 3, 1, Blocks.WATER);
        helper.setBlock(2, 1, 2, Blocks.WATER);
        helper.startSequence()
                .thenExecuteAfter(120, () -> {
                    helper.assertBlockPresent(ModBlocks.STAR_FISSURE.get(), new BlockPos(1, 1, 1));
                    helper.assertTrue(!helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(1, 2, 1))).isEmpty(),
                            "water flowed down onto the fissure");
                })
                .thenSucceed();
    }

    @GameTest(timeoutTicks = 100)
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "Black ink is water-like: entities swim in it instead of being frozen in place (playtest bug: stuck in ink)")
    static void inkIsSwimmable(ExtendedGameTestHelper helper) {
        helper.assertTrue(ModFluids.BLACK_INK_TYPE.get().getIsWaterLike(), "FluidType.isWaterLike");
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(x, 1, z, ModBlocks.BLACK_INK.get().defaultBlockState());
                helper.setBlock(x, 2, z, ModBlocks.BLACK_INK.get().defaultBlockState());
            }
        }
        // walls so the pool cannot drain
        for (int i = 0; i <= 4; i++) {
            for (int y = 1; y <= 2; y++) {
                helper.setBlock(0, y, i, Blocks.STONE);
                helper.setBlock(4, y, i, Blocks.STONE);
                helper.setBlock(i, y, 0, Blocks.STONE);
                helper.setBlock(i, y, 4, Blocks.STONE);
            }
        }
        var pig = helper.spawn(EntityType.PIG, 2, 1, 2);
        pig.setNoAi(true);
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    BlockPos at = pig.blockPosition();
                    String where = "pig at " + pig.position() + " block " + helper.getLevel().getBlockState(at)
                            + " fluid " + helper.getLevel().getFluidState(at).getType() + " height " + pig.getFluidTypeHeight(ModFluids.BLACK_INK_TYPE.get());
                    Mystcraft.LOGGER.info("[gametest] inkIsSwimmable: {}", where);
                    helper.assertTrue(pig.isAlive(), "pig alive in ink");
                    helper.assertTrue(pig.isInFluidType(ModFluids.BLACK_INK_TYPE.get()), "pig is inside the ink fluid (" + where + ")");
                    helper.assertTrue(pig.isInWater(), "ink counts as water for movement (isInWater) - " + where);
                })
                .thenSucceed();
    }
}

package com.techbucketdivision.mystcraft.gametest;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.registry.ModFluids;
import com.techbucketdivision.mystcraft.util.MystIds;
import com.techbucketdivision.mystcraft.world.feature.StarFissurePopulator;
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

        BlockPos spawn = com.techbucketdivision.mystcraft.linking.LinkController.defaultSpawn(age);
        helper.assertTrue(Math.abs(spawn.getX()) <= 80 && Math.abs(spawn.getZ()) <= 80,
                "spawn " + spawn.toShortString() + " should stay near the fissure at the origin");
        helper.succeed();
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

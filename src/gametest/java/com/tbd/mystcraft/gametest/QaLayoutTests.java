package com.tbd.mystcraft.gametest;

import com.tbd.mystcraft.command.QaWorlds;
import com.tbd.mystcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

/** Block-level layout of {@code /myst-dev qa-worlds}: the parts a tester walks and reads. */
@ForEachTest(groups = "qa")
public class QaLayoutTests {

    @GameTest
    @EmptyTemplate(value = "20x8x40", floor = true)
    @TestHolder(description = "QA worlds: a two-wide paved path runs from the player's feet into a gate centred on the player, the section sign stands directly left of the first column, the home book lies in an item frame on a pedestal")
    static void qaWorldsLayout(ExtendedGameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 38));
        int placed = QaWorlds.build(helper.getLevel(), player, start, Direction.NORTH);
        helper.assertValueEqual(placed, QaWorlds.cases().size(), "one lectern per case");

        // path: the player's column (x=10) and the one to its left (x=9), from the feet to the ring row
        for (int z = 37; z >= 33; z--) {
            helper.assertBlockPresent(Blocks.STONE_BRICKS, 9, 0, z);
            helper.assertBlockPresent(Blocks.STONE_BRICKS, 10, 0, z);
            helper.assertBlockPresent(Blocks.AIR, 9, 1, z);
            helper.assertBlockPresent(Blocks.AIR, 10, 1, z);
        }
        // ring row z=33: open where the path arrives, fenced beside it
        helper.assertBlockPresent(Blocks.OAK_FENCE, 8, 1, 33);
        helper.assertBlockPresent(Blocks.OAK_FENCE, 11, 1, 33);
        // row A lectern line z=31: sign at column 0 (x=4), home pedestal + item frame at column 1, A1 at column 3
        helper.assertBlockPresent(Blocks.OAK_SIGN, 4, 1, 31);
        helper.assertBlockPresent(Blocks.POLISHED_ANDESITE, 5, 1, 31);
        helper.assertBlockPresent(ModBlocks.LECTERN.get(), 7, 2, 31);
        BlockPos frameAt = helper.absolutePos(new BlockPos(5, 2, 31));
        var frames = helper.getLevel().getEntitiesOfClass(ItemFrame.class, new AABB(frameAt));
        helper.assertValueEqual(frames.size(), 1, "one item frame on the home pedestal");
        ItemFrame frame = frames.getFirst();
        helper.assertTrue(frame.getDirection() == Direction.UP, "frame lies on top of the pedestal");
        helper.assertTrue(frame.getItem().is(com.tbd.mystcraft.registry.ModItems.LINKING_BOOK.get()), "frame holds the home Linking Book");
        helper.assertValueEqual(frame.getRotation(), QaWorlds.frameRotation(Direction.NORTH), "book turned to read from the south");
        helper.assertValueEqual(QaWorlds.frameRotation(Direction.NORTH), 0, "north = rotation 0");
        helper.assertValueEqual(QaWorlds.frameRotation(Direction.EAST), 2, "east = rotation 2");
        helper.succeed();
    }
}

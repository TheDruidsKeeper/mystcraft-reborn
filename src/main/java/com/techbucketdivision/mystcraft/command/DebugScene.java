package com.techbucketdivision.mystcraft.command;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.block.BookReceptacleBlock;
import com.techbucketdivision.mystcraft.block.DecayType;
import com.techbucketdivision.mystcraft.block.LecternBlock;
import com.techbucketdivision.mystcraft.block.WritingDeskBlock;
import com.techbucketdivision.mystcraft.blockentity.BookDisplayBlockEntity;
import com.techbucketdivision.mystcraft.item.DescriptiveBookItem;
import com.techbucketdivision.mystcraft.item.LinkingBookItem;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

/**
 * {@code /myst-scene}: builds a showcase of every renderable Mystcraft block in front of the player, on a stone pad,
 * and positions the player to look at it. Used by the headless client self check for screenshots and by hand for
 * quick visual regression checks (docs/TESTING.md). Layout (player looks north, x grows to the right):
 *
 * <pre>
 *   row z-9 : crystal portal (3x3 ring + receptacle + new Age book)   star fissure 2x2
 *   row z-6 : writing desk | bookstand+book | lectern+book | ink mixer | book binder | link modifier
 *   row z-3 : ink pool 3x3 (1 deep)       decay blocks (one of each type)        crystal column
 * </pre>
 */
public final class DebugScene {
    private DebugScene() {}

    public static final int WIDTH = 23;
    public static final int DEPTH = 12;

    /** Builds the scene with its south-west corner at {@code origin} (pad surface = origin.y - 1). */
    public static BlockPos build(ServerLevel level, BlockPos origin, ServerPlayer viewer) {
        MinecraftServer server = level.getServer();
        int x0 = origin.getX(), y = origin.getY(), z0 = origin.getZ();

        // Pad + clear volume above.
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y - 1, z0 - dz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                for (int dy = 0; dy < 7; dy++) level.setBlock(new BlockPos(x0 + dx, y + dy, z0 - dz), Blocks.AIR.defaultBlockState(), 2);
            }
        }

        // Row A (z0-3): ink pool, decay blocks, crystal column.
        for (int dx = 1; dx <= 3; dx++) {
            for (int dz = 2; dz <= 4; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y - 1, z0 - dz), ModBlocks.BLACK_INK.get().defaultBlockState(), 3);
            }
        }
        int dx = 6;
        for (DecayType type : DecayType.values()) {
            level.setBlock(new BlockPos(x0 + dx, y, z0 - 3), ModBlocks.decay(type).get().defaultBlockState(), 3);
            dx += 2;
        }
        for (int dy = 0; dy < 3; dy++) level.setBlock(new BlockPos(x0 + WIDTH - 2, y + dy, z0 - 3), ModBlocks.CRYSTAL.get().defaultBlockState(), 3);

        // Row B (z0-6): workstations, all facing south (towards the player).
        BlockPos desk = new BlockPos(x0 + 1, y, z0 - 6);
        BlockState deskHead = ModBlocks.WRITING_DESK.get().defaultBlockState().setValue(WritingDeskBlock.FACING, Direction.EAST);
        level.setBlock(desk, deskHead, 3);
        level.setBlock(desk.east(), deskHead.setValue(WritingDeskBlock.FOOT, true), 3);
        level.setBlock(desk.above(), deskHead.setValue(WritingDeskBlock.TOP, true), 3);
        level.setBlock(desk.east().above(), deskHead.setValue(WritingDeskBlock.TOP, true).setValue(WritingDeskBlock.FOOT, true), 3);

        BlockPos stand = new BlockPos(x0 + 5, y, z0 - 6);
        level.setBlock(stand, ModBlocks.BOOKSTAND.get().defaultBlockState(), 3);
        putBook(level, stand, LinkingBookItem.createAt(viewer));

        BlockPos lectern = new BlockPos(x0 + 8, y, z0 - 6);
        level.setBlock(lectern, ModBlocks.LECTERN.get().defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH), 3);
        putBook(level, lectern, descriptiveBook(server, "Lectern"));

        level.setBlock(new BlockPos(x0 + 11, y, z0 - 6), ModBlocks.INK_MIXER.get().defaultBlockState(), 3);
        level.setBlock(new BlockPos(x0 + 14, y, z0 - 6), ModBlocks.BOOK_BINDER.get().defaultBlockState(), 3);
        level.setBlock(new BlockPos(x0 + 17, y, z0 - 6), ModBlocks.LINK_MODIFIER.get().defaultBlockState(), 3);

        // Row C (z0-9): portal frame in the x/y plane (visible face towards the player) + star fissure.
        int px = x0 + 3;
        for (int fx = 0; fx < 3; fx++) {
            for (int fy = 0; fy < 3; fy++) {
                if (fx == 1 && fy == 1) continue;
                level.setBlock(new BlockPos(px + fx, y + fy, z0 - 9), ModBlocks.CRYSTAL.get().defaultBlockState(), 3);
            }
        }
        BlockPos receptacle = new BlockPos(px + 1, y + 3, z0 - 9);
        level.setBlock(receptacle, ModBlocks.BOOK_RECEPTACLE.get().defaultBlockState().setValue(BookReceptacleBlock.ROTATION, Direction.UP), 3);
        putBook(level, receptacle, descriptiveBook(server, "Portal Scene")); // fires the portal

        for (int fx = 0; fx < 2; fx++) {
            for (int fz = 0; fz < 2; fz++) {
                level.setBlock(new BlockPos(x0 + 12 + fx, y - 1, z0 - 9 - fz), ModBlocks.STAR_FISSURE.get().defaultBlockState(), 3);
            }
        }

        // Viewer: centred, two blocks in front of the pad, looking north and slightly down.
        BlockPos view = new BlockPos(x0 + WIDTH / 2, y, z0 + 2);
        level.setBlock(view.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        viewer.teleportTo(level, view.getX() + 0.5, view.getY(), view.getZ() + 0.5, Set.of(), 180f, 10f, true);
        Mystcraft.LOGGER.info("[scene] built debug scene at {} in {}; viewer at {} (portal field expected at {})",
                origin.toShortString(), level.dimension().identifier(), view.toShortString(), new BlockPos(px + 1, y + 1, z0 - 9).toShortString());
        return view;
    }

    private static void putBook(ServerLevel level, BlockPos pos, ItemStack book) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BookDisplayBlockEntity display) {
            display.setBook(book);
            display.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
        } else {
            Mystcraft.LOGGER.warn("[scene] no book display block entity at {} ({})", pos.toShortString(), level.getBlockState(pos));
        }
    }

    /** A Descriptive Book bound to a fresh Age (so receptacles power portals and lecterns show a title). */
    private static ItemStack descriptiveBook(MinecraftServer server, String name) {
        AgeData data = AgeManager.createAge(server);
        data.setName(name);
        data.setPages(List.of(PageItem.createLinkPanel(Set.of())));
        ItemStack book = new ItemStack(ModItems.DESCRIPTIVE_BOOK.get());
        DescriptiveBookItem.initializeForAge(book, data);
        return book;
    }

}

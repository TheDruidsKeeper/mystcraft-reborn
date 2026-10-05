package com.tbd.mystcraft.linking;

import com.tbd.mystcraft.block.BookReceptacleBlock;
import com.tbd.mystcraft.block.CrystalBlock;
import com.tbd.mystcraft.block.LinkPortalBlock;
import com.tbd.mystcraft.blockentity.BookReceptacleBlockEntity;
import com.tbd.mystcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Crystal portal flood-fill, tension and pathing (original spec §7.7). Port of the original {@code PortalUtils}.
 * <p>
 * Every crystal / portal block that is part of an active portal carries {@code ACTIVE=true} and a {@code SOURCE}
 * direction pointing one step back toward the Book Receptacle that powers it. The receptacle is found by following
 * the chain ({@link #getReceptacle(Level, BlockPos)}).
 */
public final class PortalUtils {
    private PortalUtils() {}

    /** Neighbour offsets in the original processing order (east, up, south, west, down, north). */
    private static final Direction[] ORDER = {Direction.EAST, Direction.UP, Direction.SOUTH, Direction.WEST, Direction.DOWN, Direction.NORTH};

    public static Block portalBlock() {
        return ModBlocks.LINK_PORTAL.get();
    }

    public static Block frameBlock() {
        return ModBlocks.CRYSTAL.get();
    }

    public static Block receptacleBlock() {
        return ModBlocks.BOOK_RECEPTACLE.get();
    }

    /** 1 for crystal / portal blocks, 0 otherwise. */
    public static int isValidLinkPortalBlock(BlockState state) {
        return state.is(frameBlock()) || state.is(portalBlock()) ? 1 : 0;
    }

    private static Direction getBlockFacing(BlockState state) {
        if (state.is(frameBlock())) return state.getValue(CrystalBlock.SOURCE);
        if (state.is(portalBlock())) return state.getValue(LinkPortalBlock.SOURCE);
        if (state.is(receptacleBlock())) return state.getValue(BookReceptacleBlock.ROTATION);
        return Direction.DOWN;
    }

    private static boolean isBlockActive(BlockState state) {
        if (state.is(frameBlock())) return state.getValue(CrystalBlock.ACTIVE);
        if (state.is(portalBlock())) return state.getValue(LinkPortalBlock.ACTIVE);
        return false;
    }

    private static BlockState getDirectedState(BlockState state, Direction source) {
        if (state.is(frameBlock())) return state.setValue(CrystalBlock.ACTIVE, true).setValue(CrystalBlock.SOURCE, source);
        if (state.is(portalBlock())) return state.setValue(LinkPortalBlock.ACTIVE, true).setValue(LinkPortalBlock.SOURCE, source);
        return state;
    }

    private static BlockState getDisabledState(BlockState state) {
        if (state.is(frameBlock())) return state.setValue(CrystalBlock.ACTIVE, false);
        if (state.is(portalBlock())) return state.setValue(LinkPortalBlock.ACTIVE, false);
        return state;
    }

    /** The crystal a receptacle is attached to. */
    public static BlockPos getReceptacleBase(BlockPos pos, Direction facing) {
        return pos.relative(facing.getOpposite());
    }

    // --- public API --------------------------------------------------------------------------------------------

    /** Called by the portal block after a neighbour update / random tick: removes unstable portal blocks (cascading). */
    public static void validatePortal(Level level, BlockPos start) {
        if (level.isClientSide()) return;
        Deque<BlockPos> blocks = new ArrayDeque<>();
        blocks.add(start);
        while (!blocks.isEmpty()) {
            BlockPos coords = blocks.poll();
            if (!level.getBlockState(coords).is(portalBlock())) continue;
            if (!isPortalBlockStable(level, coords)) {
                level.setBlock(coords, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                addSurrounding(blocks, coords);
            }
        }
    }

    /** A book was inserted into the receptacle at {@code receptaclePos}: grow the portal and path it. */
    public static void firePortal(Level level, BlockPos receptaclePos) {
        if (level.isClientSide()) return;
        BlockState state = level.getBlockState(receptaclePos);
        if (!state.is(receptacleBlock())) return;
        BlockPos base = getReceptacleBase(receptaclePos, getBlockFacing(state));
        onPulse(level, base);
        pathTo(level, receptaclePos);
    }

    /** The receptacle at {@code receptaclePos} lost its book (or was removed): deactivate everything connected. */
    public static void shutdownPortal(Level level, BlockPos receptaclePos) {
        if (level.isClientSide()) return;
        unpath(level, receptaclePos);
    }

    /**
     * Follows {@code SOURCE} links from a crystal / portal block through active blocks until a receptacle is reached.
     *
     * @return the receptacle block entity, or {@code null} if the chain is broken or loops
     */
    public static @Nullable BookReceptacleBlockEntity getReceptacle(Level level, BlockPos pos) {
        return getReceptacle((BlockGetter) level, pos);
    }

    public static @Nullable BookReceptacleBlockEntity getReceptacle(BlockGetter level, BlockPos pos) {
        Set<BlockPos> visited = new HashSet<>();
        BlockState state = level.getBlockState(pos);
        while (!state.is(receptacleBlock())) {
            if (isValidLinkPortalBlock(state) == 0) return null;
            if (!isBlockActive(state)) return null;
            if (!visited.add(pos)) return null;
            pos = pos.relative(getBlockFacing(state));
            state = level.getBlockState(pos);
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof BookReceptacleBlockEntity r ? r : null;
    }

    // --- pathing -----------------------------------------------------------------------------------------------

    private static void pathTo(Level level, BlockPos pos) {
        Deque<BlockPos> blocks = new ArrayDeque<>();
        Deque<BlockPos> portals = new ArrayDeque<>();
        Deque<BlockPos> repath = new ArrayDeque<>();
        List<BlockPos> redraw = new ArrayList<>();
        blocks.add(pos);
        while (!portals.isEmpty() || !blocks.isEmpty()) {
            while (!blocks.isEmpty()) {
                BlockPos coords = blocks.poll();
                for (Direction d : ORDER) directPortal(level, coords.relative(d), d.getOpposite(), blocks, portals);
                redraw.add(coords);
            }
            if (!portals.isEmpty()) {
                BlockPos coords = portals.poll();
                for (Direction d : ORDER) directPortal(level, coords.relative(d), d.getOpposite(), blocks, portals);
                if (level.getBlockState(coords).is(portalBlock())) repath.add(coords);
            }
        }
        while (!repath.isEmpty()) {
            BlockPos coords = repath.poll();
            if (level.getBlockState(coords).is(portalBlock())) {
                if (!isPortalBlockStable(level, coords)) {
                    repathNeighbors(level, coords);
                    level.setBlock(coords, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
                    addSurrounding(repath, coords);
                } else {
                    redraw.add(coords);
                }
            }
        }
        for (BlockPos coords : redraw) {
            if (level.isLoaded(coords)) {
                BlockState s = level.getBlockState(coords);
                level.sendBlockUpdated(coords, s, s, Block.UPDATE_ALL);
            }
        }
    }

    private static void directPortal(Level level, BlockPos pos, Direction source, Deque<BlockPos> blocks, Deque<BlockPos> portals) {
        BlockState state = level.getBlockState(pos);
        if (isValidLinkPortalBlock(state) == 0) return;
        if (isBlockActive(state)) return;
        level.setBlock(pos, getDirectedState(state, source), Block.UPDATE_NONE);
        if (state.is(portalBlock())) portals.add(pos); else blocks.add(pos);
    }

    private static void repathNeighbors(Level level, BlockPos pos) {
        BookReceptacleBlockEntity receptacle = getReceptacle(level, pos);
        Deque<BlockPos> blocks = new ArrayDeque<>();
        blocks.add(pos);
        level.setBlock(pos, getDisabledState(level.getBlockState(pos)), Block.UPDATE_CLIENTS);
        while (!blocks.isEmpty()) {
            BlockPos coords = blocks.poll();
            for (Direction d : ORDER) redirectPortal(level, receptacle, coords.relative(d), d.getOpposite(), blocks);
        }
    }

    private static void redirectPortal(Level level, @Nullable BookReceptacleBlockEntity receptacle, BlockPos pos, Direction source, Deque<BlockPos> blocks) {
        BlockState state = level.getBlockState(pos);
        if (isValidLinkPortalBlock(state) == 0) return;
        if (isBlockActive(state) && getBlockFacing(state) == source) {
            for (Direction alt : Direction.values()) {
                if (alt == source) continue;
                level.setBlock(pos, getDirectedState(state, alt), Block.UPDATE_CLIENTS);
                BookReceptacleBlockEntity local = getReceptacle(level, pos);
                if (local == receptacle || (local != null && receptacle == null)) return; // still valid
            }
            level.setBlock(pos, state.getBlock().defaultBlockState(), Block.UPDATE_CLIENTS);
            blocks.add(pos);
        }
    }

    private static void unpath(Level level, BlockPos pos) {
        Deque<BlockPos> blocks = new ArrayDeque<>();
        List<BlockPos> notify = new ArrayList<>();
        blocks.add(pos);
        while (!blocks.isEmpty()) {
            BlockPos coords = blocks.poll();
            for (Direction d : ORDER) depolarize(level, coords.relative(d), blocks);
            notify.add(coords);
        }
        for (BlockPos coords : notify) {
            if (level.isLoaded(coords)) {
                BlockState s = level.getBlockState(coords);
                level.sendBlockUpdated(coords, s, s, Block.UPDATE_ALL);
            }
        }
    }

    private static void depolarize(Level level, BlockPos pos, Deque<BlockPos> blocks) {
        BlockState state = level.getBlockState(pos);
        if (isValidLinkPortalBlock(state) == 0) return;
        if (!isBlockActive(state)) return;
        level.setBlock(pos, state.getBlock().defaultBlockState(), Block.UPDATE_NONE);
        if (state.is(portalBlock()) && !isPortalBlockStable(level, pos)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        blocks.add(pos);
    }

    // --- growth ------------------------------------------------------------------------------------------------

    private static void onPulse(Level level, BlockPos pos) {
        Deque<BlockPos> set = new ArrayDeque<>();
        Deque<BlockPos> validate = new ArrayDeque<>();
        addSurrounding(set, pos);
        while (!set.isEmpty()) {
            expandPortal(level, set.poll(), set, validate);
        }
        while (!validate.isEmpty()) {
            BlockPos coords = validate.pop();
            if (!checkPortalTension(level, coords)) {
                level.setBlock(coords, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
            }
        }
    }

    private static void expandPortal(Level level, BlockPos pos, Deque<BlockPos> set, Deque<BlockPos> created) {
        if (!level.getBlockState(pos).isAir()) return;
        int score = 0;
        for (Direction d : Direction.values()) score += isValidLinkPortalBlock(level.getBlockState(pos.relative(d)));
        if (score > 1) {
            level.setBlock(pos, portalBlock().defaultBlockState(), Block.UPDATE_NONE);
            created.push(pos);
            addSurrounding(set, pos);
        }
    }

    private static boolean isPortalBlockStable(Level level, BlockPos pos) {
        if (level.isClientSide()) return true;
        if (!checkPortalTension(level, pos)) return false;
        return getReceptacle(level, pos) != null;
    }

    /** Counts axes whose both opposite neighbours are crystal/portal; stable when at least two. */
    private static boolean checkPortalTension(Level level, BlockPos pos) {
        if (level.isClientSide()) return true;
        int score = 0;
        if (isValidLinkPortalBlock(level.getBlockState(pos.east())) > 0 && isValidLinkPortalBlock(level.getBlockState(pos.west())) > 0) ++score;
        if (isValidLinkPortalBlock(level.getBlockState(pos.above())) > 0 && isValidLinkPortalBlock(level.getBlockState(pos.below())) > 0) ++score;
        if (isValidLinkPortalBlock(level.getBlockState(pos.south())) > 0 && isValidLinkPortalBlock(level.getBlockState(pos.north())) > 0) ++score;
        return score > 1; // score == 2 yields forcefield walls
    }

    /** The 18 positions around {@code pos}: the 6 face neighbours and the 12 edge neighbours. */
    private static void addSurrounding(Collection<BlockPos> set, BlockPos pos) {
        set.add(pos.east());
        set.add(pos.west());
        set.add(pos.above());
        set.add(pos.below());
        set.add(pos.south());
        set.add(pos.north());

        set.add(pos.east().above());
        set.add(pos.west().above());
        set.add(pos.east().below());
        set.add(pos.west().below());
        set.add(pos.south().above());
        set.add(pos.north().above());
        set.add(pos.south().below());
        set.add(pos.north().below());
        set.add(pos.east().south());
        set.add(pos.west().south());
        set.add(pos.east().north());
        set.add(pos.west().north());
    }
}

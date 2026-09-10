package com.techbucketdivision.mystcraft.world.structure;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.logic.Populator;
import com.techbucketdivision.mystcraft.blockentity.BookDisplayBlockEntity;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.symbol.CardRanks;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * The Mystcraft Library (REQUIREMENTS §11.1, layout Appendix B): an 11×11×11 cobblestone/bookshelf building placed
 * once per 32×32-chunk region (candidate chunk = region origin + rand(24), seed salt 14357617, no biome check), at the
 * average ground level of its footprint, with a cobblestone foundation, a loot chest ({@code mystcraft:chests/library})
 * and five lecterns holding rank ≥ 3 symbol pages. Runs as the last {@link Populator} of every Age.
 */
public final class MystcraftLibrary implements Populator {
    public static final ResourceKey<LootTable> LOOT_TABLE = ResourceKey.create(Registries.LOOT_TABLE, MystIds.id("chests/library"));
    private static final int SPACING = 32;
    private static final int SEPARATION = 8;
    private static final int SALT = 14357617;
    private static final int SIZE = 11;

    /** Local (x,y,z) of the five lecterns and the (x,z) they look at (Appendix B). */
    private static final int[][] LECTERNS = {{6, 2, 2}, {8, 2, 4}, {8, 2, 5}, {8, 2, 6}, {6, 2, 8}};
    private static final int[][] LECTERN_TARGETS = {{6, 3}, {7, 4}, {7, 5}, {7, 6}, {6, 7}};
    private static final int[] CHEST = {4, 1, 2};

    /**
     * Appendix B, verbatim: LAYOUT[y][z] is an 11-character row for x = 0..10.
     * '.' air, 'C' cobblestone, 'B' bookshelf, 'P' oak planks, 'X' cobweb, '#' cobblestone wall, 's' bottom slab,
     * 'S' top slab, n/u/e/w stairs facing north/south/east/west (upper-case = upside-down), ' ' untouched.
     */
    private static final String[][] LAYOUT = {
            { // y = 0
                    "  ennnnnnnn", "  eCCCCCCCw", "  eCCCCCCCw", "nneCCCCCCCw", "eCCCCCCCCCw", "eCCCCCCCCCw",
                    "eCCCCCCCCCw", "uuuCCCCCCCw", "  eCCCCCCCw", "  eCCCCCCCw", "  uuuuuuuuw"},
            { // y = 1
                    "           ", "   CCCCCCC ", "   C BBBPC ", "...CB...BC ", ".#.C....BC ", "........BC ",
                    ".#.C....BC ", "...CB...BC ", "   CPBBBPC ", "   CCCCCCC ", "           "},
            { // y = 2
                    "           ", "   CCCCCCC ", "   CPB BPC ", "...CB...BC ", ".#.C.... C ", "........ C ",
                    ".#.C.... C ", "...CB...BC ", "   CPB BPC ", "   CCCCCCC ", "           "},
            { // y = 3
                    "           ", "   CCCCCCC ", "   CPBBBPC ", "...CB...BC ", ".#.C....BC ", "...S....BC ",
                    ".#.C....BC ", "...CB...BC ", "   CPBBBPC ", "   CCCCCCC ", "           "},
            { // y = 4
                    "           ", "   CCCCCCC ", "   CPBBBPC ", " nnCB...BC ", " sCC....BC ", " sCC....BC ",
                    " sCC....BC ", " uuCB...BC ", "   CPBBBPC ", "   CCCCCCC ", "           "},
            { // y = 5
                    "           ", "   CCCCCCC ", "   CXX..XC ", "   C....XC ", " nnC....XC ", " SCC....XC ",
                    " uuC....XC ", "   C.....C ", "   C.XXXXC ", "   CCCCCCC ", "           "},
            { // y = 6
                    "           ", "   CCCCCCC ", "   CXXXX.C ", "   CXXX.XC ", "   CXX...C ", "   CX....C ",
                    "   CX...XC ", "   C..X..C ", "   C..XX.C ", "   CCCCCCC ", "           "},
            { // y = 7
                    "  NNNNNNNNW", "  ECCCCCCCW", "  ECCCCCCCW", "  ECCCCCCCW", "  ECCCCCCCW", "  ECCCCCCCW",
                    "  ECCCCCCCW", "  ECCCCCCCW", "  ECCCCCCCW", "  ECCCCCCCW", "  EUUUUUUUU"},
            { // y = 8
                    "           ", "           ", "           ", "   nnnnnnn ", "   eCCCCCw ", "   eCCCCCw ",
                    "   eCCCCCw ", "   uuuuuuw ", "           ", "           ", "           "},
            { // y = 9
                    "           ", "           ", "           ", "           ", "    ennnw  ", "    eCCCw  ",
                    "    euuuu  ", "           ", "           ", "           ", "           "},
            { // y = 10
                    "           ", "           ", "           ", "           ", "           ", "     sss   ",
                    "           ", "           ", "           ", "           ", "           "},
    };

    private final long seed;

    public MystcraftLibrary(long seed) {
        this.seed = seed;
    }

    /** Whether {@code (chunkX, chunkZ)} is the library candidate of its 32×32 region (vanilla scattered-feature rule). */
    public boolean isLibraryChunk(int chunkX, int chunkZ) {
        int rx = Math.floorDiv(chunkX, SPACING);
        int rz = Math.floorDiv(chunkZ, SPACING);
        Random rand = new Random(rx * 341873128712L + rz * 132897987541L + seed + SALT);
        int cx = rx * SPACING + rand.nextInt(SPACING - SEPARATION);
        int cz = rz * SPACING + rand.nextInt(SPACING - SEPARATION);
        return cx == chunkX && cz == chunkZ;
    }

    @Override
    public boolean populate(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag) {
        if (!isLibraryChunk(chunkX, chunkZ)) return false;
        Rotation rotation = Rotation.getRandom(random);
        // Footprint fully inside this chunk (x/z 2..12) so every write stays in the decoration region.
        int originX = (chunkX << 4) + 2;
        int originZ = (chunkZ << 4) + 2;
        int ground = averageGroundLevel(level, originX, originZ);
        if (ground < level.getMinY()) return false;
        place(level, random, new BlockPos(originX, ground, originZ), rotation);
        return true;
    }

    private static int averageGroundLevel(WorldGenLevel level, int originX, int originZ) {
        long sum = 0;
        int n = 0;
        int floor = 64;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                sum += Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, originX + x, originZ + z), floor);
                n++;
            }
        }
        return n == 0 ? -1 : (int) (sum / n);
    }

    /** Places the library with its local origin (x=0,y=0,z=0) at {@code origin}, rotated about the footprint centre. */
    public void place(WorldGenLevel level, RandomSource random, BlockPos origin, Rotation rotation) {
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        for (int y = 0; y < LAYOUT.length; y++) {
            String[] rows = LAYOUT[y];
            for (int z = 0; z < SIZE; z++) {
                String row = rows[z];
                for (int x = 0; x < SIZE; x++) {
                    char c = x < row.length() ? row.charAt(x) : ' ';
                    if (c == ' ') continue;
                    BlockState state = blockFor(c, rotation);
                    if (state == null) continue;
                    level.setBlock(world(origin, rotation, x, y, z), state, 2);
                }
            }
        }
        // Foundation: cobblestone under every column of the footprint down to solid ground.
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                BlockPos.MutableBlockPos at = world(origin, rotation, x, -1, z).mutable();
                while (at.getY() >= level.getMinY()) {
                    BlockState s = level.getBlockState(at);
                    if (!s.isAir() && !s.liquid()) break;
                    level.setBlock(at, cobble, 2);
                    at.move(Direction.DOWN);
                }
            }
        }
        // Loot chest.
        BlockPos chestPos = world(origin, rotation, CHEST[0], CHEST[1], CHEST[2]);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
        RandomizableContainer.setBlockEntityLootTable(level, random, chestPos, LOOT_TABLE);
        // Lecterns.
        for (int i = 0; i < LECTERNS.length; i++) {
            int[] lc = LECTERNS[i];
            int[] lt = LECTERN_TARGETS[i];
            BlockPos pos = world(origin, rotation, lc[0], lc[1], lc[2]);
            Direction facing = rotation.rotate(Direction.getApproximateNearest(lt[0] - lc[0], 0, lt[1] - lc[2]));
            placeLectern(level, random, pos, facing);
        }
    }

    private static BlockPos world(BlockPos origin, Rotation rotation, int x, int y, int z) {
        int max = SIZE - 1;
        int wx;
        int wz;
        switch (rotation) {
            case CLOCKWISE_90 -> {
                wx = max - z;
                wz = x;
            }
            case CLOCKWISE_180 -> {
                wx = max - x;
                wz = max - z;
            }
            case COUNTERCLOCKWISE_90 -> {
                wx = z;
                wz = max - x;
            }
            default -> {
                wx = x;
                wz = z;
            }
        }
        return origin.offset(wx, y, wz);
    }

    private static @Nullable BlockState blockFor(char c, Rotation rotation) {
        return switch (c) {
            case '.' -> Blocks.AIR.defaultBlockState();
            case 'C' -> Blocks.COBBLESTONE.defaultBlockState();
            case 'B' -> Blocks.BOOKSHELF.defaultBlockState();
            case 'P' -> Blocks.OAK_PLANKS.defaultBlockState();
            case 'X' -> Blocks.COBWEB.defaultBlockState();
            case '#' -> Blocks.COBBLESTONE_WALL.defaultBlockState();
            case 's' -> Blocks.COBBLESTONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
            case 'S' -> Blocks.COBBLESTONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
            case 'n', 'u', 'e', 'w', 'N', 'U', 'E', 'W' -> stairs(c, rotation);
            default -> null;
        };
    }

    private static BlockState stairs(char c, Rotation rotation) {
        Direction facing = switch (Character.toLowerCase(c)) {
            case 'n' -> Direction.NORTH;
            case 'u' -> Direction.SOUTH;
            case 'e' -> Direction.EAST;
            default -> Direction.WEST;
        };
        Half half = Character.isUpperCase(c) ? Half.TOP : Half.BOTTOM;
        return Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, rotation.rotate(facing))
                .setValue(BlockStateProperties.HALF, half);
    }

    private static void placeLectern(WorldGenLevel level, RandomSource random, BlockPos pos, Direction facing) {
        BlockState state = ModBlocks.LECTERN.get().defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        level.setBlock(pos, state, 2);
        ItemStack page = randomLecternPage(random);
        if (page.isEmpty()) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BookDisplayBlockEntity display) {
            display.setBook(page);
        } else {
            Mystcraft.LOGGER.debug("Library lectern at {} has no BookDisplayBlockEntity; page not placed", pos);
        }
    }

    /** A random rank ≥ 3 symbol page (weighted by item weight), or empty when no such symbol exists. */
    public static ItemStack randomLecternPage(RandomSource random) {
        List<AgeSymbol> candidates = CardRanks.ofRankAtLeast(3);
        if (candidates.isEmpty()) return ItemStack.EMPTY;
        AgeSymbol symbol = CardRanks.weightedRandom(candidates, random);
        if (symbol == null) symbol = candidates.get(random.nextInt(candidates.size()));
        return PageItem.createSymbolPage(symbol);
    }
}

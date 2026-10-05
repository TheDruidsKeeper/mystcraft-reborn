package com.tbd.mystcraft.instability.effects;

import com.tbd.mystcraft.api.symbol.logic.EnvironmentalEffect;
import com.tbd.mystcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces one random block per chunk tick by its "crumbled" mapping (original spec §6.5 EffectCrumble). Explicit
 * block mappings win over tag mappings (ores → stone, logs → planks, wool → white wool → cobweb, plants → air).
 */
public final class CrumbleEffect implements EnvironmentalEffect {
    private static final Map<Block, BlockState> BLOCK_MAP = new HashMap<>();
    private static final Map<BlockState, BlockState> STATE_MAP = new HashMap<>();
    private static final List<Map.Entry<TagKey<Block>, BlockState>> TAG_MAP = new ArrayList<>();
    private static boolean initialized;

    private final ChunkLcg lcg = new ChunkLcg();

    public CrumbleEffect() {}

    public static synchronized void initMappings() {
        if (initialized) return;
        initialized = true;
        BlockState stone = Blocks.STONE.defaultBlockState();
        tag(BlockTags.COAL_ORES, stone);
        tag(BlockTags.IRON_ORES, stone);
        tag(BlockTags.REDSTONE_ORES, stone);
        tag(BlockTags.GOLD_ORES, stone);
        tag(BlockTags.LAPIS_ORES, stone);
        tag(BlockTags.COPPER_ORES, stone);
        tag(BlockTags.EMERALD_ORES, stone);
        tag(BlockTags.DIAMOND_ORES, Blocks.COAL_ORE.defaultBlockState());
        block(Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_COAL_ORE);

        block(Blocks.ICE, Blocks.WATER);
        block(Blocks.PACKED_ICE, Blocks.ICE);
        block(Blocks.GLOWSTONE, Blocks.GLASS);
        block(ModBlocks.CRYSTAL.get(), Blocks.GLASS);

        block(Blocks.NETHER_BRICKS, Blocks.NETHERRACK);
        block(Blocks.NETHER_QUARTZ_ORE, Blocks.NETHERRACK);
        block(Blocks.NETHERRACK, Blocks.SOUL_SAND);
        block(Blocks.SOUL_SAND, Blocks.GRAVEL);

        tag(BlockTags.STONE_BRICKS, stone);
        block(Blocks.STONE, Blocks.GRAVEL);
        block(Blocks.DEEPSLATE, Blocks.GRAVEL);
        block(Blocks.COBBLESTONE, Blocks.GRAVEL);
        block(Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL);
        block(Blocks.GRASS_BLOCK, Blocks.DIRT);
        block(Blocks.MYCELIUM, Blocks.DIRT);
        block(Blocks.BROWN_MUSHROOM_BLOCK, Blocks.DIRT);
        block(Blocks.RED_MUSHROOM_BLOCK, Blocks.DIRT);
        block(Blocks.CLAY, Blocks.DIRT);

        block(Blocks.GRAVEL, Blocks.SAND);
        block(Blocks.DIRT, Blocks.SAND);
        block(Blocks.GLASS, Blocks.SAND);
        block(Blocks.SANDSTONE, Blocks.SAND);

        tag(BlockTags.LOGS, Blocks.OAK_PLANKS.defaultBlockState());
        tag(BlockTags.PLANKS, Blocks.DIRT.defaultBlockState());

        tag(BlockTags.WOOL, Blocks.WHITE_WOOL.defaultBlockState());
        block(Blocks.WHITE_WOOL, Blocks.COBWEB);

        BlockState air = Blocks.AIR.defaultBlockState();
        tag(BlockTags.SAPLINGS, air);
        block(Blocks.COBWEB, Blocks.AIR);
        tag(BlockTags.LEAVES, air);
        block(Blocks.SHORT_GRASS, Blocks.AIR);
        block(Blocks.TALL_GRASS, Blocks.AIR);
        block(Blocks.FERN, Blocks.AIR);
        block(Blocks.BROWN_MUSHROOM, Blocks.AIR);
        block(Blocks.RED_MUSHROOM, Blocks.AIR);
        tag(BlockTags.FLOWERS, air);
    }

    public static synchronized void block(Block from, Block to) {
        BLOCK_MAP.put(from, to.defaultBlockState());
    }

    public static synchronized void state(BlockState from, BlockState to) {
        STATE_MAP.put(from, to);
    }

    public static synchronized void tag(TagKey<Block> from, BlockState to) {
        TAG_MAP.add(Map.entry(from, to));
    }

    public static @Nullable BlockState mappingFor(BlockState state) {
        BlockState s = STATE_MAP.get(state);
        if (s != null) return s;
        s = BLOCK_MAP.get(state.getBlock());
        if (s != null) return s;
        for (Map.Entry<TagKey<Block>, BlockState> e : TAG_MAP) {
            if (state.is(e.getKey())) return e.getValue();
        }
        return null;
    }

    @Override
    public void tick(ServerLevel level, LevelChunk chunk) {
        int coords = lcg.next();
        int x = chunk.getPos().getMinBlockX() + ChunkLcg.localX(coords);
        int z = chunk.getPos().getMinBlockZ() + ChunkLcg.localZ(coords);
        int y = ChunkLcg.y255(coords) + level.getMinY();
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        BlockState mapped = mappingFor(state);
        if (mapped != null && mapped != state) {
            level.setBlock(pos, mapped, Block.UPDATE_ALL);
        }
    }
}

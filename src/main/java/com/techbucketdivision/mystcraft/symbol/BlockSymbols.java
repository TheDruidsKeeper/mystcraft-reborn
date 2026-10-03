package com.techbucketdivision.mystcraft.symbol;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.symbol.AgeDirector;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.BlockCategory;
import com.techbucketdivision.mystcraft.api.symbol.BlockDescriptor;
import com.techbucketdivision.mystcraft.api.symbol.WordData;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.symbol.grammar.Grammar;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Block modifier symbols (REQUIREMENTS §4.3.13). A block symbol pushes a {@link BlockDescriptor} with its usable
 * categories; one grammar rule {@code Block<Category> -> symbol} per category with the category's rank.
 * <p>
 * Ids: {@code mystcraft:block_<blockpath>[_<propertyvalues>]} (see {@link #idFor(BlockState)}). Display name:
 * {@code symbol.mystcraft.block.wrapper} = "%s Block" with the block's name.
 */
public final class BlockSymbols {
    private BlockSymbols() {}

    public static final String WRAPPER_KEY = "symbol.mystcraft.block.wrapper";

    /** The block symbol class (public so add-ons and the fluid symbols can reuse it). */
    public static final class BlockSymbol extends AgeSymbol {
        private final BlockDescriptor descriptor;
        private final Map<BlockCategory, Integer> categoryRanks;

        BlockSymbol(Identifier id, BlockDescriptor descriptor, @Nullable Integer cardRank, Map<BlockCategory, Integer> categoryRanks,
                    String thirdWord) {
            super(id, cardRank, WordData.MODIFIER, WordData.CONSTRAINT, thirdWord, id.getPath());
            this.descriptor = descriptor;
            this.categoryRanks = Collections.unmodifiableMap(new LinkedHashMap<>(categoryRanks));
        }

        public BlockDescriptor descriptor() {
            return descriptor;
        }

        public BlockState state() {
            return descriptor.state();
        }

        /** Grammar rank per usable category ({@code null} value = connect-only rule). */
        public Map<BlockCategory, Integer> categoryRanks() {
            return categoryRanks;
        }

        @Override
        public String descriptionId() {
            return WRAPPER_KEY;
        }

        @Override
        public Component displayName() {
            return Component.translatable(WRAPPER_KEY, blockName(descriptor.state()));
        }

        @Override
        public Component description() {
            return Component.translatable(WRAPPER_KEY + ".desc", blockName(descriptor.state()));
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.pushBlock(descriptor);
        }
    }

    /**
     * Creates a block symbol (not registered). {@code categoryRanks} keys become the usable categories; values are the
     * grammar ranks of the {@code Block<Category> -> symbol} rules ({@code null} = connect-only).
     */
    public static AgeSymbol createBlockSymbol(BlockState state, String thirdWord, @Nullable Integer rank, Map<BlockCategory, Integer> categoryRanks) {
        BlockDescriptor descriptor = new BlockDescriptor(state, categoryRanks.keySet().toArray(new BlockCategory[0]));
        return new BlockSymbol(idFor(state), descriptor, rank, categoryRanks, thirdWord);
    }

    /**
     * Registers a block symbol together with its grammar rules. Returns {@code false} if the registry rejected it
     * (duplicate id, config, blacklist).
     */
    public static boolean register(AgeSymbol symbol) {
        if (!(symbol instanceof BlockSymbol block)) throw new IllegalArgumentException("Not a block symbol: " + symbol);
        boolean ok = SymbolRegistry.isFrozen() ? SymbolRegistry.registerLate(symbol) : SymbolRegistry.register(symbol);
        if (ok) addRules(block);
        return ok;
    }

    private static void addRules(BlockSymbol block) {
        for (Map.Entry<BlockCategory, Integer> e : block.categoryRanks().entrySet()) {
            Grammar.addSymbolRule(block, e.getKey().grammarToken(), e.getValue());
        }
    }

    /** {@code mystcraft:block_<path>[_<value>...]}; other namespaces get {@code block_<ns>_<path>}. */
    public static Identifier idFor(BlockState state) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        StringBuilder sb = new StringBuilder("block_");
        if (!blockId.getNamespace().equals("minecraft") && !blockId.getNamespace().equals(Mystcraft.MOD_ID)) {
            sb.append(MystIds.pathSafe(blockId.getNamespace())).append('_');
        }
        sb.append(MystIds.pathSafe(blockId.getPath()));
        BlockState defaultState = state.getBlock().defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            String suffix = nonDefaultValueName(state, defaultState, property);
            if (suffix != null) sb.append('_').append(MystIds.pathSafe(suffix));
        }
        return MystIds.id(sb.toString());
    }

    /** Name of the property value if it differs from the default state's, else null. */
    private static <T extends Comparable<T>> @Nullable String nonDefaultValueName(BlockState state, BlockState defaultState, Property<T> property) {
        T value = state.getValue(property);
        return value.equals(defaultState.getValue(property)) ? null : property.getName(value);
    }

    /** Translated block name used inside the wrapper. */
    public static Component blockName(BlockState state) {
        return state.getBlock().getName();
    }

    // --- built-in table (§4.3.13) ------------------------------------------------------------------------------------

    public static void registerAll() {
        terrain(Blocks.DIRT, 2, 4, 2, 1);
        terrain(Blocks.STONE, 2, 1, 2, 1);
        terrain(Blocks.GRANITE, 2, 2, 2, 1);
        terrain(Blocks.DIORITE, 2, 2, 2, 1);
        terrain(Blocks.ANDESITE, 2, 2, 2, 1);
        structureStone(Blocks.POLISHED_GRANITE);
        structureStone(Blocks.POLISHED_DIORITE);
        structureStone(Blocks.POLISHED_ANDESITE);
        terrain(Blocks.SANDSTONE, 2, 2, 1, 1);
        terrain(Blocks.NETHERRACK, 2, 3, 2, 2);
        terrain(Blocks.END_STONE, 3, 4, 3, 3);

        add(Blocks.NETHER_BRICKS, WordData.STRUCTURE, 2, ranks(BlockCategory.SOLID, 2, BlockCategory.STRUCTURE, 2));
        for (Block log : new Block[] {Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG}) {
            add(log, WordData.STRUCTURE, 2, ranks(BlockCategory.SOLID, 1, BlockCategory.ORGANIC, 1, BlockCategory.STRUCTURE, 1));
        }

        ore(Blocks.DIAMOND_ORE, 5, 6);
        ore(Blocks.GOLD_ORE, 4, 5);
        ore(Blocks.IRON_ORE, 3, 4);
        ore(Blocks.COAL_ORE, 3, 4);
        ore(Blocks.REDSTONE_ORE, 4, 5);
        ore(Blocks.LAPIS_ORE, 3, 4);
        ore(Blocks.EMERALD_ORE, 4, 5);

        add(Blocks.ICE, WordData.CHAIN, 2, ranks(BlockCategory.SOLID, 3, BlockCategory.FLUID, 3, BlockCategory.SEA, 2,
                BlockCategory.STRUCTURE, 3, BlockCategory.CRYSTAL, 3));
        add(Blocks.PACKED_ICE, WordData.CHAIN, 2, ranks(BlockCategory.SOLID, 3, BlockCategory.FLUID, 3, BlockCategory.TERRAIN, 3,
                BlockCategory.SEA, 3, BlockCategory.STRUCTURE, 3, BlockCategory.CRYSTAL, 3));
        add(Blocks.GLASS, WordData.CHAIN, 2, ranks(BlockCategory.SOLID, 3, BlockCategory.STRUCTURE, 3, BlockCategory.CRYSTAL, 3));
        add(Blocks.SNOW_BLOCK, WordData.CHAIN, 2, ranks(BlockCategory.SOLID, 3, BlockCategory.STRUCTURE, 3, BlockCategory.CRYSTAL, 3));
        add(Blocks.OBSIDIAN, WordData.CHAIN, 3, ranks(BlockCategory.SOLID, 4, BlockCategory.TERRAIN, 4, BlockCategory.STRUCTURE, 3,
                BlockCategory.CRYSTAL, 3));
        add(Blocks.GLOWSTONE, WordData.CHAIN, 3, ranks(BlockCategory.SOLID, 4, BlockCategory.STRUCTURE, 4, BlockCategory.CRYSTAL, 4));
        add(Blocks.NETHER_QUARTZ_ORE, WordData.CHAIN, 3, ranks(BlockCategory.SOLID, 4, BlockCategory.STRUCTURE, 4, BlockCategory.CRYSTAL, 4));
        add(ModBlocks.CRYSTAL.get(), WordData.CHAIN, 3, ranks(BlockCategory.SOLID, 4, BlockCategory.STRUCTURE, 4, BlockCategory.CRYSTAL, 4));

        add(Blocks.WATER, WordData.SEA, 2, ranks(BlockCategory.FLUID, 1, BlockCategory.SEA, 1));
        add(Blocks.LAVA, WordData.SEA, 3, ranks(BlockCategory.FLUID, 2, BlockCategory.SEA, 2));
    }

    private static void terrain(Block block, int card, int terrain, int structure, int solid) {
        add(block, WordData.TERRAIN, card, ranks(BlockCategory.TERRAIN, terrain, BlockCategory.STRUCTURE, structure, BlockCategory.SOLID, solid));
    }

    private static void structureStone(Block block) {
        add(block, WordData.STRUCTURE, 2, ranks(BlockCategory.TERRAIN, 5, BlockCategory.STRUCTURE, 1, BlockCategory.SOLID, 1));
    }

    private static void ore(Block block, int card, int rank) {
        add(block, WordData.ORE, card, ranks(BlockCategory.SOLID, rank, BlockCategory.STRUCTURE, rank));
    }

    private static void add(Block block, String word, int card, Map<BlockCategory, Integer> categoryRanks) {
        register(createBlockSymbol(block.defaultBlockState(), word, card, categoryRanks));
    }

    /** Builds an ordered category->rank map from {@code (BlockCategory, Integer)} pairs. */
    public static Map<BlockCategory, Integer> ranks(Object... pairs) {
        Map<BlockCategory, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((BlockCategory) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }
}

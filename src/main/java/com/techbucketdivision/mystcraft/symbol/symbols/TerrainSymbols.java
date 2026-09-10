package com.techbucketdivision.mystcraft.symbol.symbols;

import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.api.symbol.AgeDirector;
import com.techbucketdivision.mystcraft.api.symbol.BlockCategory;
import com.techbucketdivision.mystcraft.api.symbol.ModifierUtils;
import com.techbucketdivision.mystcraft.world.gen.TerrainEndGen;
import com.techbucketdivision.mystcraft.world.gen.TerrainFlatGen;
import com.techbucketdivision.mystcraft.world.gen.TerrainNetherGen;
import com.techbucketdivision.mystcraft.world.gen.TerrainNormalGen;
import com.techbucketdivision.mystcraft.world.gen.TerrainVoidGen;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.techbucketdivision.mystcraft.api.symbol.WordData.*;

/**
 * Terrain generator symbols (REQUIREMENTS §4.3.6). All pop a SEA block then a TERRAIN block from the block list
 * (defaults water / stone; the island world defaults the sea to air).
 */
public final class TerrainSymbols {
    private TerrainSymbols() {}

    /** Terrain/sea pair popped from the director. */
    record Blocks2(BlockState terrain, BlockState sea) {}

    static Blocks2 popBlocks(AgeDirector director, BlockState defaultSea) {
        BlockState sea = ModifierUtils.popBlockState(director, defaultSea, BlockCategory.SEA);
        BlockState terrain = ModifierUtils.popBlockState(director, Blocks.STONE.defaultBlockState(), BlockCategory.TERRAIN);
        if (director instanceof AgeController controller) controller.setTerrainBlocks(terrain, sea);
        return new Blocks2(terrain, sea);
    }

    /** Standard World / Amplified Normal World. */
    public static final class Normal extends SimpleSymbol {
        private final boolean amplified;

        public Normal(boolean amplified) {
            super(amplified ? "terrain_amplified" : "terrain_normal", amplified ? 3 : 2, TERRAIN, FORM, TRADITION, amplified ? SPUR : FLOW);
            this.amplified = amplified;
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Blocks2 b = popBlocks(director, Blocks.WATER.defaultBlockState());
            if (director.isProfiling()) {
                director.registerInterface(Markers.TERRAIN_GENERATOR);
                return;
            }
            director.registerInterface(new TerrainNormalGen(director.getSeed(), amplified, b.terrain(), b.sea()));
        }
    }

    /** Flat World: fill to the average ground level, sea to sea level. */
    public static final class Flat extends SimpleSymbol {
        public Flat() { super("terrain_flat", 3, TERRAIN, FORM, INHIBIT, MOTION); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Blocks2 b = popBlocks(director, Blocks.WATER.defaultBlockState());
            if (director.isProfiling()) {
                director.registerInterface(Markers.TERRAIN_GENERATOR);
                return;
            }
            director.registerInterface(new TerrainFlatGen(director.getSeed(), b.terrain(), b.sea()));
        }
    }

    /** Cave World: nether-style noise; cloud height 200, horizon 128, sea level 32. */
    public static final class Nether extends SimpleSymbol {
        public Nether() { super("terrain_nether", 4, TERRAIN, FORM, CONSTRAINT, ENTROPY); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Blocks2 b = popBlocks(director, Blocks.WATER.defaultBlockState());
            if (director.isProfiling()) {
                director.registerInterface(Markers.TERRAIN_GENERATOR);
            } else {
                director.registerInterface(new TerrainNetherGen(director.getSeed(), b.terrain(), b.sea()));
            }
            director.setCloudHeight(200);
            director.setHorizon(128);
            director.setSeaLevel(32);
        }
    }

    /** Island World: end-style noise; sea defaults to air; horizon 0, sea level 49, no horizon/void. */
    public static final class End extends SimpleSymbol {
        public End() { super("terrain_end", 4, TERRAIN, FORM, ETHEREAL, FLOW); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Blocks2 b = popBlocks(director, Blocks.AIR.defaultBlockState());
            if (director.isProfiling()) {
                director.registerInterface(Markers.TERRAIN_GENERATOR);
            } else {
                director.registerInterface(new TerrainEndGen(director.getSeed(), b.terrain(), b.sea()));
            }
            director.setHorizon(0);
            director.setSeaLevel(49);
            director.setDrawHorizon(false);
            director.setDrawVoid(false);
        }
    }

    /** Void World: nothing generated; cloud height 0, horizon 0, no horizon/void. */
    public static final class Void extends SimpleSymbol {
        public Void() { super("terrain_void", 4, TERRAIN, FORM, INFINITE, VOID); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new TerrainVoidGen());
            director.setCloudHeight(0);
            director.setHorizon(0);
            director.setDrawHorizon(false);
            director.setDrawVoid(false);
        }
    }
}

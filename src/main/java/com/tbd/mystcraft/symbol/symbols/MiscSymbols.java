package com.tbd.mystcraft.symbol.symbols;

import com.tbd.mystcraft.api.symbol.AgeDirector;
import com.tbd.mystcraft.api.symbol.BlockCategory;
import com.tbd.mystcraft.api.symbol.BlockDescriptor;
import net.minecraft.world.level.block.Blocks;

import static com.tbd.mystcraft.api.symbol.WordData.*;

/** Misc symbols (original spec §4.3.11). */
public final class MiscSymbols {
    private MiscSymbols() {}

    /** Anti-PvP: never generated or traded. */
    public static final class PvPOff extends SimpleSymbol {
        public PvPOff() { super("pvp_off", null, CHAIN, CHAOS, ENCOURAGE, HARMONY); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.setPvPEnabled(false);
        }
    }

    /** No Seas: pushes an air block usable as the SEA block. */
    public static final class NoSea extends SimpleSymbol {
        public NoSea() { super("no_sea", 2, MODIFIER, CONSTRAINT, FLOW, INHIBIT); }

        @Override
        public java.util.Set<BlockCategory> blockCategories() {
            return java.util.Set.of(BlockCategory.SEA);
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.pushBlock(new BlockDescriptor(Blocks.AIR.defaultBlockState(), BlockCategory.SEA));
        }
    }

}

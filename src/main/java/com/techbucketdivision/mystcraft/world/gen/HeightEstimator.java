package com.techbucketdivision.mystcraft.world.gen;

import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Optional capability of a terrain generator: cheaply sample the raw terrain column at a block position without
 * generating the chunk. Used by {@code AgeChunkGenerator#getBaseHeight/getBaseColumn} (structure placement).
 */
public interface HeightEstimator {
    /**
     * Raw terrain column for y = {@code ctx.minY()} .. {@code ctx.maxY()} (index 0 = minY), before alterations,
     * surface replacement and decoration. Must not be mutated by the caller.
     */
    BlockState[] sampleColumn(TerrainContext ctx, int blockX, int blockZ);

    /**
     * First free y above the highest block of the raw column, optionally ignoring fluids (ocean-floor semantics).
     * Returns {@code ctx.minY()} for an empty column.
     */
    default int estimateSurface(TerrainContext ctx, int blockX, int blockZ, boolean ignoreFluids) {
        BlockState[] column = sampleColumn(ctx, blockX, blockZ);
        for (int i = column.length - 1; i >= 0; i--) {
            BlockState s = column[i];
            if (s.isAir()) continue;
            if (ignoreFluids && s.liquid()) continue;
            return ctx.minY() + i + 1;
        }
        return ctx.minY();
    }
}

package com.techbucketdivision.mystcraft.instability.decay;

import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.block.DecayType;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Behaviour of one decay variant (original spec §3.8). {@code block.DecayBlock} delegates its random tick, placement
 * and entity-contact hooks here via {@link DecayHandlers#get(DecayType)}.
 */
public abstract class DecayHandler {
    private final DecayType type;

    protected DecayHandler(DecayType type) {
        this.type = type;
    }

    public final DecayType type() {
        return type;
    }

    /** The decay block state this handler manages. */
    public BlockState state() {
        return ModBlocks.decay(type).get().defaultBlockState();
    }

    protected boolean isDecay(BlockState state) {
        return state.getBlock() == ModBlocks.decay(type).get();
    }

    protected static boolean isAnyDecay(BlockState state) {
        for (DecayType t : DecayType.values()) {
            if (state.getBlock() == ModBlocks.decay(t).get()) return true;
        }
        return false;
    }

    /** Random tick (from vanilla random ticks or {@code ExtraTicksEffect}). */
    public void randomTick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        pulse(level, pos, state, random);
    }

    protected abstract void pulse(ServerLevel level, BlockPos pos, BlockState state, RandomSource random);

    /** Placement hook: decay outside a Mystcraft Age is removed. Returns {@code true} if the block was removed. */
    public boolean onPlace(ServerLevel level, BlockPos pos, BlockState state) {
        if (!AgeData.isAgeLevel(level.dimension())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    /** Called when an entity touches the block. */
    public void onEntityContact(Level level, BlockPos pos, BlockState state, Entity entity) {}

    /** Hardness reported by the block (kept for API parity; the block properties already carry the values). */
    public float hardness() {
        return type.hardness();
    }

    public float explosionResistance() {
        return type.explosionResistance();
    }
}

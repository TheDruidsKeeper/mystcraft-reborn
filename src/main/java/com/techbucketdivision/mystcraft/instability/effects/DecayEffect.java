package com.techbucketdivision.mystcraft.instability.effects;

import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.api.symbol.logic.EnvironmentalEffect;
import com.techbucketdivision.mystcraft.block.DecayType;
import com.techbucketdivision.mystcraft.instability.InstabilityController;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Places decay blocks with probability {@code score / 1,000,000} per chunk tick (REQUIREMENTS §6.5 EffectDecayBasic).
 * {@code maxY == null} means the surface height (or the Age's average ground level when that is not above minY).
 */
public final class DecayEffect implements EnvironmentalEffect {
    private static final int MAX_SCORE = 1_000_000;

    private final DecayType type;
    private final int minY;
    private final @Nullable Integer maxY;
    private final ChunkLcg lcg = new ChunkLcg();
    private Predicate<BlockState> banned = state -> false;

    public DecayEffect(DecayType type, int minY, @Nullable Integer maxY) {
        this.type = type;
        this.minY = minY;
        this.maxY = maxY;
    }

    /** Skips downward over states matching the predicate (black decay bans air and fluids). */
    public DecayEffect ban(Predicate<BlockState> predicate) {
        banned = banned.or(predicate);
        return this;
    }

    public DecayEffect banAirAndFluids() {
        return ban(state -> state.isAir() || !state.getFluidState().isEmpty());
    }

    @Override
    public void tick(ServerLevel level, LevelChunk chunk) {
        InstabilityController controller = InstabilityController.get(level);
        int score = controller == null ? 0 : controller.getInstabilityScore();
        if (level.getRandom().nextInt(MAX_SCORE) >= score) return;
        int coords = lcg.next();
        int x = chunk.getPos().getMinBlockX() + ChunkLcg.localX(coords);
        int z = chunk.getPos().getMinBlockZ() + ChunkLcg.localZ(coords);
        int y = ChunkLcg.y255(coords);
        place(level, controller == null ? null : controller.ageController(), x, y, z);
    }

    private void place(ServerLevel level, @Nullable AgeController age, int x, int yRoll, int z) {
        int max;
        if (maxY == null) {
            max = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (max <= minY) max = age == null ? 64 : age.averageGroundLevel();
        } else {
            max = maxY;
        }
        if (max < minY) return;
        int y = max == minY ? minY : (yRoll % (max - minY)) + minY;
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        while (banned.test(state)) {
            pos = pos.below();
            if (pos.getY() < minY || level.isOutsideBuildHeight(pos)) return;
            state = level.getBlockState(pos);
        }
        level.setBlock(pos, ModBlocks.decay(type).get().defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}

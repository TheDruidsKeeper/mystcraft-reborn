package com.techbucketdivision.mystcraft.instability.effects;

import com.techbucketdivision.mystcraft.api.symbol.logic.EnvironmentalEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;

/**
 * Three extra random block ticks per randomly-ticking 16³ section per chunk tick (REQUIREMENTS §6.5
 * EffectExtraTicks), optionally restricted to a single block state (used by the decay providers).
 */
public final class ExtraTicksEffect implements EnvironmentalEffect {
    private final @Nullable BlockState onlyState;
    private final ChunkLcg lcg = new ChunkLcg();

    public ExtraTicksEffect(@Nullable BlockState onlyState) {
        this.onlyState = onlyState;
    }

    public ExtraTicksEffect() {
        this(null);
    }

    @Override
    public void tick(ServerLevel level, LevelChunk chunk) {
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir()) continue;
            if (onlyState == null && !section.isRandomlyTicking()) continue;
            if (onlyState != null && !section.maybeHas(s -> s == onlyState)) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int n = 0; n < 3; n++) {
                int bits = lcg.next();
                int x = ChunkLcg.localX(bits);
                int z = ChunkLcg.localZ(bits);
                int y = ChunkLcg.localY(bits);
                BlockState state = section.getBlockState(x, y, z);
                if (onlyState != null) {
                    // Decay blocks are not randomly ticking by themselves; the restricted form ticks them anyway.
                    if (onlyState != state) continue;
                } else if (!state.isRandomlyTicking()) {
                    continue;
                }
                pos.set(baseX + x, baseY + y, baseZ + z);
                state.randomTick(level, pos.immutable(), level.getRandom());
            }
        }
    }
}

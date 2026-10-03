package com.techbucketdivision.mystcraft.client.render.tint;

import com.techbucketdivision.mystcraft.api.symbol.logic.ColorKind;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Set;

/**
 * Wraps a vanilla biome-driven tint source (grass / foliage / water) so that, inside an Age with a static colour
 * symbol for that {@link ColorKind}, the Age colour replaces the biome colour. Outside Ages, and for the inventory
 * colour, the vanilla source is used unchanged.
 */
public final class AgeBiomeTintSource implements BlockTintSource {
    private final ColorKind kind;
    private final BlockTintSource vanilla;

    public AgeBiomeTintSource(ColorKind kind, BlockTintSource vanilla) {
        this.kind = kind;
        this.vanilla = vanilla;
    }

    @Override
    public int color(BlockState state) {
        return vanilla.color(state);
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        int override = AgeBiomeTints.get(kind);
        return override != 0 ? override : vanilla.colorInWorld(state, level, pos);
    }

    @Override
    public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        int override = AgeBiomeTints.get(kind);
        return override != 0 ? override : vanilla.colorAsTerrainParticle(state, level, pos);
    }

    @Override
    public Set<Property<?>> relevantProperties() {
        return vanilla.relevantProperties();
    }
}

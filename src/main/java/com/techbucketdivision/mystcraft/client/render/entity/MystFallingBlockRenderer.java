package com.techbucketdivision.mystcraft.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.techbucketdivision.mystcraft.entity.MystFallingBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Falling block (instability crumble / meteors). Mirrors vanilla {@code FallingBlockRenderer}: the real block-state
 * model is submitted through {@link SubmitNodeCollector#submitMovingBlock} with a {@link MovingBlockRenderState}
 * carrying biome (grass/foliage/water tints), cardinal lighting and the light engine of the client level.
 */
public class MystFallingBlockRenderer extends EntityRenderer<MystFallingBlockEntity, MystFallingBlockRenderer.State> {

    public static class State extends EntityRenderState {
        public final MovingBlockRenderState movingBlock = new MovingBlockRenderState();
    }

    public MystFallingBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MystFallingBlockEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
        state.movingBlock.randomSeedPos = entity.blockPosition();
        state.movingBlock.blockPos = pos;
        state.movingBlock.blockState = entity.getBlockState();
        if (entity.level() instanceof ClientLevel clientLevel) {
            state.movingBlock.biome = clientLevel.getBiome(pos);
            state.movingBlock.cardinalLighting = clientLevel.cardinalLighting();
            state.movingBlock.lightEngine = clientLevel.getLightEngine();
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockState blockState = state.movingBlock.blockState;
        if (blockState.getRenderShape() != RenderShape.MODEL) return;
        poseStack.pushPose();
        poseStack.translate(-0.5, 0.0, -0.5);
        collector.submitMovingBlock(poseStack, state.movingBlock);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}

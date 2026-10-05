package com.techbucketdivision.mystcraft.block;

import com.techbucketdivision.mystcraft.blockentity.FacilityLockBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A Facility lock block (Symbol Altar, Offering Pedestal); the Sequence Dial is {@link SequenceDialBlock}. The
 * puzzle logic lives in {@link FacilityLockBlockEntity}; the block only routes clicks and gives the kind.
 */
public class FacilityLockBlock extends Block implements EntityBlock {
    private static final VoxelShape PEDESTAL = Block.box(2, 0, 2, 14, 14, 14);

    private final FacilityLockBlockEntity.Kind kind;
    private final boolean pedestal;

    public FacilityLockBlock(FacilityLockBlockEntity.Kind kind, boolean pedestal, Properties properties) {
        super(properties);
        this.kind = kind;
        this.pedestal = pedestal;
    }

    public FacilityLockBlockEntity.Kind kind() {
        return kind;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        FacilityLockBlockEntity be = new FacilityLockBlockEntity(pos, state);
        be.configure(kind, pos.asLong(), new net.minecraft.world.level.levelgen.structure.BoundingBox(pos));
        return be;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return pedestal ? PEDESTAL : super.getShape(state, level, pos, context);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(level, pos, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player, InteractionHand.MAIN_HAND);
    }

    private static InteractionResult interact(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof FacilityLockBlockEntity be)) return InteractionResult.PASS;
        return be.interact(sp, hand);
    }
}

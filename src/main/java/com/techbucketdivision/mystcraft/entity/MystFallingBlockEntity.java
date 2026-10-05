package com.tbd.mystcraft.entity;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Falling block used by black decay (original spec §9): carries a block state, its would-be drops and block-entity
 * data; gravity 0.04, drag 0.98; places itself on landing (restoring the block entity) or drops the items if placement
 * fails; removed below y = minY - 10. On its first tick it cascades adjacent leaves.
 */
public final class MystFallingBlockEntity extends Entity {
    private static final EntityDataAccessor<BlockState> DATA_STATE = SynchedEntityData.defineId(MystFallingBlockEntity.class, EntityDataSerializers.BLOCK_STATE);

    private int fallTime;
    private List<ItemStack> drops = new ArrayList<>();
    private @Nullable CompoundTag blockEntityData;

    public MystFallingBlockEntity(EntityType<? extends MystFallingBlockEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    // --- factory -------------------------------------------------------------------------------------------------

    /**
     * Turns the block at {@code pos} into a falling block entity. Fluids are simply removed; nothing happens when the
     * position is air or the block below is not air.
     */
    public static void fall(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.isOutsideBuildHeight(pos)) return;
        BlockState current = level.getBlockState(pos);
        if (current.liquid()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return;
        }
        if (current.isAir()) return;
        if (!level.getBlockState(pos.below()).isAir()) return;
        BlockState falling = state.isAir() ? current : state;

        MystFallingBlockEntity entity = ModEntities.FALLING_BLOCK.get().create(level, EntitySpawnReason.TRIGGERED);
        if (entity == null) return;
        BlockEntity be = level.getBlockEntity(pos);
        entity.drops = new ArrayList<>(Block.getDrops(current, level, pos, be));
        if (be != null) {
            entity.blockEntityData = be.saveWithoutMetadata(level.registryAccess());
            level.removeBlockEntity(pos);
        }
        entity.getEntityData().set(DATA_STATE, falling);
        entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.addFreshEntity(entity);
    }

    /** Drops the block at {@code pos} if it is a leaf block (called for the neighbours on the first tick). */
    public static void cascade(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.LEAVES)) fall(level, pos, state);
    }

    // --- data ----------------------------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, Blocks.SAND.defaultBlockState());
    }

    public BlockState getBlockState() {
        return getEntityData().get(DATA_STATE);
    }

    public int fallTime() {
        return fallTime;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        BlockState state = input.read("block", BlockState.CODEC).orElse(Blocks.SAND.defaultBlockState());
        if (state.isAir()) state = Blocks.SAND.defaultBlockState();
        getEntityData().set(DATA_STATE, state);
        fallTime = input.getIntOr("time", 0);
        drops = new ArrayList<>(input.read("drops", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        blockEntityData = input.read("block_entity", CompoundTag.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("block", BlockState.CODEC, getBlockState());
        output.putInt("time", fallTime);
        output.store("drops", ItemStack.OPTIONAL_CODEC.listOf(), drops);
        output.storeNullable("block_entity", CompoundTag.CODEC, blockEntityData);
    }

    // --- behaviour -----------------------------------------------------------------------------------------------

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return !isRemoved();
    }

    @Override
    public void tick() {
        BlockState state = getBlockState();
        if (state.isAir()) {
            discard();
            return;
        }
        fallTime++;
        xo = getX();
        yo = getY();
        zo = getZ();
        setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.98));

        if (!(level() instanceof ServerLevel server)) return;

        if (onGround()) {
            discard();
            place(server, BlockPos.containing(getX(), getY(), getZ()), state);
            return;
        }
        if (getY() < level().getMinY() - 10) {
            discard();
            return;
        }
        if (fallTime == 1) {
            BlockPos origin = blockPosition();
            for (Direction dir : Direction.Plane.HORIZONTAL) cascade(server, origin.relative(dir));
        }
    }

    private void place(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced() || !level.setBlock(pos, state, Block.UPDATE_ALL)) {
            for (ItemStack stack : drops) spawnAtLocation(level, stack);
            return;
        }
        if (blockEntityData != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                try {
                    // TagValueInput.create(ProblemReporter, HolderLookup.Provider, CompoundTag) (26.1 sources)
                    be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), blockEntityData));
                    be.setChanged();
                } catch (Exception e) {
                    Mystcraft.LOGGER.warn("Failed to restore block entity data of fallen {} at {}", state, pos, e);
                }
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}

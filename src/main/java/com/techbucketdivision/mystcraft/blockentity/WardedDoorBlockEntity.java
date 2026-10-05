package com.tbd.mystcraft.blockentity;

import com.tbd.mystcraft.facility.FacilityState;
import com.tbd.mystcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Warded Door block: sealed until the lock it shares an id with is solved, then gone for good (doors stay open,
 * FACILITY_PLAN.md §8.5). Locks open their doors directly when solved; the tick is the fallback for a door whose chunk
 * was unloaded at that moment.
 */
public class WardedDoorBlockEntity extends MystBlockEntity {
    private static final int CHECK_INTERVAL = 40;

    private long lockId;
    private int cooldown;

    public WardedDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WARDED_DOOR.get(), pos, state);
    }

    public long lockId() {
        return lockId;
    }

    public void setLockId(long lockId) {
        this.lockId = lockId;
        setChanged();
    }

    public void serverTick() {
        if (--cooldown > 0) return;
        cooldown = CHECK_INTERVAL;
        if (getLevel() instanceof ServerLevel level && FacilityState.isLockSolved(level, lockId)) open();
    }

    /** Removes the door with a sound (server only). */
    public void open() {
        if (!(getLevel() instanceof ServerLevel level)) return;
        level.playSound(null, getBlockPos(), SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1f, 0.6f);
        level.setBlock(getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("LockId", lockId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lockId = input.getLongOr("LockId", 0L);
    }
}

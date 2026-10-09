package com.tbd.mystcraft.api.symbol;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/** A block pushed onto the director's block list together with the categories it may be used for. */
public record BlockDescriptor(BlockState state, Set<BlockCategory> usable) {

    public BlockDescriptor(BlockState state, BlockCategory... usable) {
        this(state, Set.of(usable));
    }

    public boolean isUsable(BlockCategory category) {
        return usable.contains(BlockCategory.ANY) || usable.contains(category);
    }

    public boolean isUsableForAny(BlockCategory... categories) {
        for (BlockCategory c : categories) {
            if (isUsable(c)) return true;
        }
        return false;
    }
}

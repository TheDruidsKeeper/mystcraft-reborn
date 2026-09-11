package com.techbucketdivision.mystcraft.block;

import com.techbucketdivision.mystcraft.registry.ModFluids;
import net.minecraft.world.level.block.LiquidBlock;

/**
 * Black Ink fluid block (REQUIREMENTS §3.12). Bucket / bottle pickup is cancelled by {@code event.CommonEvents}.
 */
public class InkFluidBlock extends LiquidBlock {
    public InkFluidBlock(Properties properties) {
        super(ModFluids.BLACK_INK.get(), properties);
    }
}

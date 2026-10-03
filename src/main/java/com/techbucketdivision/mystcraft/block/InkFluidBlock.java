package com.techbucketdivision.mystcraft.block;

import com.techbucketdivision.mystcraft.registry.ModFluids;
import net.minecraft.world.level.block.LiquidBlock;

/**
 * Black Ink fluid block (REQUIREMENTS §3.12). Source blocks are collectable: buckets via vanilla {@code BucketPickup}
 * (the fluid's bucket is the Black Ink Bucket), glass bottles via {@code CommonEvents.scoopIntoVial} (-> Ink Vial).
 */
public class InkFluidBlock extends LiquidBlock {
    public InkFluidBlock(Properties properties) {
        super(ModFluids.BLACK_INK.get(), properties);
    }
}

package com.tbd.mystcraft.registry;

import com.tbd.mystcraft.Mystcraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Black ink fluid (original spec §3.12). */
public final class ModFluids {
    private ModFluids() {}

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, Mystcraft.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(net.minecraft.core.registries.Registries.FLUID, Mystcraft.MOD_ID);

    public static final Supplier<FluidType> BLACK_INK_TYPE = FLUID_TYPES.register("black_ink", () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid_type.mystcraft.black_ink")
            .density(1100).viscosity(1200).temperature(300)
            .canSwim(true).canDrown(true).canExtinguish(true).canConvertToSource(false).supportsBoating(false)
            // Without this NeoForge applies NO movement logic to a non-vanilla fluid (LivingEntity.travelInFluid only
            // handles water/lava unless the type is water-like or overrides move()), so entities got stuck in ink.
            .isWaterLike(true)
            .rarity(Rarity.COMMON)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> BLACK_INK = FLUIDS.register("black_ink",
            () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_BLACK_INK = FLUIDS.register("flowing_black_ink",
            () -> new BaseFlowingFluid.Flowing(properties()));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(BLACK_INK_TYPE, BLACK_INK, FLOWING_BLACK_INK)
                .bucket(ModItems.BLACK_INK_BUCKET)
                .block(ModBlocks.BLACK_INK)
                .slopeFindDistance(4).levelDecreasePerBlock(1).explosionResistance(100f).tickRate(5);
    }

    public static boolean isInk(Fluid fluid) {
        return fluid == BLACK_INK.get() || fluid == FLOWING_BLACK_INK.get();
    }
}

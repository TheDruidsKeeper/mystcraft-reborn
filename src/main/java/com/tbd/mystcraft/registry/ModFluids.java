package com.tbd.mystcraft.registry;

import com.tbd.mystcraft.Mystcraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Black ink: a tank-only fluid. It exists so the Writing Desk inkwell is a {@code FluidStack} and the Ink Vial a
 * fluid container; it has no bucket, no block and never appears in the world (the vial is the only carrier).
 */
public final class ModFluids {
    private ModFluids() {}

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, Mystcraft.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(net.minecraft.core.registries.Registries.FLUID, Mystcraft.MOD_ID);

    public static final Supplier<FluidType> BLACK_INK_TYPE = FLUID_TYPES.register("black_ink", () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid_type.mystcraft.black_ink").density(1100).viscosity(1200).temperature(300)));

    public static final DeferredHolder<Fluid, InkFluid> BLACK_INK = FLUIDS.register("black_ink", InkFluid::new);

    public static boolean isInk(Fluid fluid) {
        return fluid == BLACK_INK.get();
    }

    /** A source-only fluid with no world form: every placement query answers "nothing here". */
    public static final class InkFluid extends Fluid {
        @Override
        public FluidType getFluidType() {
            return BLACK_INK_TYPE.get();
        }

        @Override
        public Item getBucket() {
            return Items.AIR;
        }

        @Override
        protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) {
            return true;
        }

        @Override
        protected Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState fluidState) {
            return Vec3.ZERO;
        }

        @Override
        public int getTickDelay(LevelReader level) {
            return 0;
        }

        @Override
        protected float getExplosionResistance() {
            return 0f;
        }

        @Override
        public float getHeight(FluidState fluidState, BlockGetter level, BlockPos pos) {
            return 0f;
        }

        @Override
        public float getOwnHeight(FluidState fluidState) {
            return 0f;
        }

        @Override
        protected BlockState createLegacyBlock(FluidState fluidState) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public boolean isSource(FluidState fluidState) {
            return true;
        }

        @Override
        public int getAmount(FluidState fluidState) {
            return 0;
        }

        @Override
        public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
            return Shapes.empty();
        }
    }
}

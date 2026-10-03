package com.techbucketdivision.mystcraft.gametest;

import com.techbucketdivision.mystcraft.block.WritingDeskBlock;
import com.techbucketdivision.mystcraft.blockentity.InkMixerBlockEntity;
import com.techbucketdivision.mystcraft.blockentity.WritingDeskBlockEntity;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

/** Writing desk and ink mixer: ink containers drain into the tank / basin and leave their empty form in the output slot. */
@ForEachTest(groups = "workstations")
public class WorkstationTests {

    @GameTest(timeoutTicks = 100)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "A bucket of black ink in the desk's container slot fills the inkwell and leaves an empty bucket")
    static void deskAcceptsInkBucket(ExtendedGameTestHelper helper) {
        WritingDeskBlockEntity desk = placeDesk(helper);
        desk.main.setStack(WritingDeskBlockEntity.SLOT_CONTAINER_IN, new ItemStack(ModItems.BLACK_INK_BUCKET.get()));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(desk.getInkAmount(), FluidType.BUCKET_VOLUME, "inkwell filled from bucket"))
                .thenExecute(() -> helper.assertTrue(desk.main.getStack(WritingDeskBlockEntity.SLOT_CONTAINER_OUT).is(Items.BUCKET), "empty bucket in output slot"))
                .thenSucceed();
    }

    @GameTest(timeoutTicks = 100)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "An ink vial in the desk's container slot fills the inkwell and leaves a glass bottle")
    static void deskAcceptsInkVial(ExtendedGameTestHelper helper) {
        WritingDeskBlockEntity desk = placeDesk(helper);
        desk.main.setStack(WritingDeskBlockEntity.SLOT_CONTAINER_IN, new ItemStack(ModItems.INK_VIAL.get()));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(desk.getInkAmount(), FluidType.BUCKET_VOLUME, "inkwell filled from vial"))
                .thenExecute(() -> helper.assertTrue(desk.main.getStack(WritingDeskBlockEntity.SLOT_CONTAINER_OUT).is(Items.GLASS_BOTTLE), "glass bottle in output slot"))
                .thenSucceed();
    }

    @GameTest(timeoutTicks = 100)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "A bucket of black ink in the ink mixer fills the basin")
    static void mixerAcceptsInkBucket(ExtendedGameTestHelper helper) {
        helper.setBlock(1, 1, 1, ModBlocks.INK_MIXER.get());
        InkMixerBlockEntity mixer = helper.getBlockEntity(1, 1, 1, InkMixerBlockEntity.class);
        mixer.inventory.setStack(InkMixerBlockEntity.SLOT_INK_IN, new ItemStack(ModItems.BLACK_INK_BUCKET.get()));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(mixer.hasInk(), "basin has ink"))
                .thenExecute(() -> helper.assertTrue(mixer.inventory.getStack(InkMixerBlockEntity.SLOT_INK_OUT).is(Items.BUCKET), "empty bucket in output slot"))
                .thenSucceed();
    }

    @GameTest(timeoutTicks = 100)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "An ink vial in the ink mixer fills the basin")
    static void mixerAcceptsInkVial(ExtendedGameTestHelper helper) {
        helper.setBlock(1, 1, 1, ModBlocks.INK_MIXER.get());
        InkMixerBlockEntity mixer = helper.getBlockEntity(1, 1, 1, InkMixerBlockEntity.class);
        mixer.inventory.setStack(InkMixerBlockEntity.SLOT_INK_IN, new ItemStack(ModItems.INK_VIAL.get()));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(mixer.hasInk(), "basin has ink"))
                .thenSucceed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Diagnostics: ink containers expose a fluid handler and report their contents")
    static void inkContainersReportContents(ExtendedGameTestHelper helper) {
        for (ItemStack stack : new ItemStack[] {new ItemStack(ModItems.BLACK_INK_BUCKET.get()), new ItemStack(ModItems.INK_VIAL.get())}) {
            var access = net.neoforged.neoforge.transfer.access.ItemAccess.forStack(stack.copy());
            var handler = access.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM);
            var contained = net.neoforged.neoforge.transfer.fluid.FluidUtil.getFirstStackContained(stack);
            com.techbucketdivision.mystcraft.Mystcraft.LOGGER.info("[gametest] {}: handler={} contained={} x{} isInkContainer={}",
                    stack.getItem(), handler == null ? "null" : handler.getClass().getName(), contained.getFluid(), contained.getAmount(),
                    com.techbucketdivision.mystcraft.blockentity.BookUtil.isInkContainer(stack));
            helper.assertNotNull(handler, stack.getItem() + " has a fluid handler");
            helper.assertTrue(!contained.isEmpty(), stack.getItem() + " reports contained fluid");
        }
        helper.succeed();
    }

    private static WritingDeskBlockEntity placeDesk(ExtendedGameTestHelper helper) {
        var head = ModBlocks.WRITING_DESK.get().defaultBlockState().setValue(WritingDeskBlock.FACING, Direction.EAST);
        helper.setBlock(0, 1, 1, head);
        helper.setBlock(1, 1, 1, head.setValue(WritingDeskBlock.FOOT, true));
        return helper.getBlockEntity(0, 1, 1, WritingDeskBlockEntity.class);
    }
}

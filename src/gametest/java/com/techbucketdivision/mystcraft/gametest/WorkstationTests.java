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

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Every ingredient the mixer advertises is accepted as an ink modifier; the vanilla stand-ins (gold/iron nugget) are among them")
    static void inkIngredientCatalogueMatchesLookup(ExtendedGameTestHelper helper) {
        var catalogue = com.techbucketdivision.mystcraft.linking.InkEffects.getIngredients();
        helper.assertTrue(!catalogue.isEmpty(), "ingredient catalogue is not empty");
        int available = 0;
        boolean goldNugget = false;
        for (var ingredient : catalogue) {
            if (!ingredient.available()) {
                com.techbucketdivision.mystcraft.Mystcraft.LOGGER.info("[gametest] ink ingredient tag {} has no items in this game", ingredient.tag());
                continue;
            }
            available++;
            var lookedUp = com.techbucketdivision.mystcraft.linking.InkEffects.getItemEffects(ingredient.example());
            com.techbucketdivision.mystcraft.Mystcraft.LOGGER.info("[gametest] ink ingredient {} -> {}", ingredient.example().getItem(), lookedUp);
            helper.assertTrue(!lookedUp.isEmpty(), ingredient.example().getItem() + " is accepted by the mixer");
            goldNugget |= ingredient.example().is(Items.GOLD_NUGGET);
        }
        helper.assertTrue(available >= 10, "at least the vanilla ingredients are available (" + available + ")");
        helper.assertTrue(goldNugget, "gold nugget is an ink ingredient");
        helper.assertTrue(com.techbucketdivision.mystcraft.linking.InkEffects.getItemEffects(new ItemStack(Items.STICK)).isEmpty(), "a stick is not an ingredient");
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Ink pools are collectable: an empty bucket picks up a Black Ink Bucket, a glass bottle scoops an Ink Vial, a flowing block yields nothing")
    static void inkCanBeScooped(ExtendedGameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var level = helper.getLevel();

        // bucket: vanilla BucketPickup on the fluid block
        helper.setBlock(0, 1, 0, ModBlocks.BLACK_INK.get());
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(0, 1, 0));
        ItemStack picked = ModBlocks.BLACK_INK.get().pickupBlock(player, level, pos, level.getBlockState(pos));
        helper.assertTrue(picked.is(ModItems.BLACK_INK_BUCKET.get()), "bucket pickup gives a Black Ink Bucket (got " + picked + ")");
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, 0, 1, 0);

        // bottle: the mod's scoop turns a glass bottle into an Ink Vial and removes the source block
        helper.setBlock(2, 1, 2, ModBlocks.BLACK_INK.get());
        var pos2 = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        helper.assertTrue(com.techbucketdivision.mystcraft.event.CommonEvents.scoopIntoVial(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, pos2), "bottle scoops ink");
        helper.assertTrue(player.getMainHandItem().is(ModItems.INK_VIAL.get()), "bottle became an Ink Vial (got " + player.getMainHandItem() + ")");
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, 2, 1, 2);

        // flowing ink is not a whole block's worth: neither container collects it
        helper.setBlock(1, 1, 1, ModBlocks.BLACK_INK.get().defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 3));
        var pos3 = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        helper.assertTrue(ModBlocks.BLACK_INK.get().pickupBlock(player, level, pos3, level.getBlockState(pos3)).isEmpty(), "flowing ink gives no bucket");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        helper.assertFalse(com.techbucketdivision.mystcraft.event.CommonEvents.scoopIntoVial(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, pos3), "flowing ink gives no vial");
        com.techbucketdivision.mystcraft.Mystcraft.LOGGER.info("[gametest] ink scooping: bucket={} vial={}", picked.getItem(), ModItems.INK_VIAL.get());
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Writing desk: the menu reports when a click can write, and writing a symbol fills the book's blank page and uses ink")
    static void deskWritesSymbolIntoBook(ExtendedGameTestHelper helper) {
        WritingDeskBlockEntity desk = placeDesk(helper);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var menu = new com.techbucketdivision.mystcraft.menu.WritingDeskMenu(1, player.getInventory(), desk);
        var symbol = com.techbucketdivision.mystcraft.symbol.SymbolRegistry.all().iterator().next();

        // nothing in the desk: a click must take the page, not write
        helper.assertFalse(menu.canWriteSymbol(), "empty desk cannot write");

        // an unvisited descriptive book with a blank page + ink -> writable
        ItemStack book = new ItemStack(ModItems.DESCRIPTIVE_BOOK.get());
        com.techbucketdivision.mystcraft.item.LinkingItem.setLinkInfo(book,
                com.techbucketdivision.mystcraft.api.linking.LinkInfo.EMPTY.withDisplayName("Desk test"));
        com.techbucketdivision.mystcraft.item.DescriptiveBookItem.setPages(book, java.util.List.of(
                com.techbucketdivision.mystcraft.item.PageItem.createLinkPanel(),
                com.techbucketdivision.mystcraft.item.PageItem.createBlankPage()));
        desk.main.setStack(WritingDeskBlockEntity.SLOT_TARGET, book);
        helper.assertFalse(menu.canWriteSymbol(), "book but no ink cannot write");
        desk.setInk(new net.neoforged.neoforge.fluids.FluidStack(com.techbucketdivision.mystcraft.registry.ModFluids.BLACK_INK.get(), 1000));
        helper.assertTrue(menu.canWriteSymbol(), "book + ink can write");

        int inkBefore = desk.getInkAmount();
        desk.writeSymbol(player, symbol);
        ItemStack written = desk.getTarget();
        var pages = com.techbucketdivision.mystcraft.item.DescriptiveBookItem.getPages(written);
        com.techbucketdivision.mystcraft.Mystcraft.LOGGER.info("[gametest] desk wrote {} -> pages {} ink {}->{}", symbol.id(), pages, inkBefore, desk.getInkAmount());
        helper.assertTrue(pages.size() == 2 && symbol.id().equals(com.techbucketdivision.mystcraft.item.PageItem.getSymbolId(pages.get(1))), "blank page now carries the symbol");
        helper.assertValueEqual(desk.getInkAmount(), inkBefore - WritingDeskBlockEntity.INK_COST, "ink used per symbol");

        // no blank page left and no paper: cannot write again (paper would make a new page instead)
        helper.assertTrue(menu.canWriteSymbol(), "menu still offers writing (book is Writable; the item decides per page)");
        desk.writeSymbol(player, symbol);
        helper.assertValueEqual(desk.getInkAmount(), inkBefore - WritingDeskBlockEntity.INK_COST, "no ink used when the book has no blank page");
        helper.succeed();
    }

    private static WritingDeskBlockEntity placeDesk(ExtendedGameTestHelper helper) {
        var head = ModBlocks.WRITING_DESK.get().defaultBlockState().setValue(WritingDeskBlock.FACING, Direction.EAST);
        helper.setBlock(0, 1, 1, head);
        helper.setBlock(1, 1, 1, head.setValue(WritingDeskBlock.FOOT, true));
        return helper.getBlockEntity(0, 1, 1, WritingDeskBlockEntity.class);
    }
}

package com.techbucketdivision.mystcraft.gametest;

import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.block.WritingDeskBlock;
import com.techbucketdivision.mystcraft.blockentity.InkMixerBlockEntity;
import com.techbucketdivision.mystcraft.blockentity.WritingDeskBlockEntity;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.knowledge.SymbolKnowledge;
import com.techbucketdivision.mystcraft.util.MystIds;
import com.techbucketdivision.mystcraft.linking.InkEffects;
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
    @TestHolder(description = "Ink table: one ingredient per effect and one effect per ingredient; the default table is clay/feather/gunpowder/compass/ender pearl/amethyst/eye of ender + black dye clears")
    static void inkIngredientTableIsOneToOne(ExtendedGameTestHelper helper) {
        var table = InkEffects.getIngredients();
        java.util.Set<net.minecraft.world.item.Item> items = new java.util.HashSet<>();
        java.util.Set<LinkProperty> effects = new java.util.HashSet<>();
        int clearing = 0;
        for (var ingredient : table) {
            com.techbucketdivision.mystcraft.Mystcraft.LOGGER.info("[gametest] ink ingredient {} -> {}", ingredient.item(), ingredient.clears() ? "clear" : ingredient.effect());
            helper.assertTrue(items.add(ingredient.item()), ingredient.item() + " listed once");
            if (ingredient.clears()) clearing++;
            else helper.assertTrue(effects.add(ingredient.effect()), ingredient.effect() + " has one ingredient");
            helper.assertTrue(InkEffects.ingredientFor(ingredient.example()) == ingredient, ingredient.item() + " looks up to itself");
        }
        helper.assertValueEqual(clearing, 1, "exactly one clearing ingredient");
        helper.assertValueEqual(effects, java.util.Set.of(LinkProperty.GENERATE_PLATFORM, LinkProperty.MAINTAIN_MOMENTUM, LinkProperty.DISARM,
                LinkProperty.INTRA_LINKING_ONLY, LinkProperty.INTRA_LINKING, LinkProperty.RELATIVE, LinkProperty.FOLLOWING), "every inkable effect has an ingredient");
        helper.assertTrue(InkEffects.ingredientFor(new ItemStack(Items.GUNPOWDER)).effect() == LinkProperty.DISARM, "gunpowder -> Disarm");
        helper.assertTrue(InkEffects.ingredientFor(new ItemStack(Items.ENDER_EYE)).effect() == LinkProperty.FOLLOWING, "eye of ender -> Following");
        helper.assertTrue(InkEffects.ingredientFor(new ItemStack(Items.BLACK_DYE)).clears(), "black dye clears");
        for (var removed : new net.minecraft.world.item.Item[] {Items.STICK, Items.LEAD, Items.GOLD_NUGGET, Items.IRON_NUGGET, Items.MUSHROOM_STEW, Items.FIRE_CHARGE, Items.EXPERIENCE_BOTTLE}) {
            helper.assertTrue(InkEffects.ingredientFor(new ItemStack(removed)) == null, removed + " is not an ingredient");
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 100)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Mixing: one item per click switches its effect on, a repeat is not consumed, black dye clears, the panel carries exactly the mixed effects and the next fill starts plain")
    static void mixerOneIngredientPerEffect(ExtendedGameTestHelper helper) {
        helper.setBlock(1, 1, 1, ModBlocks.INK_MIXER.get());
        InkMixerBlockEntity mixer = helper.getBlockEntity(1, 1, 1, InkMixerBlockEntity.class);
        mixer.inventory.setStack(InkMixerBlockEntity.SLOT_INK_IN, new ItemStack(ModItems.INK_VIAL.get(), 2));
        mixer.inventory.setStack(InkMixerBlockEntity.SLOT_PAPER, new ItemStack(Items.PAPER, 2));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(mixer.hasInk(), "basin has ink"))
                .thenExecute(() -> Check.run(helper, () -> {
                    ItemStack gunpowder = mixer.addItems(new ItemStack(Items.GUNPOWDER, 3), 1);
                    helper.assertValueEqual(gunpowder.getCount(), 2, "one gunpowder consumed");
                    helper.assertTrue(mixer.hasEffect(LinkProperty.DISARM), "Disarm in the ink");
                    gunpowder = mixer.addItems(gunpowder, 1);
                    helper.assertValueEqual(gunpowder.getCount(), 2, "a second gunpowder is not consumed");
                    ItemStack feather = mixer.addItems(new ItemStack(Items.FEATHER), 1);
                    helper.assertTrue(feather.isEmpty() && mixer.hasEffect(LinkProperty.MAINTAIN_MOMENTUM), "feather adds Maintain Momentum");
                    ItemStack lead = mixer.addItems(new ItemStack(Items.LEAD), 1);
                    helper.assertValueEqual(lead.getCount(), 1, "a lead is not an ingredient any more");
                    helper.assertValueEqual(mixer.getEffects().size(), 2, "two effects in the ink");

                    ItemStack panel = mixer.getCraftedItem();
                    helper.assertTrue(!panel.isEmpty(), "paper + ink gives a panel");
                    mixer.buildItem(panel, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
                    helper.assertValueEqual(PageItem.getLinkProperties(panel), java.util.Set.of(LinkProperty.DISARM, LinkProperty.MAINTAIN_MOMENTUM), "panel carries exactly the mixed effects");
                    helper.assertTrue(!mixer.hasInk(), "basin emptied by the panel");
                }))
                .thenWaitUntil(() -> helper.assertTrue(mixer.hasInk(), "basin refilled from the second vial"))
                .thenExecute(() -> Check.run(helper, () -> {
                    helper.assertTrue(mixer.getEffects().isEmpty(), "fresh ink has no effects");
                    ItemStack dye = mixer.addItems(new ItemStack(Items.BLACK_DYE), 1);
                    helper.assertValueEqual(dye.getCount(), 1, "black dye on plain ink is not consumed");
                    mixer.addItems(new ItemStack(Items.ENDER_PEARL), 1);
                    helper.assertTrue(mixer.hasEffect(LinkProperty.INTRA_LINKING), "ender pearl adds Intra-Linking");
                    dye = mixer.addItems(dye, 1);
                    helper.assertTrue(dye.isEmpty() && mixer.getEffects().isEmpty(), "black dye clears the effects");
                }))
                .thenSucceed();
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
    @TestHolder(description = "Writing desk: only a folder is a target; a known primary symbol is written onto a fresh page (paper + ink), unknown symbols and modifiers are refused; a Scholar's desk knows everything")
    static void deskWritesKnownSymbolsIntoFolder(ExtendedGameTestHelper helper) {
        WritingDeskBlockEntity desk = placeDesk(helper);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var menu = new com.techbucketdivision.mystcraft.menu.WritingDeskMenu(1, player.getInventory(), desk);
        var flat = com.techbucketdivision.mystcraft.symbol.SymbolRegistry.get(MystIds.id("terrain_flat"));
        var north = com.techbucketdivision.mystcraft.symbol.SymbolRegistry.get(MystIds.id("mod_north"));

        helper.assertFalse(WritingDeskBlockEntity.isTargetItem(new ItemStack(ModItems.DESCRIPTIVE_BOOK.get())), "a book is not a desk target any more");
        helper.assertFalse(WritingDeskBlockEntity.isTargetItem(PageItem.createBlankPage()), "a page is not a desk target");
        helper.assertTrue(WritingDeskBlockEntity.isTargetItem(com.techbucketdivision.mystcraft.item.FolderItem.create("", java.util.List.of())), "a folder is the desk target");
        helper.assertFalse(menu.canWriteSymbol(), "empty desk cannot write");

        desk.main.setStack(WritingDeskBlockEntity.SLOT_TARGET, com.techbucketdivision.mystcraft.item.FolderItem.create("Desk test", java.util.List.of()));
        desk.main.setStack(WritingDeskBlockEntity.SLOT_PAPER, new ItemStack(Items.PAPER, 4));
        desk.setInk(new net.neoforged.neoforge.fluids.FluidStack(com.techbucketdivision.mystcraft.registry.ModFluids.BLACK_INK.get(), 1000));
        helper.assertTrue(menu.canWriteSymbol(), "folder + paper + ink can write");

        helper.assertFalse(desk.writeSymbol(player, flat), "an unknown symbol is refused");
        helper.assertValueEqual(desk.getInkAmount(), 1000, "no ink spent on a refused symbol");
        SymbolKnowledge.unlock(player, java.util.List.of(flat.id(), north.id()), "test");
        helper.assertTrue(SymbolKnowledge.knows(player, flat), "player knows terrain_flat now");
        helper.assertTrue(desk.writeSymbol(player, flat), "a known symbol is written");
        var pages = ((com.techbucketdivision.mystcraft.api.item.ItemBehaviours.PageProvider) desk.getTarget().getItem()).getPageList(player, desk.getTarget());
        helper.assertTrue(pages.size() == 1 && flat.id().equals(PageItem.getSymbolId(pages.getFirst())), "folder holds the written page: " + pages);
        helper.assertValueEqual(desk.getInkAmount(), 1000 - WritingDeskBlockEntity.INK_COST, "ink used per symbol");
        helper.assertValueEqual(desk.main.getStack(WritingDeskBlockEntity.SLOT_PAPER).getCount(), 3, "paper used");
        helper.assertFalse(desk.writeSymbol(player, north), "a modifier is never written as a page of its own");

        desk.setScholar(true);
        var meteors = com.techbucketdivision.mystcraft.symbol.SymbolRegistry.get(MystIds.id("env_meteors"));
        helper.assertFalse(SymbolKnowledge.knows(player, meteors), "player does not know meteors");
        helper.assertTrue(desk.writeSymbol(player, meteors), "a Scholar's desk writes any symbol");
        helper.succeed();
    }

    @GameTest
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Writing desk modifiers: a known modifier attaches to a folder page whose symbol takes it (ink only), is refused otherwise, and the last one can be detached")
    static void deskAttachesModifiers(ExtendedGameTestHelper helper) {
        WritingDeskBlockEntity desk = placeDesk(helper);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var sun = MystIds.id("sun_normal");
        var north = com.techbucketdivision.mystcraft.symbol.SymbolRegistry.get(MystIds.id("mod_north"));
        var red = com.techbucketdivision.mystcraft.symbol.SymbolRegistry.get(MystIds.id("mod_color_red"));
        var half = com.techbucketdivision.mystcraft.symbol.SymbolRegistry.get(MystIds.id("mod_half"));
        desk.main.setStack(WritingDeskBlockEntity.SLOT_TARGET, com.techbucketdivision.mystcraft.item.FolderItem.create("Mods", java.util.List.of(
                PageItem.createSymbolPage(sun), PageItem.createSymbolPage(MystIds.id("terrain_flat")))));
        desk.setInk(new net.neoforged.neoforge.fluids.FluidStack(com.techbucketdivision.mystcraft.registry.ModFluids.BLACK_INK.get(), 1000));

        helper.assertFalse(desk.attachModifier(player, 0, north), "unknown modifier refused");
        SymbolKnowledge.unlock(player, java.util.List.of(north.id(), red.id(), half.id()), "test");
        helper.assertTrue(desk.attachModifier(player, 0, north), "north attaches to the sun");
        helper.assertFalse(desk.attachModifier(player, 1, north), "a direction does not attach to terrain");
        helper.assertFalse(desk.attachModifier(player, 1, red), "a colour does not attach to terrain");
        helper.assertTrue(desk.attachModifier(player, 0, half), "half length attaches to the sun");
        helper.assertFalse(desk.attachModifier(player, 5, half), "no page at index 5");
        var pages = ((com.techbucketdivision.mystcraft.api.item.ItemBehaviours.PageProvider) desk.getTarget().getItem()).getPageList(player, desk.getTarget());
        helper.assertValueEqual(PageItem.getModifiers(pages.get(0)), java.util.List.of(north.id(), half.id()), "sun page carries north then half");
        helper.assertValueEqual(desk.getInkAmount(), 1000 - 2 * WritingDeskBlockEntity.INK_COST, "ink per attached modifier, no paper");
        helper.assertTrue(desk.detachLastModifier(player, 0), "detach the last modifier");
        pages = ((com.techbucketdivision.mystcraft.api.item.ItemBehaviours.PageProvider) desk.getTarget().getItem()).getPageList(player, desk.getTarget());
        helper.assertValueEqual(PageItem.getModifiers(pages.get(0)), java.util.List.of(north.id()), "only north left");
        helper.assertFalse(desk.detachLastModifier(player, 1), "nothing to detach on the terrain page");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 100)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Writing desk drafts: a written page is a draft, right-click erases it and refunds ink and paper, taking the folder out makes drafts permanent")
    static void deskDraftsUndoAndCommit(ExtendedGameTestHelper helper) {
        WritingDeskBlockEntity desk = placeDesk(helper);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var symbol = com.techbucketdivision.mystcraft.symbol.SymbolRegistry.get(MystIds.id("terrain_flat"));
        SymbolKnowledge.unlock(player, java.util.List.of(symbol.id()), "test");
        desk.setInk(new net.neoforged.neoforge.fluids.FluidStack(com.techbucketdivision.mystcraft.registry.ModFluids.BLACK_INK.get(), 1000));

        // folder target + paper: writing appends a draft page
        ItemStack folder = com.techbucketdivision.mystcraft.item.FolderItem.create("Drafts", java.util.List.of());
        desk.main.setStack(WritingDeskBlockEntity.SLOT_TARGET, folder);
        desk.main.setStack(WritingDeskBlockEntity.SLOT_PAPER, new ItemStack(Items.PAPER, 3));
        desk.writeSymbol(player, symbol);
        helper.assertValueEqual(desk.getDrafts().size(), 1, "one draft after writing");
        helper.assertTrue(desk.isDraft(0), "the new page is a draft");
        helper.assertValueEqual(desk.getInkAmount(), 950, "ink used");
        helper.assertValueEqual(desk.main.getStack(WritingDeskBlockEntity.SLOT_PAPER).getCount(), 2, "paper used");

        // right-click on the draft: page gone, ink and paper back, nothing handed to the player
        helper.assertTrue(desk.removePage(player, 0).isEmpty(), "erasing a draft hands nothing back");
        helper.assertValueEqual(desk.getDrafts().size(), 0, "no drafts after erasing");
        helper.assertValueEqual(desk.getInkAmount(), 1000, "ink refunded");
        helper.assertValueEqual(desk.main.getStack(WritingDeskBlockEntity.SLOT_PAPER).getCount(), 3, "paper refunded");
        var pages = ((com.techbucketdivision.mystcraft.api.item.ItemBehaviours.PageProvider) desk.getTarget().getItem()).getPageList(player, desk.getTarget());
        helper.assertTrue(pages.isEmpty(), "folder is empty again (got " + pages + ")");

        // write again, take the folder out: the draft is committed on the next tick; a committed page is handed back on removal
        desk.writeSymbol(player, symbol);
        helper.assertValueEqual(desk.getDrafts().size(), 1, "draft written again");
        ItemStack folderOut = desk.getTarget();
        desk.main.setStack(WritingDeskBlockEntity.SLOT_TARGET, ItemStack.EMPTY);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(desk.getDrafts().size(), 0, "drafts committed once the target left the slot"))
                .thenExecute(() -> {
                    desk.main.setStack(WritingDeskBlockEntity.SLOT_TARGET, folderOut);
                    ItemStack back = desk.removePage(player, 0);
                    helper.assertTrue(symbol.id().equals(PageItem.getSymbolId(back)), "a permanent page is handed back when removed");
                    helper.assertValueEqual(desk.getInkAmount(), 950, "no refund for a permanent page");
                })
                .thenSucceed();
    }

    private static WritingDeskBlockEntity placeDesk(ExtendedGameTestHelper helper) {
        var head = ModBlocks.WRITING_DESK.get().defaultBlockState().setValue(WritingDeskBlock.FACING, Direction.EAST);
        helper.setBlock(0, 1, 1, head);
        helper.setBlock(1, 1, 1, head.setValue(WritingDeskBlock.FOOT, true));
        return helper.getBlockEntity(0, 1, 1, WritingDeskBlockEntity.class);
    }
}

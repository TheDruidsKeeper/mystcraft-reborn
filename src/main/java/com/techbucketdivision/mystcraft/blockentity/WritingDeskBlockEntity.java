package com.techbucketdivision.mystcraft.blockentity;

import com.techbucketdivision.mystcraft.Mystcraft;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.block.WritingDeskBlock;
import com.techbucketdivision.mystcraft.item.DescriptiveBookItem;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.menu.WritingDeskMenu;
import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import com.techbucketdivision.mystcraft.registry.ModCriteria;
import com.techbucketdivision.mystcraft.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Writing Desk (REQUIREMENTS §3.10). Main inventory: 0 target (writable / renameable / page acceptor, limit 1),
 * 1 paper, 2 ink container in, 3 empty container out. Tabs: 25 notebook slots. Inkwell: 1000 mB of ink.
 */
public class WritingDeskBlockEntity extends MystBlockEntity implements MenuProvider {
    public static final int SLOT_TARGET = 0;
    public static final int SLOT_PAPER = 1;
    public static final int SLOT_CONTAINER_IN = 2;
    public static final int SLOT_CONTAINER_OUT = 3;
    public static final int TAB_COUNT = 25;
    public static final int TANK_CAPACITY = FluidType.BUCKET_VOLUME;
    public static final int INK_COST = 50;

    public final FilteredItemHandler main = new FilteredItemHandler(4, this::acceptsMain, slot -> slot == SLOT_TARGET ? 1 : 64, this::markForUpdate);
    public final FilteredItemHandler tabs = new FilteredItemHandler(TAB_COUNT, (slot, res) -> isNotebook(res.toStack()), this::markForUpdate);
    public final FluidStacksResourceHandler inkwell = new FluidStacksResourceHandler(1, TANK_CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.isEmpty() || ModFluids.isInk(resource.getFluid());
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            markForUpdate();
        }
    };

    public WritingDeskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WRITING_DESK.get(), pos, state);
    }

    private boolean acceptsMain(int slot, ItemResource resource) {
        ItemStack stack = resource.toStack();
        return switch (slot) {
            case SLOT_TARGET -> isTargetItem(stack);
            case SLOT_PAPER -> stack.is(Items.PAPER);
            case SLOT_CONTAINER_IN -> BookUtil.isInkContainer(stack) || isEmptyFluidContainer(stack);
            default -> true;
        };
    }

    /** A fluid container (bucket, bottle, ...) that currently holds nothing. */
    private static boolean isEmptyFluidContainer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return FluidUtil.getFirstStackContained(stack).isEmpty()
                && stack.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(stack)) != null;
    }

    public static boolean isTargetItem(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ItemBehaviours.Writable
                || stack.getItem() instanceof ItemBehaviours.Renameable
                || stack.getItem() instanceof ItemBehaviours.PageAcceptor);
    }

    /** Items allowed in the notebook tabs: page collections and writable items (folders, portfolios, books, pages). */
    public static boolean isNotebook(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ItemBehaviours.PageCollection
                || stack.getItem() instanceof ItemBehaviours.Writable);
    }

    // --- persistence -------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putChild("fluid", inkwell);
        output.putChild("items", main);
        output.putChild("notebooks", tabs);
        output.store("drafts", Draft.LIST_CODEC, List.copyOf(drafts));
        output.putInt("draft_target", draftTargetId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.readChild("fluid", inkwell);
        input.readChild("items", main);
        input.readChild("notebooks", tabs);
        drafts = new ArrayList<>(input.read("drafts", Draft.LIST_CODEC).orElse(List.of()));
        draftTargetId = input.getIntOr("draft_target", 0);
    }

    // --- drafts ------------------------------------------------------------------------------------------------------

    /**
     * A page written at this desk that is not yet permanent: {@code index} into the target's page list, and whether a
     * sheet of paper was spent on it (Reborn: writing is provisional until the target leaves the slot, so a slip can
     * be undone with its paper and ink refunded).
     */
    public record Draft(int index, boolean paperUsed) {
        public static final com.mojang.serialization.Codec<Draft> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                com.mojang.serialization.Codec.INT.fieldOf("index").forGetter(Draft::index),
                com.mojang.serialization.Codec.BOOL.fieldOf("paper").forGetter(Draft::paperUsed)).apply(i, Draft::new));
        public static final com.mojang.serialization.Codec<List<Draft>> LIST_CODEC = CODEC.listOf();
    }

    private List<Draft> drafts = new ArrayList<>();
    /** Identity of the target the drafts belong to (item registry id hash); a different item in the slot commits them. */
    private int draftTargetId;

    /** Pages of the current target that are still drafts (indices into its page list), in writing order. */
    public List<Draft> getDrafts() {
        return List.copyOf(drafts);
    }

    public boolean isDraft(int pageIndex) {
        for (Draft d : drafts) if (d.index() == pageIndex) return true;
        return false;
    }

    /** Makes every draft permanent (the target was taken out, the desk broken, ...). */
    public void commitDrafts() {
        if (drafts.isEmpty()) return;
        Mystcraft.LOGGER.debug("[desk] {} draft page(s) became permanent at {}", drafts.size(), getBlockPos().toShortString());
        drafts.clear();
        draftTargetId = 0;
        markForUpdate();
    }

    private static int targetId(ItemStack target) {
        return target.isEmpty() ? 0 : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(target.getItem()).hashCode();
    }

    /** Called every server tick: drafts die with the target they were written into. */
    private void checkDraftTarget() {
        if (drafts.isEmpty()) return;
        int id = targetId(getTarget());
        if (id != draftTargetId) commitDrafts();
    }

    /**
     * Erases the most recently written draft: the page goes back to blank (writable books) or is removed (folders /
     * single pages), and the ink and paper it cost come back. Returns false when there is nothing to undo.
     */
    public boolean undoLastDraft(Player player) {
        if (!isServer() || drafts.isEmpty()) return false;
        ItemStack target = getTarget();
        if (target.isEmpty()) {
            commitDrafts();
            return false;
        }
        Draft draft = drafts.remove(drafts.size() - 1);
        boolean removed = false;
        if (target.getItem() instanceof ItemBehaviours.OrderablePageProvider p) {
            List<ItemStack> pages = p.getPageList(player, target);
            if (draft.index() >= 0 && draft.index() < pages.size()) {
                p.removePage(player, target, draft.index());
                removed = true;
            }
        } else if (target.getItem() instanceof PageItem) {
            // a single page written straight onto paper: back to paper
            target = ItemStack.EMPTY;
            removed = true;
        } else if (target.getItem() instanceof ItemBehaviours.PageProvider p) {
            List<ItemStack> pages = new ArrayList<>(p.getPageList(player, target));
            if (draft.index() >= 0 && draft.index() < pages.size() && PageItem.isSymbolPage(pages.get(draft.index()))) {
                pages.set(draft.index(), PageItem.createBlankPage());
                if (target.getItem() instanceof DescriptiveBookItem) DescriptiveBookItem.setPages(target, pages);
                removed = true;
            }
        }
        if (!removed) {
            markForUpdate();
            return false;
        }
        main.setStack(SLOT_TARGET, target);
        try (Transaction tx = Transaction.openRoot()) {
            inkwell.insert(0, FluidResource.of(ModFluids.BLACK_INK.get()), INK_COST, tx);
            tx.commit();
        }
        if (draft.paperUsed()) {
            ItemStack paper = main.getStack(SLOT_PAPER);
            if (paper.isEmpty()) main.setStack(SLOT_PAPER, new ItemStack(Items.PAPER));
            else if (paper.getCount() < paper.getMaxStackSize()) main.setStack(SLOT_PAPER, paper.copyWithCount(paper.getCount() + 1));
            else if (!player.getInventory().add(new ItemStack(Items.PAPER))) player.drop(new ItemStack(Items.PAPER), false);
        }
        if (drafts.isEmpty()) draftTargetId = 0;
        Mystcraft.LOGGER.debug("[desk] {} undid draft page {} (paper refunded: {})", player.getPlainTextName(), draft.index(), draft.paperUsed());
        markForUpdate();
        return true;
    }

    private void recordDraft(int index, boolean paperUsed) {
        if (drafts.isEmpty()) draftTargetId = targetId(getTarget());
        drafts.add(new Draft(index, paperUsed));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        commitDrafts();
        dropContents(main);
        dropContents(tabs);
        super.preRemoveSideEffects(pos, state);
    }

    // --- ink -------------------------------------------------------------------------------------------------------------

    public FluidStack getInk() {
        return FluidUtil.getStack(inkwell, 0);
    }

    /** Client-side sync of the tank contents. */
    public void setInk(FluidStack fluid) {
        inkwell.set(0, FluidResource.of(fluid), fluid.getAmount());
    }

    public int getInkAmount() {
        return inkwell.getAmountAsInt(0);
    }

    private boolean hasEnoughInk() {
        return getInkAmount() >= INK_COST;
    }

    private void useInk() {
        try (Transaction tx = Transaction.openRoot()) {
            inkwell.extract(0, inkwell.getResource(0), INK_COST, tx);
            tx.commit();
        }
    }

    /** Server tick: fill the inkwell from the container in slot 2, or fill that container from the inkwell. */
    public void serverTick() {
        checkDraftTarget();
        ItemStack container = main.getStack(SLOT_CONTAINER_IN);
        if (container.isEmpty()) return;
        FluidStack contained = FluidUtil.getFirstStackContained(container);
        InkContainers.Result moved;
        if (!contained.isEmpty()) {
            // container -> tank: the whole container amount must fit
            if (!ModFluids.isInk(contained.getFluid())) return;
            if (getInkAmount() + contained.getAmount() > TANK_CAPACITY) return;
            moved = InkContainers.drainInto(container, FluidResource.of(contained), contained.getAmount(), inkwell, 0);
        } else {
            // tank -> container (one bucket)
            if (getInkAmount() < FluidType.BUCKET_VOLUME) return;
            moved = InkContainers.fillFrom(container, inkwell, 0, FluidType.BUCKET_VOLUME);
        }
        if (moved == null) return;
        ItemStack result = moved.container();
        if (!fitsOutput(result)) {
            // undo: the tank already changed; put the fluid back the way it was
            revert(moved, contained);
            return;
        }
        container.shrink(1);
        main.setStack(SLOT_CONTAINER_IN, container);
        if (!result.isEmpty()) {
            ItemStack out = main.getStack(SLOT_CONTAINER_OUT);
            main.setStack(SLOT_CONTAINER_OUT, out.isEmpty() ? result : out.copyWithCount(out.getCount() + result.getCount()));
        }
        markForUpdate();
    }

    /** Reverses a transfer whose empty container did not fit the output slot. */
    private void revert(InkContainers.Result moved, FluidStack contained) {
        try (Transaction tx = Transaction.openRoot()) {
            if (!contained.isEmpty()) inkwell.extract(0, FluidResource.of(contained), moved.amount(), tx);
            else inkwell.insert(0, FluidResource.of(ModFluids.BLACK_INK.get()), moved.amount(), tx);
            tx.commit();
        }
    }

    private boolean fitsOutput(ItemStack stack) {
        if (stack.isEmpty()) return true;
        ItemStack out = main.getStack(SLOT_CONTAINER_OUT);
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, stack) && out.getCount() + stack.getCount() <= out.getMaxStackSize();
    }

    // --- target -----------------------------------------------------------------------------------------------------------

    public ItemStack getTarget() {
        return main.getStack(SLOT_TARGET);
    }

    /** Alias for renderers. */
    public ItemStack getDisplayItem() {
        return getTarget();
    }

    public int getPaperCount() {
        return main.getStack(SLOT_PAPER).getCount();
    }

    public boolean hasBackboard() {
        Level level = getLevel();
        return level != null && WritingDeskBlock.hasBackboard(level, getBlockPos());
    }

    /** The current display name of the target ("" when none / not renameable). */
    public String getTargetString() {
        ItemStack target = getTarget();
        if (target.isEmpty() || !(target.getItem() instanceof ItemBehaviours.Renameable r)) return "";
        String name = r.getDisplayName(target);
        return name == null ? "" : name;
    }

    public void setBookTitle(Player player, String title) {
        ItemStack target = getTarget();
        if (target.isEmpty() || !(target.getItem() instanceof ItemBehaviours.Renameable r)) return;
        r.setDisplayName(player, target, title);
        main.setStack(SLOT_TARGET, target);
    }

    public @Nullable List<ItemStack> getBookPageList(@Nullable Player player) {
        ItemStack target = getTarget();
        if (target.isEmpty() || !(target.getItem() instanceof ItemBehaviours.PageProvider p)) return null;
        return p.getPageList(player, target);
    }

    /** Activates the linking item in the target slot (server only). */
    public void link(Entity entity) {
        if (!(getLevel() instanceof ServerLevel serverLevel)) return;
        ItemStack book = getTarget();
        if (!BookUtil.isLinkingItem(book)) return;
        BookUtil.activate(book, serverLevel, entity);
    }

    /**
     * Writes a symbol into the target (server only): moves a paper into an empty target slot as a blank page first;
     * writable targets get the symbol written, page acceptors receive a new symbol page made from a paper.
     */
    public void writeSymbol(Player player, AgeSymbol symbol) {
        if (!isServer() || !hasEnoughInk()) return;
        ItemStack paper = main.getStack(SLOT_PAPER);
        boolean paperForNewPage = false;
        if (getTarget().isEmpty() && !paper.isEmpty()) {
            main.setStack(SLOT_TARGET, PageItem.createBlankPage());
            paper.shrink(1);
            main.setStack(SLOT_PAPER, paper);
            paperForNewPage = true;
        }
        ItemStack target = getTarget();
        if (target.isEmpty()) return;

        if (target.getItem() instanceof ItemBehaviours.Writable w) {
            int index = firstBlankPage(player, target);
            if (w.writeSymbol(player, target, symbol)) {
                main.setStack(SLOT_TARGET, target);
                useInk();
                recordDraft(index, paperForNewPage);
                award(player);
                return;
            }
        }
        paper = main.getStack(SLOT_PAPER);
        if (!paper.isEmpty() && target.getItem() instanceof ItemBehaviours.PageAcceptor acceptor) {
            ItemStack page = PageItem.createSymbolPage(symbol);
            int index = firstFreeIndex(player, target); // where the acceptor will put it (folders fill the first gap)
            if (!page.isEmpty() && acceptor.addPage(player, target, page).isEmpty()) {
                main.setStack(SLOT_TARGET, target);
                useInk();
                paper.shrink(1);
                main.setStack(SLOT_PAPER, paper);
                recordDraft(index, true);
                award(player);
            }
        }
    }

    /** Index a page acceptor will append at: the first empty slot of its page list, else the end. */
    private static int firstFreeIndex(Player player, ItemStack target) {
        if (!(target.getItem() instanceof ItemBehaviours.PageProvider p)) return 0;
        List<ItemStack> pages = p.getPageList(player, target);
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).isEmpty()) return i;
        return pages.size();
    }

    /** Index of the page a Writable target will write into (its first blank page), or 0 for a single page. */
    private static int firstBlankPage(Player player, ItemStack target) {
        if (!(target.getItem() instanceof ItemBehaviours.PageProvider p)) return 0;
        List<ItemStack> pages = p.getPageList(player, target);
        for (int i = 0; i < pages.size(); i++) if (PageItem.isBlank(pages.get(i))) return i;
        return 0;
    }

    private static void award(Player player) {
        if (player instanceof ServerPlayer sp) ModCriteria.WRITING_DESK_WRITE.get().trigger(sp);
    }

    // --- tabs -----------------------------------------------------------------------------------------------------------------

    public int getMaxSurfaceTabCount() {
        return TAB_COUNT;
    }

    /** The notebook in a tab (copy), or empty when the slot holds nothing usable. */
    public ItemStack getTabItem(int tab) {
        if (tab < 0 || tab >= TAB_COUNT) return ItemStack.EMPTY;
        ItemStack stack = tabs.getStack(tab);
        return isNotebook(stack) ? stack : ItemStack.EMPTY;
    }

    private void setTabItem(int tab, ItemStack stack) {
        tabs.setStack(tab, stack);
        markForUpdate();
    }

    /** Removes the page at {@code index} from an ordered notebook (folder). */
    public ItemStack removePageFromSurface(Player player, int tab, int index) {
        ItemStack notebook = getTabItem(tab);
        if (notebook.isEmpty() || !(notebook.getItem() instanceof ItemBehaviours.OrderablePageProvider p)) return ItemStack.EMPTY;
        ItemStack result = p.removePage(player, notebook, index);
        if (result.isEmpty()) return ItemStack.EMPTY;
        setTabItem(tab, notebook);
        return result;
    }

    /** Removes pages equal to {@code page} from an unordered collection (portfolio). */
    public ItemStack removePageFromSurface(Player player, int tab, ItemStack page) {
        ItemStack notebook = getTabItem(tab);
        if (notebook.isEmpty() || !(notebook.getItem() instanceof ItemBehaviours.PageCollection c)) return ItemStack.EMPTY;
        ItemStack result = c.remove(player, notebook, page);
        if (result.isEmpty()) return ItemStack.EMPTY;
        setTabItem(tab, notebook);
        return result;
    }

    /** Adds a page (or stack of pages) to a notebook; returns the remainder. */
    public ItemStack addPageToTab(Player player, int tab, ItemStack page) {
        ItemStack notebook = getTabItem(tab);
        if (notebook.isEmpty() || !(notebook.getItem() instanceof ItemBehaviours.PageAcceptor a)) return page;
        ItemStack result = a.addPage(player, notebook, page);
        setTabItem(tab, notebook);
        return result;
    }

    /** Places a page at {@code index} of an ordered notebook, or adds it to a collection; returns the remainder. */
    public ItemStack placePageOnSurface(Player player, int tab, ItemStack page, int index) {
        ItemStack notebook = getTabItem(tab);
        if (notebook.isEmpty()) return page;
        ItemStack result;
        if (notebook.getItem() instanceof ItemBehaviours.OrderablePageProvider p) {
            result = p.setPage(player, notebook, page, index);
        } else if (notebook.getItem() instanceof ItemBehaviours.PageCollection c) {
            result = c.addPage(player, notebook, page);
        } else {
            return page;
        }
        setTabItem(tab, notebook);
        return result;
    }

    // --- menu ------------------------------------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mystcraft.writing_desk");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WritingDeskMenu(containerId, inventory, this);
    }
}

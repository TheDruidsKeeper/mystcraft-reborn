package com.techbucketdivision.mystcraft.blockentity;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.block.WritingDeskBlock;
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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.readChild("fluid", inkwell);
        input.readChild("items", main);
        input.readChild("notebooks", tabs);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
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
        ItemStack container = main.getStack(SLOT_CONTAINER_IN);
        if (container.isEmpty()) return;
        ItemStack single = container.copyWithCount(1);
        ItemAccess access = ItemAccess.forStack(single);
        ResourceHandler<FluidResource> handler = access.getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) return;

        FluidStack contained = FluidUtil.getFirstStackContained(single);
        ItemStack result = ItemStack.EMPTY;
        if (!contained.isEmpty()) {
            // container -> tank: the whole container amount must fit
            if (!ModFluids.isInk(contained.getFluid())) return;
            if (getInkAmount() + contained.getAmount() > TANK_CAPACITY) return;
            try (Transaction tx = Transaction.openRoot()) {
                FluidResource res = FluidResource.of(contained);
                int drained = handler.extract(res, contained.getAmount(), tx);
                int filled = inkwell.insert(0, res, drained, tx);
                if (drained <= 0 || filled != drained) return;
                result = access.getResource().toStack(access.getAmount());
                if (!fitsOutput(result)) return;
                tx.commit();
            }
        } else {
            // tank -> container (one bucket)
            if (getInkAmount() < FluidType.BUCKET_VOLUME) return;
            try (Transaction tx = Transaction.openRoot()) {
                FluidResource res = inkwell.getResource(0);
                int inserted = handler.insert(res, FluidType.BUCKET_VOLUME, tx);
                if (inserted != FluidType.BUCKET_VOLUME) return;
                int drained = inkwell.extract(0, res, inserted, tx);
                if (drained != inserted) return;
                result = access.getResource().toStack(access.getAmount());
                if (!fitsOutput(result)) return;
                tx.commit();
            }
        }
        container.shrink(1);
        main.setStack(SLOT_CONTAINER_IN, container);
        if (!result.isEmpty()) {
            ItemStack out = main.getStack(SLOT_CONTAINER_OUT);
            main.setStack(SLOT_CONTAINER_OUT, out.isEmpty() ? result : out.copyWithCount(out.getCount() + result.getCount()));
        }
        markForUpdate();
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
        if (getTarget().isEmpty() && !paper.isEmpty()) {
            main.setStack(SLOT_TARGET, PageItem.createBlankPage());
            paper.shrink(1);
            main.setStack(SLOT_PAPER, paper);
        }
        ItemStack target = getTarget();
        if (target.isEmpty()) return;

        if (target.getItem() instanceof ItemBehaviours.Writable w && w.writeSymbol(player, target, symbol)) {
            main.setStack(SLOT_TARGET, target);
            useInk();
            award(player);
            return;
        }
        paper = main.getStack(SLOT_PAPER);
        if (!paper.isEmpty() && target.getItem() instanceof ItemBehaviours.PageAcceptor acceptor) {
            ItemStack page = PageItem.createSymbolPage(symbol);
            if (!page.isEmpty() && acceptor.addPage(player, target, page).isEmpty()) {
                main.setStack(SLOT_TARGET, target);
                useInk();
                paper.shrink(1);
                main.setStack(SLOT_PAPER, paper);
                award(player);
            }
        }
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

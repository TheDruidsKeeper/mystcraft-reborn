package com.tbd.mystcraft.blockentity;

import com.tbd.mystcraft.Mystcraft;

import com.tbd.mystcraft.api.item.ItemBehaviours;
import com.tbd.mystcraft.api.symbol.AgeSymbol;
import com.tbd.mystcraft.item.FolderItem;
import com.tbd.mystcraft.item.InkVialItem;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.item.component.SymbolPage;
import com.tbd.mystcraft.knowledge.SymbolKnowledge;
import com.tbd.mystcraft.menu.WritingDeskMenu;
import com.tbd.mystcraft.registry.ModBlockEntities;
import com.tbd.mystcraft.registry.ModCriteria;
import com.tbd.mystcraft.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
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
 * Writing Desk (original spec §3.10, Reborn rework: world-building plan §4). Main inventory: 0 target (a Collation
 * Folder, limit 1), 1 paper, 2 ink container in, 3 empty container out. Inkwell: {@link #TANK_CAPACITY} of ink, four
 * Ink Vials. The desk has no
 * notebooks: it writes copies of the symbols the <i>player</i> knows ({@link SymbolKnowledge}) into the folder, and
 * attaches known modifiers to the folder's pages. A Scholar's desk ({@link #isScholar()}) offers every registered
 * symbol. Pages written here stay drafts until the folder leaves the desk.
 */
public class WritingDeskBlockEntity extends MystBlockEntity implements MenuProvider {
    public static final int SLOT_TARGET = 0;
    public static final int SLOT_PAPER = 1;
    public static final int SLOT_CONTAINER_IN = 2;
    public static final int SLOT_CONTAINER_OUT = 3;
    public static final int TANK_CAPACITY = FluidType.BUCKET_VOLUME;
    public static final int INK_COST = 50;

    public final FilteredItemHandler main = new FilteredItemHandler(4, this::acceptsMain, slot -> slot == SLOT_TARGET ? 1 : 64, this::markForUpdate);
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

    /** Only a Collation Folder goes in the target slot (books are bound at the Book Binder from a folder's pages). */
    public static boolean isTargetItem(ItemStack stack) {
        return FolderItem.isFolder(stack);
    }

    // --- scholar's desk -------------------------------------------------------------------------------------------

    private boolean scholar;

    /** A Scholar's desk (creative) offers every registered symbol instead of the player's knowledge. */
    public boolean isScholar() {
        return scholar;
    }

    public void setScholar(boolean scholar) {
        this.scholar = scholar;
        markForUpdate();
    }

    /** Whether this desk lets {@code player} write {@code symbol}. */
    public boolean canUse(Player player, AgeSymbol symbol) {
        return scholar || SymbolKnowledge.knows(player, symbol);
    }

    // --- persistence -------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putChild("fluid", inkwell);
        output.putChild("items", main);
        output.putBoolean("scholar", scholar);
        output.store("drafts", Draft.LIST_CODEC, List.copyOf(drafts));
        output.putInt("draft_target", draftTargetId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.readChild("fluid", inkwell);
        input.readChild("items", main);
        scholar = input.getBooleanOr("scholar", false);
        drafts = new ArrayList<>(input.read("drafts", Draft.LIST_CODEC).orElse(List.of()));
        draftTargetId = input.getIntOr("draft_target", 0);
    }

    // --- drafts ------------------------------------------------------------------------------------------------------

    /**
     * A page written at this desk that is not yet permanent: {@code index} into the target's page list, and whether a
     * sheet of paper was spent on it (Reborn: writing is provisional until the folder leaves the slot, so a slip can
     * be erased by right-clicking it with its paper and ink refunded).
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
     * Removes the folder page at {@code index} (server only). A draft written here is erased and its ink and paper
     * come back (the page never existed); any other page is handed back as the returned stack. Empty when nothing
     * was removed.
     */
    public ItemStack removePage(Player player, int index) {
        if (!isServer()) return ItemStack.EMPTY;
        ItemStack target = getTarget();
        if (!(target.getItem() instanceof ItemBehaviours.OrderablePageProvider p)) return ItemStack.EMPTY;
        List<ItemStack> pages = p.getPageList(player, target);
        if (index < 0 || index >= pages.size() || pages.get(index).isEmpty()) return ItemStack.EMPTY;
        Draft draft = null;
        for (Draft d : drafts) if (d.index() == index) draft = d;
        ItemStack removed = p.removePage(player, target, index);
        main.setStack(SLOT_TARGET, target);
        if (draft == null) {
            markForUpdate();
            return removed;
        }
        drafts.remove(draft);
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
        Mystcraft.LOGGER.debug("[desk] {} erased draft page {} (paper refunded: {})", player.getPlainTextName(), index, draft.paperUsed());
        markForUpdate();
        return ItemStack.EMPTY;
    }

    private void recordDraft(int index, boolean paperUsed) {
        if (drafts.isEmpty()) draftTargetId = targetId(getTarget());
        drafts.add(new Draft(index, paperUsed));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        commitDrafts();
        dropContents(main);
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

    /**
     * Server tick: pour one container from slot 2 into the inkwell (one vial per tick, as long as a whole one fits),
     * or fill one empty container from the inkwell.
     */
    public void serverTick() {
        checkDraftTarget();
        ItemStack container = main.getStack(SLOT_CONTAINER_IN);
        if (container.isEmpty()) return;
        FluidStack contained = FluidUtil.getFirstStackContained(container);
        InkContainers.Result moved;
        if (!contained.isEmpty()) {
            // container -> tank: one container's worth (FluidUtil reads the stack one by one) must fit whole
            if (!ModFluids.isInk(contained.getFluid())) return;
            if (getInkAmount() + contained.getAmount() > TANK_CAPACITY) return;
            moved = InkContainers.drainInto(container, FluidResource.of(contained), contained.getAmount(), inkwell, 0);
        } else {
            // tank -> container (one vial)
            if (getInkAmount() < InkVialItem.VOLUME) return;
            moved = InkContainers.fillFrom(container, inkwell, 0, InkVialItem.VOLUME);
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

    /**
     * Writes a copy of a known symbol onto a fresh page in the folder (server only): costs one paper and
     * {@link #INK_COST} ink; the page is a draft until the folder leaves the desk. Returns false when the player does
     * not know the symbol, the symbol is a modifier (those are attached, not written), or paper / ink / folder is
     * missing.
     */
    public boolean writeSymbol(Player player, AgeSymbol symbol) {
        if (!isServer() || !hasEnoughInk() || !canUse(player, symbol) || symbol.category().isModifier()) return false;
        ItemStack paper = main.getStack(SLOT_PAPER);
        ItemStack target = getTarget();
        if (paper.isEmpty() || !(target.getItem() instanceof ItemBehaviours.PageAcceptor acceptor)) return false;
        ItemStack page = PageItem.createSymbolPage(symbol);
        int index = firstFreeIndex(player, target); // where the acceptor will put it (folders fill the first gap)
        if (!acceptor.addPage(player, target, page).isEmpty()) return false;
        main.setStack(SLOT_TARGET, target);
        useInk();
        paper.shrink(1);
        main.setStack(SLOT_PAPER, paper);
        recordDraft(index, true);
        award(player);
        Mystcraft.LOGGER.debug("[desk] {} wrote {} into slot {}", player.getPlainTextName(), symbol.id(), index);
        return true;
    }

    /**
     * Attaches a known modifier to the folder page at {@code index} (server only): costs {@link #INK_COST} ink, no
     * paper. Refused when the page's symbol does not take the modifier's slot, the page is not a player's page, or the
     * modifier is unknown to the player.
     */
    public boolean attachModifier(Player player, int index, AgeSymbol modifier) {
        if (!isServer() || !hasEnoughInk() || !canUse(player, modifier) || modifier.fills() == null) return false;
        ItemStack target = getTarget();
        if (!(target.getItem() instanceof ItemBehaviours.OrderablePageProvider provider)) return false;
        List<ItemStack> pages = provider.getPageList(player, target);
        if (index < 0 || index >= pages.size()) return false;
        ItemStack page = pages.get(index);
        SymbolPage symbolPage = PageItem.getSymbolPage(page);
        AgeSymbol symbol = symbolPage == null ? null : symbolPage.resolve();
        if (symbol == null || !symbol.takes(modifier)) return false;
        ItemStack updated = page.copy();
        PageItem.setSymbolPage(updated, symbolPage.withModifier(modifier.id()));
        provider.setPage(player, target, updated, index);
        main.setStack(SLOT_TARGET, target);
        useInk();
        award(player);
        Mystcraft.LOGGER.debug("[desk] {} attached {} to page {} ({})", player.getPlainTextName(), modifier.id(), index, symbol.id());
        markForUpdate();
        return true;
    }

    /** Removes the last attached modifier from the folder page at {@code index} (server only; no refund). */
    public boolean detachLastModifier(Player player, int index) {
        if (!isServer()) return false;
        ItemStack target = getTarget();
        if (!(target.getItem() instanceof ItemBehaviours.OrderablePageProvider provider)) return false;
        List<ItemStack> pages = provider.getPageList(player, target);
        if (index < 0 || index >= pages.size()) return false;
        ItemStack page = pages.get(index);
        SymbolPage symbolPage = PageItem.getSymbolPage(page);
        if (symbolPage == null || symbolPage.modifiers().isEmpty()) return false;
        ItemStack updated = page.copy();
        PageItem.setSymbolPage(updated, symbolPage.withoutModifier(symbolPage.modifiers().size() - 1));
        provider.setPage(player, target, updated, index);
        main.setStack(SLOT_TARGET, target);
        markForUpdate();
        return true;
    }

    /** Index a page acceptor will append at: the first empty slot of its page list, else the end. */
    private static int firstFreeIndex(Player player, ItemStack target) {
        if (!(target.getItem() instanceof ItemBehaviours.PageProvider p)) return 0;
        List<ItemStack> pages = p.getPageList(player, target);
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).isEmpty()) return i;
        return pages.size();
    }

    private static void award(Player player) {
        if (player instanceof ServerPlayer sp) ModCriteria.WRITING_DESK_WRITE.get().trigger(sp);
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

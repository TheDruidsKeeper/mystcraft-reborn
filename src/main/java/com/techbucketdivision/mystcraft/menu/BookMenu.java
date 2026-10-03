package com.techbucketdivision.mystcraft.menu;

import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.blockentity.BookDisplayBlockEntity;
import com.techbucketdivision.mystcraft.age.AgeSummary;
import com.techbucketdivision.mystcraft.blockentity.BookUtil;
import com.techbucketdivision.mystcraft.entity.LinkbookEntity;
import com.techbucketdivision.mystcraft.menu.slot.ToggleHandlerSlot;
import com.techbucketdivision.mystcraft.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Book GUI container (REQUIREMENTS §8.5) for held books, book displays (stand / lectern / receptacle / modifier) and
 * dropped book entities.
 * <p>
 * Slot layout for block / entity sources: 0–26 inventory, 27–35 hotbar (active only while no book is present),
 * 36 book slot at (80,35) (active while no book is present — the plain single-slot screen), 37 book slot at (41,21)
 * (active while a book is present and page 0 is shown). Held books have no slots.
 * <p>
 * Messages client→server: {@code Link}, {@code SetCurrentPage(Index)}. Server→client:
 * {@code LinkPermitted(Permitted, Visited)}, {@code SetCurrentPage(Index)}.
 */
public class BookMenu extends AbstractMystcraftMenu {
    public static final String MSG_LINK = BookView.MSG_LINK;
    public static final String MSG_SET_CURRENT_PAGE = BookView.MSG_SET_CURRENT_PAGE;
    public static final String MSG_LINK_PERMITTED = BookView.MSG_LINK_PERMITTED;
    public static final String MSG_SUMMARY = BookView.MSG_SUMMARY;

    public static final int INV_START = 0;
    public static final int SLOT_BOOK_EMPTY = 36;
    public static final int SLOT_BOOK_PAGE = 37;

    private static final byte KIND_HAND = 0;
    private static final byte KIND_BLOCK = 1;
    private static final byte KIND_ENTITY = 2;

    /** Where the book lives. */
    public interface Source {
        ItemStack getBook();

        boolean stillValid(Player player);

        /** Handler exposing the book slot, or null for held books (no slots). */
        @Nullable ResourceHandler<ItemResource> handler();

        /** {@code IndexModifier} for the handler slot. */
        void set(int index, ItemResource resource, int amount);

        void link(ServerPlayer player);
    }

    private final Source source;
    private final BookView book;
    private int summaryTicks;
    private ItemStack lastBook = ItemStack.EMPTY;

    public BookMenu(int containerId, Inventory inv, Source source) {
        super(ModMenus.BOOK.get(), containerId, inv);
        this.source = source;
        this.book = new BookView(source::getBook, player);
        ResourceHandler<ItemResource> handler = source.handler();
        if (handler != null) {
            addPlayerSlots(8, 84, () -> book.getBook().isEmpty());
            addSlot(new ToggleHandlerSlot(handler, source::set, 0, 80, 35, () -> book.getBook().isEmpty()));
            addSlot(new ToggleHandlerSlot(handler, source::set, 0, 41, 21, () -> !book.getBook().isEmpty() && book.getCurrentPageIndex() == 0));
        }
    }

    // --- opening ------------------------------------------------------------------------------------------------------

    private static Component title() {
        return Component.translatable("container.mystcraft.book");
    }

    public static void openForHeldBook(ServerPlayer player, InteractionHand hand) {
        if (!BookUtil.isLinkingItem(player.getItemInHand(hand))) return;
        MenuProvider provider = new SimpleMenuProvider((id, inv, p) -> new BookMenu(id, inv, new HandSource(p, hand)), title());
        player.openMenu(provider, buf -> {
            buf.writeByte(KIND_HAND);
            buf.writeEnum(hand);
        });
    }

    public static void openForBlock(ServerPlayer player, BlockPos pos) {
        if (!(player.level().getBlockEntity(pos) instanceof BookDisplayBlockEntity be)) return;
        MenuProvider provider = new SimpleMenuProvider((id, inv, p) -> new BookMenu(id, inv, new BlockSource(be)), title());
        player.openMenu(provider, buf -> {
            buf.writeByte(KIND_BLOCK);
            buf.writeBlockPos(pos);
        });
    }

    public static void openForEntity(ServerPlayer player, Entity entity) {
        if (!(entity instanceof LinkbookEntity linkbook)) return;
        MenuProvider provider = new SimpleMenuProvider((id, inv, p) -> new BookMenu(id, inv, new EntitySource(linkbook)), title());
        player.openMenu(provider, buf -> {
            buf.writeByte(KIND_ENTITY);
            buf.writeVarInt(entity.getId());
        });
    }

    public static BookMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        byte kind = buf.readByte();
        Player player = inv.player;
        Source source = switch (kind) {
            case KIND_BLOCK -> {
                BlockPos pos = buf.readBlockPos();
                if (!(player.level().getBlockEntity(pos) instanceof BookDisplayBlockEntity be)) {
                    throw new IllegalStateException("No book display at " + pos);
                }
                yield new BlockSource(be);
            }
            case KIND_ENTITY -> {
                int id = buf.readVarInt();
                if (!(player.level().getEntity(id) instanceof LinkbookEntity linkbook)) {
                    throw new IllegalStateException("No book entity " + id);
                }
                yield new EntitySource(linkbook);
            }
            default -> new HandSource(player, buf.readEnum(InteractionHand.class));
        };
        return new BookMenu(containerId, inv, source);
    }

    // --- container plumbing ---------------------------------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        return source.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (slots.isEmpty()) return ItemStack.EMPTY;
        if (index == SLOT_BOOK_EMPTY || index == SLOT_BOOK_PAGE) {
            return quickMove(player, index, index, index + 1, INV_START, null);
        }
        if (book.getBook().isEmpty()) {
            return quickMove(player, index, SLOT_BOOK_EMPTY, SLOT_BOOK_EMPTY + 1, INV_START, null);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
        if (slotIndex >= slots.size()) return;
        if (slotIndex >= 0 && !slots.get(slotIndex).isActive()) return;
        super.clicked(slotIndex, button, input, player);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!isServer()) return;
        ItemStack current = source.getBook();
        if (!ItemStack.matches(current, lastBook)) {
            lastBook = current.copy();
            book.invalidate();
            book.setCurrentPageIndex(book.getCurrentPageIndex());
            CompoundTag tag = new CompoundTag();
            tag.putInt("Index", book.getCurrentPageIndex());
            sendToClient(MSG_SET_CURRENT_PAGE, tag);
        }
        if (book.needsPermissionSync() && book.computeServerState()) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Permitted", book.isLinkPermitted());
            tag.putBoolean("Visited", book.isTargetWorldVisited());
            sendToClient(MSG_LINK_PERMITTED, tag);
        }
        // the summary page follows the Age (score, active instability effects): refresh once a second
        boolean first = summaryTicks == 0;
        if (summaryTicks++ % 20 == 0 && (book.computeSummary() || first)) {
            CompoundTag tag = new CompoundTag();
            AgeSummary summary = book.getSummary();
            if (summary != null) tag.put("Summary", summary.toTag());
            sendToClient(MSG_SUMMARY, tag);
        }
    }

    @Override
    public void processMessage(Player player, CompoundTag data) {
        switch (messageName(data)) {
            case MSG_LINK_PERMITTED -> book.setPermitted(data.getBooleanOr("Permitted", false), data.getBooleanOr("Visited", false));
            case MSG_SET_CURRENT_PAGE -> book.setCurrentPageIndex(data.getIntOr("Index", 0));
            case MSG_SUMMARY -> book.setSummary(AgeSummary.fromTag(data, "Summary"));
            case MSG_LINK -> {
                if (player instanceof ServerPlayer sp) source.link(sp);
            }
            default -> {
            }
        }
    }

    // --- screen state ------------------------------------------------------------------------------------------------------

    public BookView getBookView() {
        return book;
    }

    public ItemStack getBook() {
        return book.getBook();
    }

    public @Nullable LinkInfo getLinkInfo() {
        return book.getLinkInfo();
    }

    public boolean hasBookSlot() {
        return !slots.isEmpty();
    }

    public int getCurrentPageIndex() {
        return book.getCurrentPageIndex();
    }

    public ItemStack getCurrentPage() {
        return book.getCurrentPage();
    }

    public int getPageCount() {
        return book.getPageCount();
    }

    public @Nullable AgeSummary getSummary() {
        return book.getSummary();
    }

    public boolean isLinkPermitted() {
        return book.isLinkPermitted();
    }

    public boolean isTargetWorldVisited() {
        return book.isTargetWorldVisited();
    }

    public String getBookTitle() {
        return book.getBookTitle();
    }

    public List<String> getBookAuthors() {
        return book.getBookAuthors();
    }

    // --- sources --------------------------------------------------------------------------------------------------------------

    /** A book held in the player's hand. */
    public static final class HandSource implements Source {
        private final Player player;
        private final InteractionHand hand;

        public HandSource(Player player, InteractionHand hand) {
            this.player = player;
            this.hand = hand;
        }

        @Override
        public ItemStack getBook() {
            return player.getItemInHand(hand);
        }

        @Override
        public boolean stillValid(Player player) {
            return BookUtil.isLinkingItem(getBook());
        }

        @Override
        public @Nullable ResourceHandler<ItemResource> handler() {
            return null;
        }

        @Override
        public void set(int index, ItemResource resource, int amount) {
            player.setItemInHand(hand, resource.toStack(amount));
        }

        @Override
        public void link(ServerPlayer player) {
            ItemStack book = getBook();
            if (BookUtil.isLinkingItem(book)) BookUtil.activate(book, player.level(), player);
        }
    }

    /** A book in a display block entity. */
    public static final class BlockSource implements Source {
        private final BookDisplayBlockEntity be;

        public BlockSource(BookDisplayBlockEntity be) {
            this.be = be;
        }

        @Override
        public ItemStack getBook() {
            return be.getBook();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(be, player);
        }

        @Override
        public ResourceHandler<ItemResource> handler() {
            return be.inventory;
        }

        @Override
        public void set(int index, ItemResource resource, int amount) {
            be.inventory.set(index, resource, amount);
        }

        @Override
        public void link(ServerPlayer player) {
            be.link(player);
        }
    }

    /** A dropped book entity; taking the book out kills the entity. */
    public static final class EntitySource implements Source {
        private final LinkbookEntity entity;
        private final ItemStacksResourceHandler handler;

        public EntitySource(LinkbookEntity entity) {
            this.entity = entity;
            this.handler = new ItemStacksResourceHandler(1) {
                @Override
                protected void onContentsChanged(int index, ItemStack previous) {
                    ItemStack now = getResource(0).toStack(getAmountAsInt(0));
                    if (now.isEmpty()) {
                        entity.discard();
                    } else {
                        entity.setBook(now);
                    }
                }
            };
            ItemStack book = entity.getBook();
            if (!book.isEmpty()) handler.set(0, ItemResource.of(book), book.getCount());
        }

        @Override
        public ItemStack getBook() {
            return entity.isRemoved() ? ItemStack.EMPTY : entity.getBook();
        }

        @Override
        public boolean stillValid(Player player) {
            return !entity.isRemoved() && !getBook().isEmpty() && player.distanceToSqr(entity) <= 64.0;
        }

        @Override
        public ResourceHandler<ItemResource> handler() {
            return handler;
        }

        @Override
        public void set(int index, ItemResource resource, int amount) {
            handler.set(index, resource, amount);
        }

        @Override
        public void link(ServerPlayer player) {
            ItemStack book = getBook();
            if (BookUtil.isLinkingItem(book)) BookUtil.activate(book, player.level(), player);
        }
    }
}

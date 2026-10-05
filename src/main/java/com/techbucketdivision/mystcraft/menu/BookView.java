package com.techbucketdivision.mystcraft.menu;

import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.age.AgeSummary;
import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.blockentity.BookUtil;
import com.techbucketdivision.mystcraft.linking.LinkListeners;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * State of the book element (original spec §8.5) shared by {@link BookMenu} and {@link WritingDeskMenu}: current page,
 * page count, link permission and "target visited" flags (both computed on the server and pushed with the
 * {@code LinkPermitted} message).
 */
public final class BookView {
    public static final String MSG_LINK_PERMITTED = "LinkPermitted";
    public static final String MSG_SET_CURRENT_PAGE = "SetCurrentPage";
    public static final String MSG_LINK = "Link";
    public static final String MSG_SUMMARY = "Summary";

    private final Supplier<ItemStack> bookSupplier;
    private final Player player;

    private ItemStack currentPage = ItemStack.EMPTY;
    private int currentPageIndex;
    private int pageCount;
    private @Nullable Boolean permitted;
    private boolean targetVisited;
    private @Nullable AgeSummary summary;

    public BookView(Supplier<ItemStack> bookSupplier, Player player) {
        this.bookSupplier = bookSupplier;
        this.player = player;
    }

    /** The linking item being viewed, or empty. */
    public ItemStack getBook() {
        ItemStack stack = bookSupplier.get();
        return BookUtil.isLinkingItem(stack) ? stack : ItemStack.EMPTY;
    }

    public @Nullable LinkInfo getLinkInfo() {
        return BookUtil.linkInfo(getBook());
    }

    public @Nullable List<ItemStack> getPageList() {
        ItemStack book = getBook();
        if (book.isEmpty() || !(book.getItem() instanceof ItemBehaviours.PageProvider p)) return null;
        return p.getPageList(player, book);
    }

    public void setCurrentPageIndex(int index) {
        currentPage = ItemStack.EMPTY;
        if (index < 0) index = 0;
        List<ItemStack> pages = getPageList();
        if (pages == null) {
            index = 0;
            pageCount = 0;
        } else {
            pageCount = pages.size();
            if (index >= pageCount) index = pageCount;
            else currentPage = pages.get(index);
        }
        currentPageIndex = index;
    }

    /** Page item at the current index (empty for the cover / link page). */
    public ItemStack getCurrentPage() {
        if (currentPage.isEmpty()) setCurrentPageIndex(currentPageIndex);
        return currentPage;
    }

    public int getCurrentPageIndex() {
        return currentPageIndex;
    }

    public int getPageCount() {
        return pageCount;
    }

    public String getBookTitle() {
        return BookUtil.title(getBook());
    }

    public List<String> getBookAuthors() {
        return BookUtil.authors(getBook());
    }

    /** Summary page of a bound Descriptive Book (client: as synced; server: as last computed), or {@code null}. */
    public @Nullable AgeSummary getSummary() {
        return summary;
    }

    public void setSummary(@Nullable AgeSummary summary) {
        this.summary = summary;
    }

    /** Server-side: recomputes the summary from the Age; returns whether it changed. */
    public boolean computeSummary() {
        MinecraftServer server = player.level().getServer();
        AgeSummary fresh = server == null ? null : AgeSummary.of(server, getBook());
        boolean changed = !java.util.Objects.equals(fresh, summary);
        summary = fresh;
        return changed;
    }

    /** Whether the view currently shows the summary page (one past the last page of a bound book). */
    public boolean isOnSummaryPage() {
        return summary != null && pageCount > 0 && currentPageIndex >= pageCount;
    }

    public boolean isLinkPermitted() {
        if (getLinkInfo() == null) permitted = null;
        return permitted != null && permitted;
    }

    public boolean isTargetWorldVisited() {
        return targetVisited;
    }

    /** Invalidates the cached permission (the server recomputes and resyncs). */
    public void invalidate() {
        permitted = null;
        currentPage = ItemStack.EMPTY;
    }

    public boolean needsPermissionSync() {
        return permitted == null;
    }

    /** Client-side: apply the synced flags. */
    public void setPermitted(boolean permitted, boolean visited) {
        this.permitted = permitted;
        this.targetVisited = visited;
    }

    /** Server-side: recompute and cache. Returns false when there is no book. */
    public boolean computeServerState() {
        LinkInfo info = getLinkInfo();
        if (info == null) {
            permitted = null;
            targetVisited = false;
            return false;
        }
        boolean allowed;
        if (BookUtil.isDescriptiveBook(getBook()) && !info.isBound()) {
            allowed = true; // new Descriptive Book: first link creates the Age
        } else {
            allowed = player.level() instanceof ServerLevel level && LinkListeners.isLinkPermitted(level, player, info);
        }
        permitted = allowed;
        targetVisited = computeVisited(info);
        return true;
    }

    private boolean computeVisited(LinkInfo info) {
        ResourceKey<Level> key = info.dimension().orElse(null);
        MinecraftServer server = player.level().getServer();
        if (key == null || server == null) return false;
        if (AgeManager.isAge(key)) {
            AgeData data = AgeManager.get(server, key);
            return data != null && data.visited();
        }
        return server.getLevel(key) != null;
    }
}

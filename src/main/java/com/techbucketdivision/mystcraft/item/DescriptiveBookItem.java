package com.techbucketdivision.mystcraft.item;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.item.component.BookHealth;
import com.techbucketdivision.mystcraft.item.component.PageList;
import com.techbucketdivision.mystcraft.registry.ModDataComponents;
import com.techbucketdivision.mystcraft.registry.ModItems;
import com.techbucketdivision.mystcraft.symbol.grammar.Grammar;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Descriptive Book (REQUIREMENTS §2.3.1): pages + authors + link to an Age created on first link. */
public class DescriptiveBookItem extends LinkingItem implements ItemBehaviours.Writable, ItemBehaviours.PageProvider,
        ItemBehaviours.OnLoadable {

    public DescriptiveBookItem(Item.Properties properties) {
        super(properties);
    }

    // --- factories ---------------------------------------------------------------------------------------------

    /** Book Binder output: pages, author and title; link-panel properties of page 0 become link flags. */
    public static ItemStack create(Player player, List<ItemStack> pages, String title) {
        ItemStack book = new ItemStack(ModItems.DESCRIPTIVE_BOOK.get());
        LinkInfo info = LinkInfo.EMPTY.withDisplayName(title).withFlag(LinkProperty.GENERATE_PLATFORM, true);
        if (!pages.isEmpty() && PageItem.isLinkPanel(pages.getFirst())) {
            info = info.withFlags(PageItem.getLinkProperties(pages.getFirst()));
        }
        setLinkInfo(book, info);
        setPages(book, pages);
        book.set(ModDataComponents.BOOK_HEALTH.get(), BookHealth.FULL);
        addAuthor(book, player.getPlainTextName());
        return book;
    }

    /** Command / creative: a book bound to an existing Age. */
    public static void initializeForAge(ItemStack book, AgeData data) {
        LinkInfo info = LinkInfo.EMPTY
                .withDimension(data.levelKey())
                .withTargetUuid(data.uuid())
                .withDisplayName(data.name())
                .withFlag(LinkProperty.GENERATE_PLATFORM, true)
                .withProp(LinkProperty.PROP_SEED, Long.toString(data.seed()));
        setLinkInfo(book, info);
        setPages(book, data.pages());
        book.set(ModDataComponents.AUTHORS.get(), data.authors());
        if (!book.has(ModDataComponents.BOOK_HEALTH.get())) {
            book.set(ModDataComponents.BOOK_HEALTH.get(), BookHealth.FULL);
        }
    }

    public static boolean isDescriptiveBook(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof DescriptiveBookItem;
    }

    /** An unbound book whose first page is a link panel: always "link permitted" in GUIs (creates its Age on use). */
    public static boolean isNewAgebook(ItemStack stack) {
        if (!isDescriptiveBook(stack) || !hasLinkInfo(stack)) return false;
        if (getLinkInfo(stack).isBound()) return false;
        List<ItemStack> pages = getPages(stack);
        return !pages.isEmpty() && PageItem.isLinkPanel(pages.getFirst());
    }

    // --- pages -------------------------------------------------------------------------------------------------

    public static List<ItemStack> getPages(ItemStack book) {
        return book.getOrDefault(ModDataComponents.PAGES.get(), PageList.EMPTY).copies();
    }

    public static void setPages(ItemStack book, List<ItemStack> pages) {
        book.set(ModDataComponents.PAGES.get(), new PageList(pages));
    }

    private static void addAuthor(ItemStack book, String name) {
        List<String> authors = new ArrayList<>(book.getOrDefault(ModDataComponents.AUTHORS.get(), List.of()));
        if (!authors.contains(name)) {
            authors.add(name);
            book.set(ModDataComponents.AUTHORS.get(), authors);
        }
    }

    /** Symbol ids of every symbol page, in page order (blank pages and link panels skipped). */
    public static List<Identifier> writtenSymbols(List<ItemStack> pages) {
        List<Identifier> out = new ArrayList<>();
        for (ItemStack page : pages) {
            Identifier id = PageItem.getSymbolId(page);
            if (id != null) out.add(id);
        }
        return out;
    }

    private List<ItemStack> defaultPages(@Nullable MinecraftServer server, ItemStack book) {
        AgeData data = getAgeData(server, book);
        if (data != null && !data.pages().isEmpty()) return data.pages();
        return List.of(PageItem.createLinkPanel());
    }

    // --- lifecycle ---------------------------------------------------------------------------------------------

    @Override
    protected void initialize(@Nullable ServerLevel level, ItemStack stack, @Nullable Entity entity) {
        if (!hasLinkInfo(stack)) {
            setLinkInfo(stack, LinkInfo.EMPTY.withFlag(LinkProperty.GENERATE_PLATFORM, true));
        }
        if (!stack.has(ModDataComponents.PAGES.get())) {
            setPages(stack, defaultPages(level == null ? null : level.getServer(), stack));
        }
    }

    @Override
    public void validate(@Nullable ServerLevel level, ItemStack stack, @Nullable Entity entity) {
        super.validate(level, stack, entity);
        if (!stack.has(ModDataComponents.PAGES.get())) {
            setPages(stack, defaultPages(level == null ? null : level.getServer(), stack));
        }
    }

    @Override
    public void onLoad(ItemStack stack) {
        if (stack.has(ModDataComponents.PAGES.get())) {
            setPages(stack, PageItem.remapAll(getPages(stack)));
        }
        initialize(null, stack, null);
        validate(null, stack, null);
        if (getPages(stack).isEmpty()) setPages(stack, defaultPages(null, stack));
    }

    // --- linking -----------------------------------------------------------------------------------------------

    @Override
    public void activate(ItemStack stack, ServerLevel level, Entity entity) {
        if (level.isClientSide()) return;
        MinecraftServer server = level.getServer();
        if (server != null) checkFirstLink(stack, server);
        super.activate(stack, level, entity);
    }

    /**
     * First link: creates the Age, binds the book, copies title/seed/pages/authors into the Age and expands the
     * written symbols through the grammar (REQUIREMENTS §2.3.1, §4.4).
     */
    public static void checkFirstLink(ItemStack stack, MinecraftServer server) {
        if (!isDescriptiveBook(stack) || !hasLinkInfo(stack)) return;
        LinkInfo info = getLinkInfo(stack);
        if (info.isBound()) return;

        AgeData data = AgeManager.createAge(server);
        info = info.withDimension(data.levelKey()).withTargetUuid(data.uuid());
        if (LinkInfo.DEFAULT_NAME.equals(info.displayName()) || info.displayName().isBlank()) {
            // Untitled book: the book takes the Age's generated name ("Age N") instead of naming the Age "???".
            info = info.withDisplayName(data.name());
        } else {
            data.setName(info.displayName());
        }

        String seedProp = info.prop(LinkProperty.PROP_SEED);
        if (seedProp != null) {
            try {
                data.setSeed(Long.parseLong(seedProp));
            } catch (NumberFormatException e) {
                Mystcraft.LOGGER.warn("Ignoring invalid seed '{}' on descriptive book", seedProp);
                info = info.withProp(LinkProperty.PROP_SEED, Long.toString(data.seed()));
            }
        } else {
            info = info.withProp(LinkProperty.PROP_SEED, Long.toString(data.seed()));
        }
        setLinkInfo(stack, info);

        List<ItemStack> pages = PageItem.remapAll(getPages(stack));
        setPages(stack, pages);
        data.setPages(pages);
        data.setAuthors(stack.getOrDefault(ModDataComponents.AUTHORS.get(), List.of()));

        List<Identifier> written = writtenSymbols(pages);
        List<Identifier> expanded = Grammar.expandAge(written, RandomSource.create(data.seed()));
        data.setSymbols(expanded);
        Mystcraft.LOGGER.info("Bound descriptive book '{}' to Age {} ({} written symbols -> {} total)", info.displayName(),
                data.uuid(), written.size(), expanded.size());
    }

    // --- age access --------------------------------------------------------------------------------------------

    public static @Nullable AgeData getAgeData(@Nullable MinecraftServer server, ItemStack stack) {
        if (server == null || !hasLinkInfo(stack)) return null;
        LinkInfo info = getLinkInfo(stack);
        if (info.dimension().isEmpty()) return null;
        AgeData data = AgeManager.get(server, info.dimension().get());
        if (data == null) return null;
        UUID target = info.targetUuid().orElse(null);
        if (target != null && !target.equals(data.uuid())) return null;
        return data;
    }

    private static boolean isVisited(@Nullable MinecraftServer server, ItemStack stack) {
        AgeData data = getAgeData(server, stack);
        return data != null && data.visited();
    }

    /** Renames the book and, if bound, its Age. */
    public static void rename(ItemStack stack, @Nullable MinecraftServer server, String name) {
        setLinkInfo(stack, getLinkInfo(stack).withDisplayName(name));
        AgeData data = getAgeData(server, stack);
        if (data != null) data.setName(name);
    }

    /** Link Modifier: sets the seed (only effective on the Age before it was visited). */
    public static void setSeed(ItemStack stack, @Nullable MinecraftServer server, long seed) {
        AgeData data = getAgeData(server, stack);
        if (data != null) {
            data.setSeed(seed);
            setLinkInfo(stack, getLinkInfo(stack).withProp(LinkProperty.PROP_SEED, Long.toString(data.seed())));
        } else {
            setLinkInfo(stack, getLinkInfo(stack).withProp(LinkProperty.PROP_SEED, Long.toString(seed)));
        }
    }

    @Override
    public void setDisplayName(Player player, ItemStack stack, String name) {
        rename(stack, player.level().getServer(), name);
    }

    // --- behaviours --------------------------------------------------------------------------------------------

    @Override
    public boolean writeSymbol(Player player, ItemStack stack, AgeSymbol symbol) {
        if (player.level().isClientSide()) return false;
        if (isVisited(player.level().getServer(), stack)) return false;
        if (!hasLinkInfo(stack)) return false;
        List<ItemStack> pages = getPages(stack);
        for (int i = 0; i < pages.size(); i++) {
            if (PageItem.isBlank(pages.get(i))) {
                pages.set(i, PageItem.createSymbolPage(symbol));
                setPages(stack, pages);
                addAuthor(stack, player.getPlainTextName());
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ItemStack> getPageList(@Nullable Player player, ItemStack stack) {
        return getPages(stack);
    }
}

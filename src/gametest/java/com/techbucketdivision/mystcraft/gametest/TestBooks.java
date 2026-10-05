package com.tbd.mystcraft.gametest;

import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.api.linking.LinkProperty;
import com.tbd.mystcraft.item.DescriptiveBookItem;
import com.tbd.mystcraft.item.LinkingItem;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.registry.ModItems;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Book factories shared by the tests. */
final class TestBooks {
    private TestBooks() {}

    /** An unbound Descriptive Book (link panel + the given symbol pages), as a player would get from the Book Binder. */
    static ItemStack unboundDescriptiveBook(String title, Identifier... symbols) {
        ItemStack book = new ItemStack(ModItems.DESCRIPTIVE_BOOK.get());
        List<ItemStack> pages = new ArrayList<>();
        pages.add(PageItem.createLinkPanel());
        for (Identifier symbol : symbols) pages.add(PageItem.createSymbolPage(symbol));
        LinkingItem.setLinkInfo(book, LinkInfo.EMPTY.withDisplayName(title).withFlag(LinkProperty.GENERATE_PLATFORM, true));
        DescriptiveBookItem.setPages(book, pages);
        return book;
    }

    /** As {@link #unboundDescriptiveBook(String, Identifier...)} with a fixed Age seed (deterministic blueprint and start time). */
    static ItemStack unboundDescriptiveBook(String title, long seed, Identifier... symbols) {
        ItemStack book = unboundDescriptiveBook(title, symbols);
        LinkingItem.setLinkInfo(book, LinkingItem.getLinkInfo(book).withProp(LinkProperty.PROP_SEED, Long.toString(seed)));
        return book;
    }

    /** Binds the book to a fresh Age (what the first link does) and returns the Age data. */
    static AgeData bind(ItemStack book, MinecraftServer server) {
        DescriptiveBookItem.checkFirstLink(book, server);
        AgeData data = DescriptiveBookItem.getAgeData(server, book);
        if (data == null) throw new IllegalStateException("book did not bind to an Age");
        return data;
    }
}

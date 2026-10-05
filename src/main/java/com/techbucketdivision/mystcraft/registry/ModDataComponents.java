package com.techbucketdivision.mystcraft.registry;

import com.mojang.serialization.Codec;
import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.item.component.BookHealth;
import com.techbucketdivision.mystcraft.item.component.PageList;
import com.techbucketdivision.mystcraft.item.component.SlotPages;
import com.techbucketdivision.mystcraft.item.component.SymbolPage;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Set;

/**
 * Item data components (replace the original NBT layouts, original spec §2).
 * <ul>
 * <li>Page: {@link #SYMBOL} (symbol + modifiers + discovered; absent = blank) or {@link #LINK_PANEL} (set of properties; present = link panel)</li>
 * <li>Books: {@link #LINK_INFO}, {@link #BOOK_HEALTH}, {@link #PAGES} (descriptive book), {@link #AUTHORS}</li>
 * <li>Folder: {@link #SLOT_PAGES}, {@link #ITEM_TITLE}; Portfolio: {@link #PAGES}, {@link #ITEM_TITLE}</li>
 * </ul>
 */
public final class ModDataComponents {
    private ModDataComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Mystcraft.MOD_ID);

    /** Symbol page: primary symbol, attached modifiers, discovered flag. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SymbolPage>> SYMBOL = COMPONENTS.registerComponentType("symbol",
            b -> b.persistent(SymbolPage.CODEC).networkSynchronized(SymbolPage.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Set<LinkProperty>>> LINK_PANEL = COMPONENTS.registerComponentType("link_panel",
            b -> b.persistent(LinkProperty.CODEC.listOf().xmap(l -> (Set<LinkProperty>) new java.util.LinkedHashSet<>(l), List::copyOf))
                    .networkSynchronized(LinkProperty.STREAM_CODEC.apply(ByteBufCodecs.list()).map(l -> (Set<LinkProperty>) new java.util.LinkedHashSet<>(l), List::copyOf)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LinkInfo>> LINK_INFO = COMPONENTS.registerComponentType("link_info",
            b -> b.persistent(LinkInfo.CODEC).networkSynchronized(LinkInfo.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BookHealth>> BOOK_HEALTH = COMPONENTS.registerComponentType("book_health",
            b -> b.persistent(BookHealth.CODEC).networkSynchronized(BookHealth.STREAM_CODEC));

    /** Ordered page list (descriptive books, portfolios). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PageList>> PAGES = COMPONENTS.registerComponentType("pages",
            b -> b.persistent(PageList.CODEC).networkSynchronized(PageList.STREAM_CODEC));

    /** Sparse slot → page map (collation folders). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SlotPages>> SLOT_PAGES = COMPONENTS.registerComponentType("slot_pages",
            b -> b.persistent(SlotPages.CODEC).networkSynchronized(SlotPages.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> AUTHORS = COMPONENTS.registerComponentType("authors",
            b -> b.persistent(Codec.STRING.listOf()).networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())));

    /** User-given title for folders/portfolios (books use LinkInfo.displayName). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ITEM_TITLE = COMPONENTS.registerComponentType("item_title",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** Marker on unlinked books carrying the panel properties they were crafted with. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> ORIGIN = COMPONENTS.registerComponentType("origin",
            b -> b.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));
}

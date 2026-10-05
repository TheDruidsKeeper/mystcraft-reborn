package com.tbd.mystcraft.registry;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.api.linking.LinkProperty;
import com.tbd.mystcraft.api.symbol.AgeSymbol;
import com.tbd.mystcraft.block.DecayType;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.symbol.SymbolRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Creative tabs: {@code mystcraft} (blocks/items) and {@code mystcraft_pages} (every page, searchable). */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mystcraft.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mystcraft.main"))
            .icon(() -> ModItems.DESCRIPTIVE_BOOK.get().getDefaultInstance())
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems((params, out) -> {
                out.accept(ModItems.DESCRIPTIVE_BOOK.get());
                out.accept(ModItems.LINKING_BOOK.get());
                out.accept(ModItems.UNLINKED_BOOK.get());
                out.accept(ModItems.SEALED_NOTEBOOK.get());
                out.accept(ModItems.COLLATION_FOLDER.get());
                out.accept(ModItems.INK_VIAL.get());
                out.accept(ModItems.BLACK_INK_BUCKET.get());
                out.accept(ModItems.WRITING_DESK.get());
                out.accept(ModItems.WRITING_DESK_BACKBOARD.get());
                out.accept(ModItems.SCHOLARS_WRITING_DESK.get());
                out.accept(ModItems.INK_MIXER.get());
                out.accept(ModItems.BOOK_BINDER.get());
                out.accept(ModItems.BOOKSTAND.get());
                out.accept(ModItems.LECTERN.get());
                out.accept(ModItems.BOOK_RECEPTACLE.get());
                out.accept(ModItems.CRYSTAL.get());
                out.accept(ModItems.LINK_MODIFIER.get());
                out.accept(ModItems.WARDED_DOOR.get());
                out.accept(ModItems.SYMBOL_ALTAR.get());
                out.accept(ModItems.OFFERING_PEDESTAL.get());
                out.accept(ModItems.SEQUENCE_DIAL.get());
                out.accept(ModItems.FACILITY_CACHE.get());
                out.accept(ModItems.LINK_PORTAL.get());
                out.accept(ModItems.STAR_FISSURE.get());
                for (DecayType t : DecayType.values()) out.accept(ModItems.decay(t));
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PAGES = TABS.register("pages", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mystcraft.pages"))
            .icon(() -> ModItems.PAGE.get().getDefaultInstance())
            .withTabsAfter(MAIN.getKey())
            .withSearchBar()
            .displayItems((params, out) -> {
                out.accept(PageItem.createLinkPanel(Set.of()));
                List<LinkProperty> props = new ArrayList<>(LinkProperty.all().values());
                props.sort(Comparator.comparing(LinkProperty::name));
                for (LinkProperty p : props) {
                    if (p.inkable() && p != LinkProperty.RELATIVE) out.accept(PageItem.createLinkPanel(Set.of(p)));
                }
                List<AgeSymbol> symbols = new ArrayList<>(SymbolRegistry.all());
                symbols.sort(Comparator.comparing(s -> s.displayName().getString()));
                for (AgeSymbol s : symbols) out.accept(PageItem.createSymbolPage(s));
            }).build());
}

package com.tbd.mystcraft.registry;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.menu.ArchivistShopMenu;
import com.tbd.mystcraft.menu.BookBinderMenu;
import com.tbd.mystcraft.menu.BookMenu;
import com.tbd.mystcraft.menu.FolderMenu;
import com.tbd.mystcraft.menu.InkMixerMenu;
import com.tbd.mystcraft.menu.LinkModifierMenu;
import com.tbd.mystcraft.menu.WritingDeskMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu types. Every menu has a client factory {@code (int containerId, Inventory inv, RegistryFriendlyByteBuf data)}
 * exposed as a static {@code fromNetwork} method.
 */
public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Mystcraft.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<WritingDeskMenu>> WRITING_DESK = MENUS.register("writing_desk",
            () -> IMenuTypeExtension.create(WritingDeskMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<BookBinderMenu>> BOOK_BINDER = MENUS.register("book_binder",
            () -> IMenuTypeExtension.create(BookBinderMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<InkMixerMenu>> INK_MIXER = MENUS.register("ink_mixer",
            () -> IMenuTypeExtension.create(InkMixerMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<LinkModifierMenu>> LINK_MODIFIER = MENUS.register("link_modifier",
            () -> IMenuTypeExtension.create(LinkModifierMenu::fromNetwork));
    /** Book GUI for held books, displays, receptacles and book entities. */
    public static final DeferredHolder<MenuType<?>, MenuType<BookMenu>> BOOK = MENUS.register("book",
            () -> IMenuTypeExtension.create(BookMenu::fromNetwork));
    /** Folder and portfolio GUI. */
    public static final DeferredHolder<MenuType<?>, MenuType<FolderMenu>> FOLDER = MENUS.register("folder",
            () -> IMenuTypeExtension.create(FolderMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<ArchivistShopMenu>> ARCHIVIST_SHOP = MENUS.register("archivist_shop",
            () -> IMenuTypeExtension.create(ArchivistShopMenu::fromNetwork));
}

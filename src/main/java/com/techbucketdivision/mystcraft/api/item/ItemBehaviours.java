package com.techbucketdivision.mystcraft.api.item;

import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Item behaviour interfaces used by the Writing Desk, Book Binder, folders and portals. Items implement the ones that
 * apply (see original spec §2.10). All methods take the stack because items are stateless.
 */
public final class ItemBehaviours {
    private ItemBehaviours() {}

    /** The desk can write a symbol into this item. */
    public interface Writable {
        boolean writeSymbol(Player player, ItemStack stack, AgeSymbol symbol);
    }

    /** The desk / link modifier can rename this item. */
    public interface Renameable {
        @Nullable String getDisplayName(ItemStack stack);

        void setDisplayName(Player player, ItemStack stack, String name);
    }

    /** Provides the list of pages it contains (read-only copies). */
    public interface PageProvider {
        List<ItemStack> getPageList(@Nullable Player player, ItemStack stack);
    }

    /** Can accept a page (folder). Returns the remainder (empty if fully accepted). */
    public interface PageAcceptor {
        ItemStack addPage(@Nullable Player player, ItemStack stack, ItemStack page);
    }

    /** Ordered page editing (folder). */
    public interface OrderablePageProvider extends PageProvider {
        /** Replaces the page at index (index may equal size to append). Returns the previous page. */
        ItemStack setPage(@Nullable Player player, ItemStack stack, ItemStack page, int index);

        ItemStack removePage(@Nullable Player player, ItemStack stack, int index);

        /** Highest occupied slot index, or -1. */
        int getLargestPageIndex(ItemStack stack);
    }

    /** Placed in a Book Receptacle to power a crystal portal. */
    public interface PortalActivator {
        void onPortalCollision(ItemStack stack, Level level, Entity entity, BlockPos portalPos);

        /** 0xRRGGBB portal tint. */
        int getPortalColor(ItemStack stack, Level level);
    }

    /** Migration hook when a stack is loaded from disk (symbol remapping). */
    public interface OnLoadable {
        void onLoad(ItemStack stack);
    }
}

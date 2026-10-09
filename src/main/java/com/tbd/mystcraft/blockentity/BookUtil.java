package com.tbd.mystcraft.blockentity;

import com.tbd.mystcraft.api.item.ItemBehaviours;
import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.item.DescriptiveBookItem;
import com.tbd.mystcraft.item.LinkingItem;
import com.tbd.mystcraft.registry.ModDataComponents;
import com.tbd.mystcraft.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Small helpers shared by block entities and menus for dealing with linking items. Link info is read straight from
 * the {@code LINK_INFO} data component so this package does not depend on the exact shape of the item classes.
 */
public final class BookUtil {
    private BookUtil() {}

    public static boolean isLinkingItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof LinkingItem;
    }

    public static boolean isDescriptiveBook(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof DescriptiveBookItem;
    }

    /** Link info stored on a linking item, or {@code null} if the stack is not a linking item. */
    public static @Nullable LinkInfo linkInfo(ItemStack stack) {
        if (!isLinkingItem(stack)) return null;
        LinkInfo info = stack.get(ModDataComponents.LINK_INFO.get());
        return info == null ? LinkInfo.EMPTY : info;
    }

    public static void setLinkInfo(ItemStack stack, LinkInfo info) {
        stack.set(ModDataComponents.LINK_INFO.get(), info);
    }

    /** Activates (links with) a linking item. Server only. */
    public static void activate(ItemStack book, ServerLevel level, Entity entity) {
        if (book.getItem() instanceof LinkingItem item) {
            item.activate(book, level, entity);
        }
    }

    /** Display title of a book / renameable item ("" when none). */
    public static String title(ItemStack stack) {
        if (stack.isEmpty()) return "";
        if (stack.getItem() instanceof ItemBehaviours.Renameable r) {
            String name = r.getDisplayName(stack);
            return name == null ? "" : name;
        }
        LinkInfo info = linkInfo(stack);
        return info == null ? "" : info.displayName();
    }

    public static List<String> authors(ItemStack stack) {
        List<String> authors = stack.get(ModDataComponents.AUTHORS.get());
        return authors == null ? List.of() : authors;
    }

    /** Whether the stack is a fluid container holding a permitted ink. */
    public static boolean isInkContainer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        FluidStack contained = FluidUtil.getFirstStackContained(stack);
        return !contained.isEmpty() && ModFluids.isInk(contained.getFluid());
    }

    /** Drops a stack in the world (no-op on the client or for empty stacks). */
    public static void drop(Level level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || level.isClientSide()) return;
        Block.popResource(level, pos, stack);
    }

    /** Gives a stack to a player, dropping what does not fit. */
    public static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        player.getInventory().placeItemBackInInventory(stack);
    }
}

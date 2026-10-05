package com.tbd.mystcraft.item;

import com.tbd.mystcraft.api.linking.LinkProperty;
import com.tbd.mystcraft.registry.ModDataComponents;
import com.tbd.mystcraft.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Unlinked Link Book (original spec §2.3.3): carries the link-panel properties it was crafted with in
 * {@link ModDataComponents#LINK_PANEL}; right-click binds it to the player's position.
 */
public class UnlinkedBookItem extends Item {

    public UnlinkedBookItem(Item.Properties properties) {
        super(properties);
    }

    /** Recipe result: copies the panel's properties onto the book. */
    public static ItemStack createFrom(ItemStack linkPanel) {
        ItemStack book = new ItemStack(ModItems.UNLINKED_BOOK.get());
        book.set(ModDataComponents.LINK_PANEL.get(), new LinkedHashSet<>(PageItem.getLinkProperties(linkPanel)));
        return book;
    }

    public static Set<LinkProperty> getProperties(ItemStack stack) {
        Set<LinkProperty> props = stack.get(ModDataComponents.LINK_PANEL.get());
        return props == null ? Set.of() : Set.copyOf(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        for (LinkProperty property : getProperties(stack)) {
            builder.accept(Component.translatable(property.descriptionId()));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide() || held.getCount() != 1) return InteractionResult.PASS;
        ItemStack book = LinkingBookItem.createAt(player);
        LinkingItem.setLinkInfo(book, LinkingItem.getLinkInfo(book).withFlags(getProperties(held)));
        player.setItemInHand(hand, book);
        return InteractionResult.SUCCESS_SERVER;
    }
}

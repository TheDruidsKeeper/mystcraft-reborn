package com.techbucketdivision.mystcraft.item;

import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.item.component.BookHealth;
import com.techbucketdivision.mystcraft.registry.ModDataComponents;
import com.techbucketdivision.mystcraft.registry.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Linking Book (original spec §2.3.2): links back to the position where it was created. */
public class LinkingBookItem extends LinkingItem implements ItemBehaviours.PageProvider {

    public LinkingBookItem(Item.Properties properties) {
        super(properties);
    }

    /** A linking book bound to the entity's current position (server side). */
    public static ItemStack createAt(Entity entity) {
        ItemStack book = new ItemStack(ModItems.LINKING_BOOK.get());
        setLinkInfo(book, linkInfoAt(entity));
        book.set(ModDataComponents.BOOK_HEALTH.get(), BookHealth.FULL);
        return book;
    }

    /** Link info for the entity's position: age name (or dimension path) as display name, age UUID as target. */
    public static LinkInfo linkInfoAt(Entity entity) {
        MinecraftServer server = entity.level().getServer();
        UUID uuid = null;
        String name = entity.level().dimension().identifier().getPath();
        if (server != null) {
            AgeData data = AgeManager.get(server, entity.level().dimension());
            if (data != null) {
                uuid = data.uuid();
                name = data.name();
            }
        }
        return LinkInfo.fromPosition(entity, name, uuid);
    }

    @Override
    protected void initialize(@Nullable ServerLevel level, ItemStack stack, @Nullable Entity entity) {
        if (entity != null && level != null) {
            setLinkInfo(stack, linkInfoAt(entity));
        } else if (!hasLinkInfo(stack)) {
            setLinkInfo(stack, LinkInfo.EMPTY);
        }
    }

    /** Display only: a link panel carrying the book's flags. */
    @Override
    public List<ItemStack> getPageList(@Nullable Player player, ItemStack stack) {
        return List.of(PageItem.createLinkPanel(getLinkInfo(stack).flags()));
    }
}

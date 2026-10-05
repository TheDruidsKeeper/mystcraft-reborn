package com.techbucketdivision.mystcraft.menu;

import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.blockentity.BookUtil;
import com.techbucketdivision.mystcraft.blockentity.LinkModifierBlockEntity;
import com.techbucketdivision.mystcraft.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import org.jspecify.annotations.Nullable;

/**
 * Link Modifier container (original spec §8.4). Slot 0 book at (80,35), then the standard inventory.
 * Messages client→server: {@code SetTitle(Title)}, {@code SetFlag(Flag, Value)}, {@code SetSeed(Seed)} (empty string
 * clears), {@code RecycleDim}. Server→client: {@code LinkDead(Dead)}.
 */
public class LinkModifierMenu extends AbstractMystcraftMenu {
    public static final String MSG_SET_TITLE = "SetTitle";
    public static final String MSG_SET_FLAG = "SetFlag";
    public static final String MSG_SET_SEED = "SetSeed";
    public static final String MSG_RECYCLE_DIM = "RecycleDim";
    public static final String MSG_LINK_DEAD = "LinkDead";

    public static final int SLOT_BOOK = 0;
    public static final int INV_START = 1;
    public static final int MAX_TITLE = 21;

    private final LinkModifierBlockEntity modifier;
    private ItemStack lastBook = ItemStack.EMPTY;
    private @Nullable Boolean cachedDead;

    public LinkModifierMenu(int containerId, Inventory inv, LinkModifierBlockEntity modifier) {
        super(ModMenus.LINK_MODIFIER.get(), containerId, inv);
        this.modifier = modifier;
        addSlot(new ResourceHandlerSlot(modifier.inventory, modifier.inventory::set, 0, 80, 35));
        addStandardInventorySlots(inv, 8, 84);
    }

    public static LinkModifierMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (!(inv.player.level().getBlockEntity(pos) instanceof LinkModifierBlockEntity be)) {
            throw new IllegalStateException("No link modifier at " + pos);
        }
        return new LinkModifierMenu(containerId, inv, be);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(modifier, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return quickMove(player, index, 0, INV_START, INV_START, null);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!isServer()) return;
        ItemStack book = modifier.getBook();
        if (!ItemStack.matches(book, lastBook)) {
            lastBook = book.copy();
            cachedDead = null;
        }
        if (cachedDead == null) {
            cachedDead = modifier.isLinkDimensionDead();
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Dead", cachedDead);
            sendToClient(MSG_LINK_DEAD, tag);
        }
    }

    @Override
    public void processMessage(Player player, CompoundTag data) {
        switch (messageName(data)) {
            case MSG_LINK_DEAD -> cachedDead = data.getBooleanOr("Dead", false);
            case MSG_RECYCLE_DIM -> {
                if (isServer()) modifier.recycleDimension();
                cachedDead = null;
            }
            case MSG_SET_FLAG -> {
                LinkProperty property = LinkProperty.getOrCreate(data.getStringOr("Flag", ""));
                modifier.setLinkFlag(property, data.getBooleanOr("Value", false));
            }
            case MSG_SET_SEED -> {
                String seed = data.getStringOr("Seed", "");
                if (!modifier.hasSeed()) return;
                if (seed.isEmpty()) {
                    modifier.setLinkProperty(LinkProperty.PROP_SEED, null);
                } else {
                    try {
                        long parsed = Long.parseLong(seed.trim());
                        modifier.setLinkProperty(LinkProperty.PROP_SEED, Long.toString(parsed));
                    } catch (NumberFormatException ignored) {
                        // keep the previous seed
                    }
                }
            }
            case MSG_SET_TITLE -> {
                String title = data.getStringOr("Title", "");
                if (title.length() > MAX_TITLE) title = title.substring(0, MAX_TITLE);
                modifier.setBookTitle(player, title);
            }
            default -> {
            }
        }
    }

    // --- screen state ------------------------------------------------------------------------------------------------

    public LinkModifierBlockEntity getModifier() {
        return modifier;
    }

    public ItemStack getBook() {
        return modifier.getBook();
    }

    public String getBookTitle() {
        return BookUtil.title(modifier.getBook());
    }

    public boolean getLinkFlag(LinkProperty property) {
        return modifier.getLinkFlag(property);
    }

    /** Target dimension id ("" when unbound). */
    public String getLinkDimensionId() {
        return modifier.getLinkDimensionString();
    }

    /** Seed text ("" when none). */
    public String getItemSeed() {
        return modifier.getSeedString();
    }

    /** True only for Descriptive Books (shows the seed field and kill buttons). */
    public boolean hasItemSeed() {
        return modifier.hasSeed();
    }

    public boolean isLinkDead() {
        return cachedDead != null && cachedDead && !modifier.getBook().isEmpty();
    }
}

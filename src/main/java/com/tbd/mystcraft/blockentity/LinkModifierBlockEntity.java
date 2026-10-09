package com.tbd.mystcraft.blockentity;

import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.age.AgeManager;
import com.tbd.mystcraft.api.item.ItemBehaviours;
import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.api.linking.LinkProperty;
import com.tbd.mystcraft.menu.LinkModifierMenu;
import com.tbd.mystcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Link Modifier (original spec §3.9): edits link flags, seed and title of the held book and can mark its Age dead.
 */
public class LinkModifierBlockEntity extends BookDisplayBlockEntity implements MenuProvider {
    public LinkModifierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LINK_MODIFIER.get(), pos, state);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return BookUtil.isLinkingItem(stack);
    }

    private @Nullable LinkInfo info() {
        return BookUtil.linkInfo(getBook());
    }

    private void update(LinkInfo info) {
        ItemStack book = getBook();
        if (book.isEmpty()) return;
        BookUtil.setLinkInfo(book, info);
        inventory.setStack(0, book);
    }

    public void setBookTitle(Player player, String title) {
        ItemStack book = getBook();
        if (!book.isEmpty() && book.getItem() instanceof ItemBehaviours.Renameable r) {
            r.setDisplayName(player, book, title);
            inventory.setStack(0, book);
        }
    }

    public @Nullable ResourceKey<Level> getLinkDimension() {
        LinkInfo info = info();
        return info == null ? null : info.dimension().orElse(null);
    }

    /** Dimension id string shown in the GUI ("" when unbound). */
    public String getLinkDimensionString() {
        ResourceKey<Level> key = getLinkDimension();
        return key == null ? "" : key.identifier().toString();
    }

    /** True when the target Age is dead or its UUID no longer matches (server only). */
    public boolean isLinkDimensionDead() {
        LinkInfo info = info();
        Level level = getLevel();
        if (info == null || level == null || level.isClientSide()) return false;
        MinecraftServer server = level.getServer();
        ResourceKey<Level> key = info.dimension().orElse(null);
        if (server == null || key == null || !AgeManager.isAge(key)) return false;
        AgeData data = AgeManager.get(server, key);
        if (data == null) return true;
        if (data.dead()) return true;
        UUID expected = info.targetUuid().orElse(null);
        return expected != null && !expected.equals(data.uuid());
    }

    public void recycleDimension() {
        Level level = getLevel();
        ResourceKey<Level> key = getLinkDimension();
        if (level == null || level.isClientSide() || key == null) return;
        MinecraftServer server = level.getServer();
        if (server != null) AgeManager.markDead(server, key);
    }

    public boolean getLinkFlag(LinkProperty property) {
        LinkInfo info = info();
        return info != null && info.hasFlag(property);
    }

    public void setLinkFlag(LinkProperty property, boolean value) {
        LinkInfo info = info();
        if (info != null) update(info.withFlag(property, value));
    }

    public @Nullable String getLinkProperty(String name) {
        LinkInfo info = info();
        return info == null ? null : info.prop(name);
    }

    public void setLinkProperty(String name, @Nullable String value) {
        LinkInfo info = info();
        if (info != null) update(info.withProp(name, value));
    }

    /** Seed shown in the GUI ("" when none). Only Descriptive Books carry a seed. */
    public String getSeedString() {
        String seed = getLinkProperty(LinkProperty.PROP_SEED);
        return seed == null ? "" : seed;
    }

    public boolean hasSeed() {
        return BookUtil.isDescriptiveBook(getBook());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mystcraft.link_modifier");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new LinkModifierMenu(containerId, inventory, this);
    }
}

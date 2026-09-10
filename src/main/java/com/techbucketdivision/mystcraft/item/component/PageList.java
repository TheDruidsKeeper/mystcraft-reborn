package com.techbucketdivision.mystcraft.item.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Immutable ordered list of page stacks (each count 1). */
public record PageList(List<ItemStack> pages) {
    public static final PageList EMPTY = new PageList(List.of());
    public static final Codec<PageList> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(PageList::new, PageList::pages);
    public static final StreamCodec<RegistryFriendlyByteBuf, PageList> STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).map(PageList::new, PageList::pages);

    public PageList {
        pages = List.copyOf(pages);
    }

    public int size() {
        return pages.size();
    }

    public boolean isEmpty() {
        return pages.isEmpty();
    }

    public ItemStack get(int index) {
        return pages.get(index).copy();
    }

    /** Defensive copies of every page. */
    public List<ItemStack> copies() {
        List<ItemStack> out = new ArrayList<>(pages.size());
        for (ItemStack s : pages) out.add(s.copy());
        return out;
    }

    public PageList with(int index, ItemStack page) {
        List<ItemStack> l = copies();
        if (index >= l.size()) l.add(page.copy()); else l.set(index, page.copy());
        return new PageList(l);
    }

    public PageList append(ItemStack page) {
        List<ItemStack> l = copies();
        l.add(page.copy());
        return new PageList(l);
    }

    public PageList insert(int index, ItemStack page) {
        List<ItemStack> l = copies();
        l.add(Math.min(index, l.size()), page.copy());
        return new PageList(l);
    }

    public PageList without(int index) {
        List<ItemStack> l = copies();
        if (index >= 0 && index < l.size()) l.remove(index);
        return new PageList(l);
    }

    /** Hashes item + components of every page, ignoring counts (mirrors the original page equality). */
    public boolean containsEqual(ItemStack page) {
        for (ItemStack s : pages) if (ItemStack.isSameItemSameComponents(s, page)) return true;
        return false;
    }
}

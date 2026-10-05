package com.tbd.mystcraft.item.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Sparse slot → page map for collation folders (slots may have gaps). */
public record SlotPages(Map<Integer, ItemStack> slots) {
    public static final SlotPages EMPTY = new SlotPages(Map.of());
    private static final Codec<Map<Integer, ItemStack>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING.xmap(Integer::parseInt, String::valueOf), ItemStack.OPTIONAL_CODEC);
    public static final Codec<SlotPages> CODEC = MAP_CODEC.xmap(SlotPages::new, SlotPages::slots);
    public static final StreamCodec<RegistryFriendlyByteBuf, SlotPages> STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_INT, ItemStack.OPTIONAL_STREAM_CODEC)
                    .map(SlotPages::new, s -> new HashMap<>(s.slots()));

    public SlotPages {
        slots = Map.copyOf(slots);
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    public ItemStack get(int slot) {
        ItemStack s = slots.get(slot);
        return s == null ? ItemStack.EMPTY : s.copy();
    }

    public int largestSlot() {
        int max = -1;
        for (int k : slots.keySet()) max = Math.max(max, k);
        return max;
    }

    public int firstFreeSlot() {
        int i = 0;
        while (slots.containsKey(i)) i++;
        return i;
    }

    public SlotPages with(int slot, ItemStack page) {
        Map<Integer, ItemStack> m = new TreeMap<>(slots);
        if (page.isEmpty()) m.remove(slot); else m.put(slot, page.copyWithCount(1));
        return new SlotPages(m);
    }

    public SlotPages without(int slot) {
        Map<Integer, ItemStack> m = new TreeMap<>(slots);
        m.remove(slot);
        return new SlotPages(m);
    }

    /** Pages in slot order. */
    public java.util.List<ItemStack> ordered() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        new TreeMap<>(slots).values().forEach(s -> out.add(s.copy()));
        return out;
    }
}

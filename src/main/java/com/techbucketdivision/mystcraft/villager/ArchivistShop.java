package com.techbucketdivision.mystcraft.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.registry.ModAttachments;
import com.techbucketdivision.mystcraft.registry.ModItems;
import com.techbucketdivision.mystcraft.symbol.CardRanks;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Archivist's private page shop (original spec §10): three page slots (rank 1 / 2 / 3+ symbol pages, 3 each),
 * a booster stock (starts at 5, max 8, costs 20 emeralds) and a restock simulation every 12000 ticks. Stored as an
 * entity data attachment ({@code ModAttachments.ARCHIVIST_SHOP}).
 */
public final class ArchivistShop {
    public static final int SLOTS = 3;
    public static final int BOOSTER_COST = 20;
    public static final int MAX_BOOSTERS = 8;
    public static final int MAX_PAGES = 5;
    public static final long RESTOCK_STEP = 12000L;

    public static final Codec<ArchivistShop> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("pages", List.of()).forGetter(s -> s.pages),
            Codec.INT.optionalFieldOf("booster_count", 5).forGetter(s -> s.boosterCount),
            Codec.LONG.optionalFieldOf("last_restock", 0L).forGetter(s -> s.lastRestock)
    ).apply(i, ArchivistShop::new));

    private final List<ItemStack> pages;
    private int boosterCount;
    private long lastRestock;
    private long lastUpdated;

    public ArchivistShop() {
        this(List.of(), 5, 0L);
    }

    private ArchivistShop(List<ItemStack> pages, int boosterCount, long lastRestock) {
        this.pages = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            this.pages.add(i < pages.size() ? pages.get(i).copy() : ItemStack.EMPTY);
        }
        this.boosterCount = boosterCount;
        this.lastRestock = lastRestock;
    }

    // --- attachment helpers ------------------------------------------------------------------------------------

    public static ArchivistShop of(Villager villager) {
        return villager.getData(ModAttachments.ARCHIVIST_SHOP.get());
    }

    /** Writes the shop back to the villager so the attachment is marked dirty. */
    public void save(Villager villager) {
        villager.setData(ModAttachments.ARCHIVIST_SHOP.get(), this);
    }

    // --- state ---------------------------------------------------------------------------------------------------

    public int getBoosterCount() {
        return boosterCount;
    }

    public int getBoosterCost() {
        return BOOSTER_COST;
    }

    /** Game time of the last change (for client cache invalidation). */
    public long lastUpdated() {
        return lastUpdated;
    }

    /** Stock of slot {@code index} (0..2); generated lazily on the server when empty. */
    public ItemStack getPageStack(int index) {
        if (index < 0 || index >= SLOTS) return ItemStack.EMPTY;
        return pages.get(index);
    }

    /** Ensures each slot holds a stack of a random symbol page of rank {@code index + 1} (server side). */
    public void ensureStocked(RandomSource random) {
        for (int i = 0; i < SLOTS; i++) {
            if (!pages.get(i).isEmpty()) continue;
            AgeSymbol symbol = CardRanks.weightedRandom(CardRanks.ofRank(i + 1), random);
            if (symbol == null) symbol = CardRanks.weightedRandom(CardRanks.ofRankAtLeast(i + 1), random);
            if (symbol == null) continue;
            ItemStack page = PageItem.createSymbolPage(symbol);
            page.setCount(3);
            pages.set(i, page);
        }
    }

    /** Emerald price of slot {@code index}: {@code 4 * (1 + rank)}, 100 for unknown symbols. */
    public int getPagePrice(int index) {
        ItemStack stack = getPageStack(index);
        if (stack.isEmpty()) return 100;
        AgeSymbol symbol = PageItem.getSymbol(stack);
        if (symbol == null || symbol.cardRank() == null) return 100;
        return 4 * (1 + symbol.cardRank());
    }

    /** Runs the restock simulation for the elapsed time. Returns {@code true} if anything changed. */
    public boolean simulate(long gameTime, RandomSource random) {
        boolean changed = false;
        if (lastRestock == 0L) lastRestock = gameTime;
        long delta = gameTime - lastRestock;
        while (delta >= RESTOCK_STEP) {
            delta -= RESTOCK_STEP;
            lastRestock += RESTOCK_STEP;
            changed |= restock(random);
        }
        if (changed) lastUpdated = gameTime;
        return changed;
    }

    private boolean restock(RandomSource random) {
        int roll = random.nextInt(1 + 3 + SLOTS);
        roll -= 1;
        if (roll < 0) return false;
        roll -= 3;
        if (roll < 0) {
            if (boosterCount < MAX_BOOSTERS) {
                boosterCount++;
                return true;
            }
            return false;
        }
        ItemStack stack = pages.get(roll);
        if (!stack.isEmpty() && stack.getCount() < MAX_PAGES) {
            stack.grow(1);
            return true;
        }
        return false;
    }

    // --- purchases -------------------------------------------------------------------------------------------

    public boolean purchaseBooster(ServerPlayer player) {
        if (boosterCount <= 0) return false;
        if (countEmeralds(player) < BOOSTER_COST) return false;
        if (!deduct(player, BOOSTER_COST)) return false;
        ItemStack booster = new ItemStack(ModItems.SEALED_NOTEBOOK.get());
        player.getInventory().placeItemBackInInventory(booster);
        boosterCount--;
        lastUpdated = player.level().getGameTime();
        return true;
    }

    public boolean purchasePage(ServerPlayer player, int index) {
        ItemStack stock = getPageStack(index);
        if (stock.isEmpty()) return false;
        int price = getPagePrice(index);
        if (countEmeralds(player) < price) return false;
        if (!deduct(player, price)) return false;
        ItemStack sold = stock.copyWithCount(1);
        player.getInventory().placeItemBackInInventory(sold);
        stock.shrink(1);
        lastUpdated = player.level().getGameTime();
        return true;
    }

    /** Emeralds plus 9 per emerald block in the player's main inventory. */
    public static int countEmeralds(ServerPlayer player) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.EMERALD)) total += s.getCount();
            else if (s.is(Items.EMERALD_BLOCK)) total += 9 * s.getCount();
        }
        return total;
    }

    /** Removes {@code price} emeralds, breaking emerald blocks into 9 emeralds when needed and returning change. */
    private static boolean deduct(ServerPlayer player, int price) {
        if (countEmeralds(player) < price) return false;
        Inventory inv = player.getInventory();
        int remaining = removeItems(inv, Items.EMERALD, price);
        if (remaining > 0) {
            int blocks = (remaining + 8) / 9;
            int removedBlocks = blocks - removeItems(inv, Items.EMERALD_BLOCK, blocks);
            int change = removedBlocks * 9 - remaining;
            if (change > 0) inv.placeItemBackInInventory(new ItemStack(Items.EMERALD, change));
        }
        inv.setChanged();
        return true;
    }

    /** Removes up to {@code count} of an item; returns how many could not be removed. */
    private static int removeItems(Inventory inv, net.minecraft.world.item.Item item, int count) {
        for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(item)) continue;
            int take = Math.min(count, s.getCount());
            s.shrink(take);
            count -= take;
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        return count;
    }

    // --- NBT (for the UVC menu message) ------------------------------------------------------------------------

    public CompoundTag toTag(HolderLookup.Provider registries) {
        Tag tag = CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this).getOrThrow();
        return tag instanceof CompoundTag c ? c : new CompoundTag();
    }

    public static ArchivistShop fromTag(HolderLookup.Provider registries, CompoundTag tag) {
        return CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result().orElseGet(ArchivistShop::new);
    }

    /** Deep copy (used when handing state to menus). */
    public ArchivistShop copy() {
        ArchivistShop c = new ArchivistShop(pages, boosterCount, lastRestock);
        c.lastUpdated = lastUpdated;
        return c;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof ArchivistShop other)) return false;
        if (boosterCount != other.boosterCount || lastRestock != other.lastRestock) return false;
        for (int i = 0; i < SLOTS; i++) {
            if (!ItemStack.matches(pages.get(i), other.pages.get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return 31 * boosterCount + Long.hashCode(lastRestock);
    }
}

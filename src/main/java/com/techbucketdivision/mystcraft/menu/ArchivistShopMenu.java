package com.techbucketdivision.mystcraft.menu;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.registry.ModAttachments;
import com.techbucketdivision.mystcraft.registry.ModMenus;
import com.techbucketdivision.mystcraft.villager.ArchivistShop;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Archivist shop container (original spec §8.8 / §10). Slots: 0–26 inventory (y 99), 27–35 hotbar (y 157).
 * Messages client→server: {@code PB} (purchase booster), {@code PI(Index)} (purchase page). Server→client:
 * {@code UVC(Shop)} (villager shop state).
 */
public class ArchivistShopMenu extends AbstractMystcraftMenu {
    public static final String MSG_UPDATE_SHOP = "UVC";
    public static final String MSG_PURCHASE_BOOSTER = "PB";
    public static final String MSG_PURCHASE_ITEM = "PI";

    public static final int INV_START = 0;
    public static final int SHOP_SLOTS = 3;
    public static final int BOOSTER_COST = 20;
    public static final int UNKNOWN_RANK_PRICE = 100;

    private final Villager villager;
    private ArchivistShop shop;
    private @Nullable CompoundTag lastSent;
    private int cachedEmeralds = -1;
    private long lastEmeraldUpdate = Long.MIN_VALUE;

    public ArchivistShopMenu(int containerId, Inventory inv, Villager villager) {
        super(ModMenus.ARCHIVIST_SHOP.get(), containerId, inv);
        this.villager = villager;
        this.shop = villager.getData(ModAttachments.ARCHIVIST_SHOP.get());
        addStandardInventorySlots(inv, 8, 99);
    }

    public static void open(ServerPlayer player, Villager villager) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ArchivistShopMenu(id, inv, villager),
                Component.translatable("container.mystcraft.archivist_shop")), buf -> buf.writeVarInt(villager.getId()));
    }

    public static ArchivistShopMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        if (!(inv.player.level().getEntity(id) instanceof Villager villager)) {
            throw new IllegalStateException("No villager " + id);
        }
        return new ArchivistShopMenu(containerId, inv, villager);
    }

    // --- container plumbing --------------------------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        return villager.isAlive() && player.distanceToSqr(villager) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return quickMove(player, index, 0, 0, INV_START, null);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!isServer()) return;
        CompoundTag now = shop.toTag(player.level().registryAccess());
        if (lastSent == null || !lastSent.equals(now)) {
            lastSent = now.copy();
            CompoundTag tag = new CompoundTag();
            tag.put("Shop", now);
            sendToClient(MSG_UPDATE_SHOP, tag);
        }
    }

    @Override
    public void processMessage(Player player, CompoundTag data) {
        switch (messageName(data)) {
            case MSG_UPDATE_SHOP -> {
                if (!isClient()) return;
                CompoundTag tag = data.getCompoundOrEmpty("Shop");
                ArchivistShop.CODEC.parse(player.level().registryAccess().createSerializationContext(NbtOps.INSTANCE), tag)
                        .resultOrPartial(err -> Mystcraft.LOGGER.warn("Bad archivist shop sync: {}", err))
                        .ifPresent(parsed -> shop = parsed);
            }
            case MSG_PURCHASE_BOOSTER -> {
                if (player instanceof ServerPlayer sp && shop.purchaseBooster(sp)) {
                    cachedEmeralds = -1;
                    shop.save(villager);
                }
            }
            case MSG_PURCHASE_ITEM -> {
                int index = data.getIntOr("Index", -1);
                if (index < 0 || index >= SHOP_SLOTS) return;
                if (player instanceof ServerPlayer sp && shop.purchasePage(sp, index)) {
                    cachedEmeralds = -1;
                    shop.save(villager);
                }
            }
            default -> {
            }
        }
    }

    // --- screen state ----------------------------------------------------------------------------------------------

    public Villager getVillager() {
        return villager;
    }

    public ArchivistShop getShop() {
        return shop;
    }

    /** Page stack offered in shop slot {@code index} (0–2), or empty. */
    public ItemStack getShopItem(int index) {
        return shop.getPageStack(index);
    }

    /** Price in emeralds: {@code 4 * (1 + rank)}, 100 for pages of unknown rank. */
    public int getShopItemPrice(int index) {
        ItemStack page = getShopItem(index);
        if (page.isEmpty()) return 0;
        AgeSymbol symbol = PageItem.getSymbol(page);
        Integer rank = symbol == null ? null : symbol.cardRank();
        return rank == null ? UNKNOWN_RANK_PRICE : 4 * (1 + rank);
    }

    public int getBoosterCount() {
        return shop.getBoosterCount();
    }

    public int getBoosterCost() {
        return BOOSTER_COST;
    }

    /** Emeralds the player can spend (emeralds + 9 × emerald blocks), refreshed every 100 ticks. */
    public int getPlayerEmeralds() {
        long now = player.level().getGameTime();
        if (cachedEmeralds < 0 || lastEmeraldUpdate + 100 < now) {
            lastEmeraldUpdate = now;
            cachedEmeralds = playerInventory.countItem(Items.EMERALD) + 9 * playerInventory.countItem(Items.EMERALD_BLOCK);
        }
        return cachedEmeralds;
    }
}

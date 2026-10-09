package com.tbd.mystcraft.blockentity;

import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.facility.FacilityState;
import com.tbd.mystcraft.item.LinkingItem;
import com.tbd.mystcraft.item.component.BookHealth;
import com.tbd.mystcraft.registry.ModBlockEntities;
import com.tbd.mystcraft.registry.ModDataComponents;
import com.tbd.mystcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Facility Cache: a per-player reward (FACILITY_PLAN.md §8.5). Every player may claim it once: a Linking Book home
 * ({@code reward:linkbook}, which also marks the facility solved and lifts the protection rule) or one roll of a loot
 * table ({@code reward:<table>}).
 */
public class FacilityCacheBlockEntity extends MystBlockEntity {
    public static final String HOME_BOOK_NAME = "Home";

    private @Nullable ResourceKey<LootTable> lootTable;
    private final List<UUID> claimed = new ArrayList<>();

    public FacilityCacheBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FACILITY_CACHE.get(), pos, state);
    }

    /** {@code null} = the Linking Book reward. */
    public void setLootTable(@Nullable Identifier table) {
        lootTable = table == null ? null : ResourceKey.create(Registries.LOOT_TABLE, table);
        setChanged();
    }

    public @Nullable ResourceKey<LootTable> lootTable() {
        return lootTable;
    }

    public boolean isLinkbook() {
        return lootTable == null;
    }

    public boolean hasClaimed(UUID player) {
        return claimed.contains(player);
    }

    public InteractionResult interact(ServerPlayer player) {
        if (!(getLevel() instanceof ServerLevel level)) return InteractionResult.PASS;
        if (claimed.contains(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.mystcraft.facility.cache.claimed"), true);
            return InteractionResult.CONSUME;
        }
        List<ItemStack> rewards = new ArrayList<>();
        if (lootTable == null) {
            rewards.add(homeBook());
            FacilityState.markSolved(level);
        } else {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(lootTable);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, getBlockPos().getCenter())
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                    .create(LootContextParamSets.CHEST);
            rewards.addAll(table.getRandomItems(params, FacilityState.seed(level) ^ player.getUUID().getLeastSignificantBits()));
        }
        for (ItemStack stack : rewards) player.getInventory().placeItemBackInInventory(stack);
        claimed.add(player.getUUID());
        markForUpdate();
        level.playSound(null, getBlockPos(), SoundEvents.VAULT_EJECT_ITEM, SoundSource.BLOCKS, 1f, 1f);
        player.sendSystemMessage(Component.translatable(lootTable == null
                ? "message.mystcraft.facility.cache.linkbook" : "message.mystcraft.facility.cache.loot"), true);
        return InteractionResult.CONSUME;
    }

    /** A Linking Book to the overworld's world spawn (the facility's exit). */
    public static ItemStack homeBook() {
        ItemStack book = new ItemStack(ModItems.LINKING_BOOK.get());
        LinkingItem.setLinkInfo(book, new LinkInfo(Optional.of(Level.OVERWORLD), Optional.empty(), Optional.empty(),
                LinkInfo.DEFAULT_YAW, HOME_BOOK_NAME, Set.of(), Map.of()));
        book.set(ModDataComponents.BOOK_HEALTH.get(), BookHealth.FULL);
        return book;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (lootTable != null) output.store("LootTable", Identifier.CODEC, lootTable.identifier());
        output.store("Claimed", UUIDUtil.CODEC.listOf(), claimed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Identifier table = input.read("LootTable", Identifier.CODEC).orElse(null);
        lootTable = table == null ? null : ResourceKey.create(Registries.LOOT_TABLE, table);
        claimed.clear();
        claimed.addAll(input.read("Claimed", UUIDUtil.CODEC.listOf()).orElse(List.of()));
    }
}

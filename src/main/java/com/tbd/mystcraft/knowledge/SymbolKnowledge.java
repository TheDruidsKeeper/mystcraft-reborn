package com.tbd.mystcraft.knowledge;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.api.symbol.AgeSymbol;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.item.component.SymbolPage;
import com.tbd.mystcraft.network.KnowledgePayload;
import com.tbd.mystcraft.registry.ModAttachments;
import com.tbd.mystcraft.symbol.SymbolRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.tbd.mystcraft.network.Network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a player knows how to write (world-building plan §4, decision §7.1): the set of unlocked symbol ids, stored as
 * a player attachment ({@link ModAttachments#KNOWLEDGE}), kept over death, and mirrored to the client with
 * {@link KnowledgePayload} so the Writing Desk can list it. Symbols are learned by <b>using a symbol page</b> (the page
 * is consumed; its modifiers are learned too) and by <b>arriving in an Age</b> (every symbol of the Age). Logged under
 * {@code [knowledge]}.
 */
public final class SymbolKnowledge {
    private SymbolKnowledge() {}

    /** Symbols the player knows (a copy; on the client the synced set). */
    public static Set<Identifier> known(Player player) {
        return new LinkedHashSet<>(player.getData(ModAttachments.KNOWLEDGE.get()));
    }

    public static boolean knows(Player player, Identifier symbol) {
        return player.getData(ModAttachments.KNOWLEDGE.get()).contains(symbol);
    }

    public static boolean knows(Player player, AgeSymbol symbol) {
        return knows(player, symbol.id());
    }

    /** Whether the player knows every symbol the page carries (its symbol and its modifiers). */
    public static boolean knowsPage(Player player, ItemStack page) {
        SymbolPage symbolPage = PageItem.getSymbolPage(page);
        if (symbolPage == null) return false;
        Set<Identifier> set = player.getData(ModAttachments.KNOWLEDGE.get());
        return set.containsAll(symbolPage.flatten());
    }

    /**
     * Unlocks registered symbols for the player; returns the ones that were new. Syncs the client and tells the
     * player what they learned.
     */
    public static List<Identifier> unlock(Player player, Collection<Identifier> symbols, String source) {
        Set<Identifier> set = new LinkedHashSet<>(player.getData(ModAttachments.KNOWLEDGE.get()));
        List<Identifier> learned = new ArrayList<>();
        for (Identifier id : symbols) {
            if (SymbolRegistry.get(id) != null && set.add(id)) learned.add(id);
        }
        if (learned.isEmpty()) return learned;
        player.setData(ModAttachments.KNOWLEDGE.get(), Set.copyOf(set));
        if (player instanceof ServerPlayer serverPlayer) {
            sync(serverPlayer);
            com.tbd.mystcraft.registry.ModCriteria.SYMBOL_LEARNED.get().trigger(serverPlayer);
        }
        Mystcraft.LOGGER.info("[knowledge] {} learned {} symbol(s) from {}: {} ({} known)", player.getPlainTextName(), learned.size(), source,
                learned, set.size());
        if (player instanceof ServerPlayer serverPlayer) {
            if (learned.size() <= 3) {
                List<Component> names = new ArrayList<>();
                for (Identifier id : learned) names.add(SymbolRegistry.get(id).displayName());
                serverPlayer.sendOverlayMessage(Component.translatable("message.mystcraft.knowledge.learned",
                        names.stream().map(Component::getString).reduce((a, b) -> a + ", " + b).orElse("")).withStyle(ChatFormatting.AQUA));
            } else {
                serverPlayer.sendOverlayMessage(Component.translatable("message.mystcraft.knowledge.learned_many", learned.size()).withStyle(ChatFormatting.AQUA));
            }
        }
        return learned;
    }

    /** Learns everything on a page (symbol + modifiers). */
    public static List<Identifier> learnPage(Player player, ItemStack page) {
        SymbolPage symbolPage = PageItem.getSymbolPage(page);
        if (symbolPage == null) return List.of();
        return unlock(player, symbolPage.flatten(), "page " + symbolPage.symbol());
    }

    /** Learns every symbol of an Age (called on arrival). */
    public static List<Identifier> learnAge(Player player, AgeData age) {
        return unlock(player, age.symbols(), "Age " + age.name());
    }

    /** Pushes the player's knowledge to their client (login, respawn, after every change); skipped for connections without the channel. */
    public static void sync(ServerPlayer player) {
        Network.sendToPlayer(player, new KnowledgePayload(List.copyOf(player.getData(ModAttachments.KNOWLEDGE.get()))));
    }

    /** Client side: replaces the local copy. */
    public static void receive(Player player, Collection<Identifier> symbols) {
        player.setData(ModAttachments.KNOWLEDGE.get(), Set.copyOf(new LinkedHashSet<>(symbols)));
    }
}

package com.tbd.mystcraft.linking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tbd.mystcraft.util.MystIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-player link permissions (original spec §7.3, §12 {@code /myst permissions}), server-global SavedData
 * {@code data/mystcraft/link_permissions.dat}. Keyed by player name like the original.
 * <p>
 * Semantics per direction (entry / departure): a player with a <em>permit</em> set may only use the dimensions in
 * it; a player with a <em>restrict</em> set may not use those dimensions; no entry means everything is allowed.
 */
public final class LinkPermissions extends SavedData {
    private static final Codec<Map<String, List<ResourceKey<Level>>>> MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, ResourceKey.codec(Registries.DIMENSION).listOf());

    private static final Codec<LinkPermissions> CODEC = RecordCodecBuilder.create(i -> i.group(
            MAP_CODEC.optionalFieldOf("permit_entry", Map.of()).forGetter(p -> toLists(p.permitEntry)),
            MAP_CODEC.optionalFieldOf("restrict_entry", Map.of()).forGetter(p -> toLists(p.restrictEntry)),
            MAP_CODEC.optionalFieldOf("permit_depart", Map.of()).forGetter(p -> toLists(p.permitDepart)),
            MAP_CODEC.optionalFieldOf("restrict_depart", Map.of()).forGetter(p -> toLists(p.restrictDepart))
    ).apply(i, LinkPermissions::new));

    public static final SavedDataType<LinkPermissions> TYPE = new SavedDataType<>(
            MystIds.id("link_permissions"), LinkPermissions::new, CODEC);

    private final Map<String, Set<ResourceKey<Level>>> permitEntry = new HashMap<>();
    private final Map<String, Set<ResourceKey<Level>>> restrictEntry = new HashMap<>();
    private final Map<String, Set<ResourceKey<Level>>> permitDepart = new HashMap<>();
    private final Map<String, Set<ResourceKey<Level>>> restrictDepart = new HashMap<>();

    public LinkPermissions() {}

    private LinkPermissions(Map<String, List<ResourceKey<Level>>> permitEntry, Map<String, List<ResourceKey<Level>>> restrictEntry,
                            Map<String, List<ResourceKey<Level>>> permitDepart, Map<String, List<ResourceKey<Level>>> restrictDepart) {
        fromLists(permitEntry, this.permitEntry);
        fromLists(restrictEntry, this.restrictEntry);
        fromLists(permitDepart, this.permitDepart);
        fromLists(restrictDepart, this.restrictDepart);
    }

    private static Map<String, List<ResourceKey<Level>>> toLists(Map<String, Set<ResourceKey<Level>>> map) {
        Map<String, List<ResourceKey<Level>>> out = new HashMap<>();
        map.forEach((k, v) -> out.put(k, List.copyOf(v)));
        return out;
    }

    private static void fromLists(Map<String, List<ResourceKey<Level>>> in, Map<String, Set<ResourceKey<Level>>> out) {
        in.forEach((k, v) -> out.put(k, new HashSet<>(v)));
    }

    public static LinkPermissions get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Player key used for permission lookups. */
    public static String keyOf(Player player) {
        return player.getPlainTextName();
    }

    // --- queries -----------------------------------------------------------------------------------------------

    public boolean canEnter(String player, ResourceKey<Level> dimension) {
        return allowed(permitEntry.get(player), restrictEntry.get(player), dimension);
    }

    public boolean canLeave(String player, ResourceKey<Level> dimension) {
        return allowed(permitDepart.get(player), restrictDepart.get(player), dimension);
    }

    public boolean canEnter(Player player, ResourceKey<Level> dimension) {
        return canEnter(keyOf(player), dimension);
    }

    public boolean canLeave(Player player, ResourceKey<Level> dimension) {
        return canLeave(keyOf(player), dimension);
    }

    private static boolean allowed(@Nullable Set<ResourceKey<Level>> permitted, @Nullable Set<ResourceKey<Level>> restricted,
                                   ResourceKey<Level> dimension) {
        if (restricted != null && restricted.contains(dimension)) return false;
        return permitted == null || permitted.contains(dimension);
    }

    // --- mutators ({@code null} dimension = all) ---------------------------------------------------------------

    public void permitEntry(String player, @Nullable ResourceKey<Level> dimension) {
        permit(permitEntry, restrictEntry, player, dimension);
    }

    public void restrictEntry(String player, @Nullable ResourceKey<Level> dimension) {
        restrict(permitEntry, restrictEntry, player, dimension);
    }

    public void permitDepart(String player, @Nullable ResourceKey<Level> dimension) {
        permit(permitDepart, restrictDepart, player, dimension);
    }

    public void restrictDepart(String player, @Nullable ResourceKey<Level> dimension) {
        restrict(permitDepart, restrictDepart, player, dimension);
    }

    private void permit(Map<String, Set<ResourceKey<Level>>> permitMap, Map<String, Set<ResourceKey<Level>>> restrictMap,
                        String player, @Nullable ResourceKey<Level> dimension) {
        if (dimension == null) {
            restrictMap.remove(player);
            permitMap.remove(player);
        } else {
            Set<ResourceKey<Level>> permitted = permitMap.get(player);
            if (permitted != null) permitted.add(dimension);
            Set<ResourceKey<Level>> restricted = restrictMap.get(player);
            if (restricted != null) restricted.remove(dimension);
        }
        setDirty();
    }

    private void restrict(Map<String, Set<ResourceKey<Level>>> permitMap, Map<String, Set<ResourceKey<Level>>> restrictMap,
                          String player, @Nullable ResourceKey<Level> dimension) {
        if (dimension == null) {
            restrictMap.remove(player);
            permitMap.put(player, new HashSet<>());
        } else {
            Set<ResourceKey<Level>> permitted = permitMap.get(player);
            if (permitted != null) permitted.remove(dimension);
            restrictMap.computeIfAbsent(player, k -> new HashSet<>()).add(dimension);
        }
        setDirty();
    }

    /** Read-only views for commands / debugging. */
    public Map<String, Set<ResourceKey<Level>>> permittedEntry() {
        return Map.copyOf(permitEntry);
    }

    public Map<String, Set<ResourceKey<Level>>> restrictedEntry() {
        return Map.copyOf(restrictEntry);
    }

    public Map<String, Set<ResourceKey<Level>>> permittedDepart() {
        return Map.copyOf(permitDepart);
    }

    public Map<String, Set<ResourceKey<Level>>> restrictedDepart() {
        return Map.copyOf(restrictDepart);
    }
}

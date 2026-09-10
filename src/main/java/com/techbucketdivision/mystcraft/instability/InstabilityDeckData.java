package com.techbucketdivision.mystcraft.instability;

import com.mojang.serialization.Codec;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persisted shuffled card order per deck for one Age: per-level SavedData {@code mystcraft:instability_decks}. */
public final class InstabilityDeckData extends SavedData {
    private static final Codec<InstabilityDeckData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
            .fieldOf("decks").codec().xmap(InstabilityDeckData::new, d -> d.decks);

    public static final SavedDataType<InstabilityDeckData> TYPE = new SavedDataType<>(MystIds.id("instability_decks"), InstabilityDeckData::new, CODEC);

    private final Map<String, List<String>> decks = new LinkedHashMap<>();

    public InstabilityDeckData() {}

    private InstabilityDeckData(Map<String, List<String>> decks) {
        decks.forEach((k, v) -> this.decks.put(k, new ArrayList<>(v)));
    }

    public static InstabilityDeckData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<String> deck(String name) {
        return List.copyOf(decks.getOrDefault(name, List.of()));
    }

    public void updateDeck(String name, List<String> order) {
        decks.put(name, new ArrayList<>(order));
        setDirty();
    }
}

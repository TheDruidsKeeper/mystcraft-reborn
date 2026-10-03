package com.techbucketdivision.mystcraft.linking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.linking.LinkEvent;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.network.PanelImagePayloads;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.techbucketdivision.mystcraft.network.Network;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pictures of link destinations, server-global SavedData {@code data/mystcraft/panel_images.dat}. A key names a
 * destination ({@link #keyFor}); each key keeps the last {@link #MAX_FRAMES} photographs clients took on arrival,
 * which book GUIs play as a slideshow on the link panel. Pictures are small PNGs (about 10 KB each).
 */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class PanelImageStorage extends SavedData {
    public static final int MAX_FRAMES = 4;
    /** Do not ask the same arriving player for a new photo of the same place more often than this (ticks). */
    public static final long RECAPTURE_INTERVAL_TICKS = 20L * 60 * 5;
    private static final int MAX_KEYS = 4096;

    private record Entry(List<byte[]> frames, long lastCapture) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BYTE_BUFFER.xmap(b -> { byte[] a = new byte[b.remaining()]; b.duplicate().get(a); return a; }, java.nio.ByteBuffer::wrap)
                        .listOf().fieldOf("frames").forGetter(Entry::frames),
                Codec.LONG.optionalFieldOf("last_capture", 0L).forGetter(Entry::lastCapture)
        ).apply(i, Entry::new));
    }

    private static final Codec<PanelImageStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Entry.CODEC).optionalFieldOf("images", Map.of()).forGetter(s -> s.entries)
    ).apply(i, PanelImageStorage::new));

    public static final SavedDataType<PanelImageStorage> TYPE = new SavedDataType<>(
            MystIds.id("panel_images"), PanelImageStorage::new, CODEC);

    private final Map<String, Entry> entries = new HashMap<>();

    public PanelImageStorage() {}

    private PanelImageStorage(Map<String, Entry> entries) {
        this.entries.putAll(entries);
    }

    public static @Nullable PanelImageStorage get(@Nullable MinecraftServer server) {
        return server == null ? null : server.getDataStorage().computeIfAbsent(TYPE);
    }

    // --- keys ----------------------------------------------------------------------------------------------------

    /**
     * Destination key of a link: an Age is one place ({@code age/<uuid>}) whatever the exact spot; any other link is
     * its dimension plus the arrival point on an 8-block grid ({@code pos/<dimension>/x/y/z}).
     */
    public static @Nullable String keyFor(LinkInfo info) {
        if (info.targetUuid().isPresent()) return "age/" + info.targetUuid().get();
        if (info.dimension().isEmpty()) return null;
        Optional<BlockPos> spawn = info.spawn();
        if (spawn.isEmpty()) return "dim/" + info.dimension().get().identifier();
        BlockPos p = spawn.get();
        return "pos/" + info.dimension().get().identifier() + "/" + (p.getX() >> 3) + "/" + (p.getY() >> 3) + "/" + (p.getZ() >> 3);
    }

    // --- storage ----------------------------------------------------------------------------------------------------

    public List<byte[]> frames(String key) {
        Entry e = entries.get(key);
        return e == null ? List.of() : List.copyOf(e.frames());
    }

    public void add(String key, byte[] png) {
        if (key.length() > PanelImagePayloads.MAX_KEY_LENGTH || png.length == 0 || png.length > PanelImagePayloads.MAX_IMAGE_BYTES) return;
        if (!entries.containsKey(key) && entries.size() >= MAX_KEYS) return;
        Entry old = entries.get(key);
        List<byte[]> frames = new ArrayList<>(old == null ? List.of() : old.frames());
        frames.add(png);
        while (frames.size() > MAX_FRAMES) frames.remove(0);
        entries.put(key, new Entry(frames, old == null ? 0L : old.lastCapture()));
        setDirty();
        Mystcraft.LOGGER.debug("[panel] stored picture {} ({} bytes, {} frames)", key, png.length, frames.size());
    }

    /** Whether a fresh photo of {@code key} is wanted now; records the request time when it is. */
    public boolean requestCapture(String key, long now) {
        Entry e = entries.get(key);
        if (e != null && e.frames().size() >= MAX_FRAMES && now - e.lastCapture() < RECAPTURE_INTERVAL_TICKS) return false;
        if (e != null && now - e.lastCapture() < 200L) return false; // one request per arrival burst
        if (e == null && entries.size() >= MAX_KEYS) return false;
        entries.put(key, new Entry(e == null ? List.of() : e.frames(), now));
        setDirty();
        return true;
    }

    // --- capture trigger -----------------------------------------------------------------------------------------

    /** After a player arrives through a link, ask their client to photograph the destination. */
    @SubscribeEvent
    public static void onLinkEnd(LinkEvent.End event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String key = keyFor(event.getInfo());
        if (key == null) return;
        PanelImageStorage storage = get(player.level().getServer());
        if (storage == null) return;
        long now = player.level().getGameTime();
        if (!storage.requestCapture(key, now)) return;
        Network.sendToPlayer(player, new PanelImagePayloads.CaptureRequest(key));
        Mystcraft.LOGGER.info("[panel] asked {} to photograph {}", player.getPlainTextName(), key);
    }
}

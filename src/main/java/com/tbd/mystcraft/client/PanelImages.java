package com.tbd.mystcraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.linking.PanelImageStorage;
import com.tbd.mystcraft.network.PanelImagePayloads;
import com.tbd.mystcraft.util.MystIds;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client side of the link panel pictures: takes the photos the server asks for after a link (once the chunks around
 * the arrival point have rendered; HUD and hand hidden, camera - not the player - turned to the four compass
 * directions, downscaled to {@link #WIDTH}x{@link #HEIGHT}), and caches the frames received for a key as GPU
 * textures for {@code BookElement} to draw. Keys are {@link PanelImageStorage#keyFor} strings.
 */
public final class PanelImages {
    private PanelImages() {}

    public static final int WIDTH = 128;
    public static final int HEIGHT = 80;
    /** Ticks after arrival before the capture may start (the arrival platform and the link effects settle). */
    private static final int CAPTURE_DELAY = 30;
    /** Longest wait for the chunk sections around the player to be present and compiled before the first shot. */
    private static final int CHUNK_WAIT_LIMIT = 200;
    /** Longest wait after turning the camera for the newly visible sections to compile before that shot. */
    private static final int TURN_WAIT_LIMIT = 40;
    /** Ticks the "all sections compiled" state must hold before a shot is taken (one is noise). */
    private static final int READY_STREAK = 2;
    /** Chunk radius around the player that must be present on the client before the first shot. */
    private static final int CHUNK_RADIUS = 2;
    /** Milliseconds each frame of the slideshow stays. */
    public static final long FRAME_MILLIS = 2500L;
    private static final long REQUEST_RETRY_MILLIS = 15_000L;

    private static final class Cached {
        final List<Identifier> frames = new ArrayList<>();
        long requested;
        boolean received;
    }

    private static final Map<String, Cached> CACHE = new HashMap<>();
    private static int textureSerial;

    private static @Nullable String pendingKey;
    /** Ticks since the capture was requested / since the current shot started. */
    private static int pendingTicks;
    private static int readyTicks;
    private static boolean hidGui;
    private static @Nullable CameraType savedCameraType;
    /** Index into {@link #SHOT_ORDER} of the view being photographed; -1 = still waiting for the world to be ready. */
    private static int pendingDirection = -1;
    /** The four level views are taken in this order so the slideshow turns clockwise: north, east, south, west. */
    private static final Direction[] SHOT_ORDER = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    // --- capture ------------------------------------------------------------------------------------------------

    public static void onCaptureRequest(String key) {
        pendingKey = key;
        pendingTicks = 0;
        readyTicks = 0;
        pendingDirection = -1;
        Mystcraft.LOGGER.info("[panel] will photograph {} once the chunks around the arrival point have rendered (four level views)", key);
    }

    /**
     * Called every client tick (after the level ticked). After a short delay the capture waits until the chunks
     * around the player are present and the section builder has caught up (bounded), then hides the HUD and hand
     * and photographs north, east, south and west in turn. The player is not turned: the camera alone is pointed
     * through {@link #onCameraAngles}, so nothing is sent to the server and other players see no spin. Each turn
     * waits for the newly visible sections to compile (bounded) before its frame is grabbed. The server keeps
     * exactly four frames per key, so the four views replace the previous arrival's set.
     */
    public static void tick(Minecraft mc) {
        if (pendingKey == null) return;
        if (mc.level == null || mc.player == null) {
            abort(mc);
            return;
        }
        if (mc.screen != null && pendingDirection < 0) return; // wait until no GUI covers the view
        pendingTicks++;
        if (pendingDirection < 0) {
            if (pendingTicks < CAPTURE_DELAY) return;
            readyTicks = worldReady(mc) ? readyTicks + 1 : 0;
            if (readyTicks < READY_STREAK && pendingTicks < CAPTURE_DELAY + CHUNK_WAIT_LIMIT) return;
            Mystcraft.LOGGER.info("[panel] photographing {} after {} ticks ({})", pendingKey, pendingTicks,
                    readyTicks >= READY_STREAK ? "chunks rendered" : "chunk wait limit reached");
            // hide the HUD and hand, look along the first direction; grab once that frame has been drawn and settled
            hidGui = true;
            mc.options.hideGui = true;
            savedCameraType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            startShot(mc, 0);
            return;
        }
        // a turned camera needs at least one rendered frame, then the sections it now sees should be compiled
        readyTicks = sectionsReady(mc) ? readyTicks + 1 : 0;
        if (pendingTicks < 2 || (readyTicks < 1 && pendingTicks < TURN_WAIT_LIMIT)) return;
        String key = pendingKey;
        int shot = pendingDirection;
        try {
            Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
                try (image) {
                    upload(key, image, shot == 0);
                }
            });
        } catch (RuntimeException e) {
            Mystcraft.LOGGER.warn("[panel] screenshot {} failed for {}", shot, key, e);
        }
        if (shot + 1 < SHOT_ORDER.length) {
            startShot(mc, shot + 1);
        } else {
            abort(mc);
        }
    }

    private static void startShot(Minecraft mc, int index) {
        pendingDirection = index;
        pendingTicks = 0;
        readyTicks = 0;
        mc.levelRenderer.needsUpdate(); // re-walk the occlusion graph for the new view direction right away
    }

    /** Chunks within {@link #CHUNK_RADIUS} of the player are on the client and the section builder is idle. */
    private static boolean worldReady(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        int cx = mc.player.chunkPosition().x(), cz = mc.player.chunkPosition().z();
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                if (!mc.level.hasChunk(cx + dx, cz + dz)) return false;
            }
        }
        return sectionsReady(mc);
    }

    private static boolean sectionsReady(Minecraft mc) {
        return mc.levelRenderer.hasRenderedAllSections();
    }

    /**
     * Points the camera (not the player) along the direction being photographed, level. Registered on the game bus
     * by {@code ClientGameEvents}.
     */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (pendingKey == null || pendingDirection < 0) return;
        event.setYaw(SHOT_ORDER[pendingDirection].toYRot());
        event.setPitch(0f);
        event.setRoll(0f);
    }

    /** No first-person hand in the photographs. */
    public static void onRenderHand(RenderHandEvent event) {
        if (pendingKey != null && pendingDirection >= 0) event.setCanceled(true);
    }

    private static void abort(Minecraft mc) {
        if (hidGui) {
            mc.options.hideGui = false;
            if (savedCameraType != null) mc.options.setCameraType(savedCameraType);
        }
        hidGui = false;
        savedCameraType = null;
        pendingKey = null;
        pendingDirection = -1;
    }

    private static void upload(String key, NativeImage full, boolean firstOfSet) {
        try (NativeImage small = new NativeImage(WIDTH, HEIGHT, false)) {
            // crop to the panel's aspect ratio around the centre, then downscale
            int w = full.getWidth(), h = full.getHeight();
            int cropW = w, cropH = (int) (w * (HEIGHT / (double) WIDTH));
            if (cropH > h) {
                cropH = h;
                cropW = (int) (h * (WIDTH / (double) HEIGHT));
            }
            full.resizeSubRectTo((w - cropW) / 2, (h - cropH) / 2, cropW, cropH, small);
            Path tmp = Files.createTempFile("mystcraft-panel", ".png");
            try {
                small.writeToFile(tmp);
                byte[] png = Files.readAllBytes(tmp);
                if (png.length > PanelImagePayloads.MAX_IMAGE_BYTES) {
                    Mystcraft.LOGGER.warn("[panel] picture of {} too large ({} bytes), dropped", key, png.length);
                    return;
                }
                ClientNetwork.sendToServer(new PanelImagePayloads.Upload(key, png));
                Mystcraft.LOGGER.info("[panel] uploaded picture of {} ({} bytes)", key, png.length);
                // show it locally right away (a new set of views replaces the old frames)
                Cached cached = CACHE.computeIfAbsent(key, k -> new Cached());
                if (firstOfSet) release(cached);
                Identifier mine = register(png);
                if (mine != null) cached.frames.add(mine);
                cached.received = true;
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException | RuntimeException e) {
            Mystcraft.LOGGER.warn("[panel] could not encode picture of {}", key, e);
        }
    }

    // --- cache --------------------------------------------------------------------------------------------------

    public static void onImages(String key, List<byte[]> frames) {
        Cached cached = CACHE.computeIfAbsent(key, k -> new Cached());
        release(cached);
        for (byte[] png : frames) {
            Identifier id = register(png);
            if (id != null) cached.frames.add(id);
        }
        cached.received = true;
    }

    private static @Nullable Identifier register(byte[] png) {
        try {
            NativeImage image = NativeImage.read(png);
            Identifier id = MystIds.id("panel/" + (textureSerial++));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
            return id;
        } catch (IOException e) {
            Mystcraft.LOGGER.warn("[panel] bad picture data", e);
            return null;
        }
    }

    private static void release(Cached cached) {
        for (Identifier id : cached.frames) Minecraft.getInstance().getTextureManager().release(id);
        cached.frames.clear();
    }

    /**
     * Texture to draw for a link right now (slideshow over the stored frames), or {@code null} when none is known.
     * Asks the server for the pictures the first time a key is seen (and again every few seconds while empty).
     */
    public static @Nullable Identifier current(@Nullable LinkInfo info) {
        if (info == null) return null;
        String key = PanelImageStorage.keyFor(info);
        if (key == null) return null;
        Cached cached = CACHE.computeIfAbsent(key, k -> new Cached());
        long now = System.currentTimeMillis();
        if (cached.frames.isEmpty() && (!cached.received || now - cached.requested > REQUEST_RETRY_MILLIS)
                && now - cached.requested > 1000L) {
            cached.requested = now;
            if (Minecraft.getInstance().getConnection() != null) {
                ClientNetwork.sendToServer(new PanelImagePayloads.Request(key));
            }
        }
        if (cached.frames.isEmpty()) return null;
        int index = (int) ((now / FRAME_MILLIS) % cached.frames.size());
        return cached.frames.get(index);
    }

    public static int frameCount(@Nullable LinkInfo info) {
        if (info == null) return 0;
        String key = PanelImageStorage.keyFor(info);
        Cached cached = key == null ? null : CACHE.get(key);
        return cached == null ? 0 : cached.frames.size();
    }

    /** True while an arrival photo sequence is scheduled or in progress (GUIs must not cover the view). */
    public static boolean isCapturing() {
        return pendingKey != null;
    }

    public static void clear() {
        for (Cached cached : CACHE.values()) release(cached);
        CACHE.clear();
        pendingKey = null;
        pendingDirection = -1;
        hidGui = false;
    }
}

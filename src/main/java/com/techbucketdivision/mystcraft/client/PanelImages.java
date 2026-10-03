package com.techbucketdivision.mystcraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.linking.PanelImageStorage;
import com.techbucketdivision.mystcraft.network.PanelImagePayloads;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client side of the link panel pictures: takes the photo the server asks for after a link (a few ticks after
 * arrival, HUD hidden, downscaled to {@link #WIDTH}x{@link #HEIGHT}), and caches the frames received for a key as
 * GPU textures for {@code BookElement} to draw. Keys are {@link PanelImageStorage#keyFor} strings.
 */
public final class PanelImages {
    private PanelImages() {}

    public static final int WIDTH = 128;
    public static final int HEIGHT = 80;
    /** Ticks after arrival before the photo is taken (chunks need to show up). */
    private static final int CAPTURE_DELAY = 45;
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
    private static int pendingTicks;
    private static boolean hidGui;
    /** Index of the next compass direction to photograph (0..3 = S, W, N, E in {@link Direction#from2DDataValue}); -1 = not started. */
    private static int pendingDirection = -1;
    private static float savedYaw, savedPitch;
    /** The four level views are taken in this order so the slideshow turns clockwise: north, east, south, west. */
    private static final Direction[] SHOT_ORDER = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    // --- capture ------------------------------------------------------------------------------------------------

    public static void onCaptureRequest(String key) {
        pendingKey = key;
        pendingTicks = CAPTURE_DELAY;
        pendingDirection = -1;
        Mystcraft.LOGGER.info("[panel] will photograph {} in {} ticks (four level views from the arrival point)", key, CAPTURE_DELAY);
    }

    /**
     * Called every client tick (after the level ticked). Once the delay is over the HUD is hidden and the player is
     * turned to face north, east, south and west in turn (level, one tick each so the frame gets rendered); each
     * view is photographed and uploaded, then the original view direction is restored. The server keeps exactly
     * four frames per key, so the four views replace the previous arrival's set.
     */
    public static void tick(Minecraft mc) {
        if (pendingKey == null) return;
        if (mc.level == null || mc.player == null) {
            abort(mc);
            return;
        }
        if (mc.screen != null && pendingDirection < 0) return; // wait until no GUI covers the view
        if (pendingDirection < 0 && --pendingTicks > 0) return;
        if (!hidGui) {
            // hide the HUD and face the first direction; grab on the next tick once that frame has been drawn
            hidGui = true;
            mc.options.hideGui = true;
            savedYaw = mc.player.getYRot();
            savedPitch = mc.player.getXRot();
            pendingDirection = 0;
            face(mc, SHOT_ORDER[0]);
            return;
        }
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
            pendingDirection = shot + 1;
            face(mc, SHOT_ORDER[pendingDirection]);
        } else {
            finish(mc);
        }
    }

    private static void face(Minecraft mc, Direction direction) {
        float yaw = direction.toYRot();
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setYHeadRot(yaw);
        mc.player.yHeadRotO = yaw;
        mc.player.setXRot(0f);
        mc.player.xRotO = 0f;
    }

    private static void finish(Minecraft mc) {
        if (mc.player != null && hidGui) {
            mc.player.setYRot(savedYaw);
            mc.player.yRotO = savedYaw;
            mc.player.setYHeadRot(savedYaw);
            mc.player.yHeadRotO = savedYaw;
            mc.player.setXRot(savedPitch);
            mc.player.xRotO = savedPitch;
        }
        abort(mc);
    }

    private static void abort(Minecraft mc) {
        if (hidGui) mc.options.hideGui = false;
        hidGui = false;
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

    public static void clear() {
        for (Cached cached : CACHE.values()) release(cached);
        CACHE.clear();
        pendingKey = null;
        pendingDirection = -1;
        hidGui = false;
    }
}

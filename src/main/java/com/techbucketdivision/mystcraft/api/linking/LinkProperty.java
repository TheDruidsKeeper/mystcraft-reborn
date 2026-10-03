package com.techbucketdivision.mystcraft.api.linking;

import com.mojang.serialization.Codec;
import com.techbucketdivision.mystcraft.util.Colors;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A link property (flag). Built-ins mirror the original names; add-ons may register more via {@link #register}.
 * Serialized by {@link #name()}.
 */
public final class LinkProperty {
    private static final Map<String, LinkProperty> REGISTRY = new LinkedHashMap<>();

    public static final LinkProperty INTRA_LINKING = register("intra_linking", new Colors.RGB(0, 1, 0), true);
    public static final LinkProperty INTRA_LINKING_ONLY = register("intra_linking_only", new Colors.RGB(1, 1, 1), true);
    public static final LinkProperty RELATIVE = register("relative", new Colors.RGB(0.6f, 0, 0.6f), true);
    public static final LinkProperty DISARM = register("disarm", new Colors.RGB(1, 0, 0), true);
    public static final LinkProperty MAINTAIN_MOMENTUM = register("maintain_momentum", new Colors.RGB(0, 0, 1), true);
    public static final LinkProperty GENERATE_PLATFORM = register("generate_platform", new Colors.RGB(0.5f, 0.5f, 0.5f), true);
    public static final LinkProperty NATURAL = register("natural", null, false);
    public static final LinkProperty EXTERNAL = register("external", null, false);
    public static final LinkProperty OFFENSIVE = register("offensive", null, false);
    public static final LinkProperty OP_TP = register("op_tp", null, false);
    /** Reborn: inkable (gold colour) so the Link Modifier and Ink Mixer can set it; the book travels with the linker. */
    public static final LinkProperty FOLLOWING = register("following", new Colors.RGB(0.9f, 0.75f, 0.1f), true);

    /** Property key used for the "Sound" link prop. */
    public static final String PROP_SOUND = "sound";
    /** Property key used for the Descriptive Book seed. */
    public static final String PROP_SEED = "seed";

    public static final Codec<LinkProperty> CODEC = Codec.STRING.xmap(LinkProperty::getOrCreate, LinkProperty::name);
    public static final StreamCodec<ByteBuf, LinkProperty> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(LinkProperty::getOrCreate, LinkProperty::name);

    private final String name;
    private final Colors.@Nullable RGB color;
    private final boolean inkable;

    private LinkProperty(String name, Colors.@Nullable RGB color, boolean inkable) {
        this.name = name;
        this.color = color;
        this.inkable = inkable;
    }

    /**
     * @param color   colour in the ink mixer gradient, or {@code null} for informational flags
     * @param inkable whether the Ink Mixer can produce this property (relative is inkable but not craftable — see
     *                {@code InkEffects})
     */
    public static synchronized LinkProperty register(String name, Colors.@Nullable RGB color, boolean inkable) {
        String key = name.toLowerCase(Locale.ROOT);
        LinkProperty existing = REGISTRY.get(key);
        if (existing != null) return existing;
        LinkProperty p = new LinkProperty(key, color, inkable);
        REGISTRY.put(key, p);
        return p;
    }

    public static synchronized LinkProperty getOrCreate(String name) {
        LinkProperty p = REGISTRY.get(name.toLowerCase(Locale.ROOT));
        return p != null ? p : register(name, null, false);
    }

    public static @Nullable LinkProperty get(String name) {
        return REGISTRY.get(name.toLowerCase(Locale.ROOT));
    }

    public static Map<String, LinkProperty> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    public String name() {
        return name;
    }

    public Colors.@Nullable RGB color() {
        return color;
    }

    /** Whether the ink mixer can attach this property to a link panel. */
    public boolean inkable() {
        return inkable;
    }

    public String descriptionId() {
        return "link_property.mystcraft." + name;
    }

    @Override
    public String toString() {
        return name;
    }
}

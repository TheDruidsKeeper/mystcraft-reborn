package com.techbucketdivision.mystcraft.api.symbol;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * What a modifier symbol supplies and what a primary symbol takes: the pending-modifier channels of
 * {@link AgeDirector} named for players. A modifier page can be attached to a page whose symbol accepts the slot the
 * modifier fills (world-building plan §3, §4).
 */
public enum ModifierSlot {
    DIRECTION(Modifier.ANGLE),
    PHASE(Modifier.PHASE),
    LENGTH(Modifier.FACTOR),
    COLOR(Modifier.COLOR),
    GRADIENT(Modifier.GRADIENT),
    SUNSET(Modifier.SUNSET),
    /** A block (material); {@link AgeSymbol#blockCategories()} says which block categories are involved. */
    BLOCK(Modifier.BLOCKLIST);

    private final String modifierId;

    ModifierSlot(String modifierId) {
        this.modifierId = modifierId;
    }

    /** The {@link AgeDirector} modifier id this slot stands for. */
    public String modifierId() {
        return modifierId;
    }

    public static @Nullable ModifierSlot byModifierId(String id) {
        for (ModifierSlot s : values()) if (s.modifierId.equals(id)) return s;
        return null;
    }

    public String descriptionId() {
        return "modifier_slot.mystcraft." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable(descriptionId());
    }
}

package com.techbucketdivision.mystcraft.linking;

import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.entity.LinkbookEntity;
import com.techbucketdivision.mystcraft.registry.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Link sound selection (original spec §7.3): volume 0.8, pitch 0.9–1.1, category PLAYERS. */
public final class LinkSounds {
    private LinkSounds() {}

    public static final float VOLUME = 0.8f;

    /** Picks the sound for an entity travelling with {@code info}. */
    public static SoundEvent select(Entity entity, LinkInfo info) {
        if (entity instanceof ItemEntity || entity instanceof LinkbookEntity) return ModSounds.LINK_POP.value();
        if (info.hasFlag(LinkProperty.DISARM)) return ModSounds.LINK_DISARM.value();
        String custom = info.prop(LinkProperty.PROP_SOUND);
        if (custom != null) {
            SoundEvent event = byId(custom);
            if (event != null) return event;
        }
        if (info.hasFlag(LinkProperty.FOLLOWING)) return ModSounds.LINK_FOLLOWING.value();
        if (info.hasFlag(LinkProperty.INTRA_LINKING)) return ModSounds.LINK_INTRA.value();
        return ModSounds.LINK.value();
    }

    public static @Nullable SoundEvent byId(String id) {
        Identifier identifier = Identifier.tryParse(id);
        return identifier == null ? null : BuiltInRegistries.SOUND_EVENT.getValue(identifier);
    }

    /** Plays the link sound at the entity's current position in its current level. */
    public static void play(Entity entity, LinkInfo info) {
        play(entity.level(), entity.getX(), entity.getY(), entity.getZ(), select(entity, info));
    }

    public static void play(Level level, double x, double y, double z, SoundEvent sound) {
        float pitch = level.getRandom().nextFloat() * 0.2f + 0.9f;
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, VOLUME, pitch);
    }
}

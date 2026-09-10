package com.techbucketdivision.mystcraft.registry;

import com.techbucketdivision.mystcraft.Mystcraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sound events (REQUIREMENTS §18). Ids use dots like the original so lang/sounds.json keys stay familiar. */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Mystcraft.MOD_ID);

    public static final Holder<SoundEvent> LINK_POP = SOUNDS.register("linking.pop", SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> LINK = SOUNDS.register("linking.link", SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> LINK_DISARM = SOUNDS.register("linking.link_disarm", SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> LINK_FOLLOWING = SOUNDS.register("linking.link_following", SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> LINK_INTRA = SOUNDS.register("linking.link_intra", SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> LINK_FISSURE = SOUNDS.register("linking.link_fissure", SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> LINK_PORTAL = SOUNDS.register("linking.link_portal", SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> METEOR_ROAR = SOUNDS.register("entity.meteor.roar", SoundEvent::createVariableRangeEvent);
}

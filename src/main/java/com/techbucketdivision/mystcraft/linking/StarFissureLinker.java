package com.techbucketdivision.mystcraft.linking;

import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Star Fissure travel (REQUIREMENTS §3.11): a Natural + External link to the configured home dimension's spawn,
 * keeping the entity's yaw and playing {@code mystcraft:linking.link_fissure}.
 */
public final class StarFissureLinker {
    private StarFissureLinker() {}

    public static final String SOUND = "mystcraft:linking.link_fissure";

    /** Link info used by the fissure for {@code entity}. */
    public static LinkInfo linkInfo(Entity entity) {
        return LinkInfo.EMPTY
                .withDimension(MystcraftConfig.homeDimension())
                .withFlag(LinkProperty.NATURAL, true)
                .withFlag(LinkProperty.EXTERNAL, true)
                .withProp(LinkProperty.PROP_SOUND, SOUND)
                .withYaw(entity.getYRot());
    }

    /**
     * Called from the fissure block's entity collision. Applies the vanilla portal cooldown so an entity standing in
     * the fissure does not link every tick.
     *
     * @return whether the entity travelled
     */
    public static boolean link(ServerLevel level, Entity entity) {
        if (level.isClientSide()) return false;
        if (entity.isOnPortalCooldown() || entity.isPassenger()) return false;
        boolean moved = LinkController.travelEntity(entity, linkInfo(entity));
        if (moved) entity.setPortalCooldown();
        return moved;
    }
}

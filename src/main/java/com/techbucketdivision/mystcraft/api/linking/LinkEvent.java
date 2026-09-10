package com.techbucketdivision.mystcraft.api.linking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jspecify.annotations.Nullable;

/** Events posted on {@code NeoForge.EVENT_BUS} during a link (see REQUIREMENTS §7.3). */
public abstract class LinkEvent extends Event {
    private final ServerLevel origin;
    private final Entity entity;
    private final LinkInfo info;

    protected LinkEvent(ServerLevel origin, Entity entity, LinkInfo info) {
        this.origin = origin;
        this.entity = entity;
        this.info = info;
    }

    public ServerLevel getOrigin() {
        return origin;
    }

    public Entity getEntity() {
        return entity;
    }

    public LinkInfo getInfo() {
        return info;
    }

    /** Cancel to refuse the link. */
    public static final class Allow extends LinkEvent implements ICancellableEvent {
        public Allow(ServerLevel origin, Entity entity, LinkInfo info) {
            super(origin, entity, info);
        }
    }

    /** Listeners may change the destination position / yaw. */
    public static final class Alter extends LinkEvent {
        private final ServerLevel destination;
        private @Nullable BlockPos spawn;
        private float yaw;

        public Alter(ServerLevel origin, ServerLevel destination, Entity entity, LinkInfo info, @Nullable BlockPos spawn, float yaw) {
            super(origin, entity, info);
            this.destination = destination;
            this.spawn = spawn;
            this.yaw = yaw;
        }

        public ServerLevel getDestination() {
            return destination;
        }

        public @Nullable BlockPos getSpawn() {
            return spawn;
        }

        public void setSpawn(@Nullable BlockPos spawn) {
            this.spawn = spawn;
        }

        public float getYaw() {
            return yaw;
        }

        public void setYaw(float yaw) {
            this.yaw = yaw;
        }
    }

    public static final class Start extends LinkEvent {
        private final ServerLevel destination;

        public Start(ServerLevel origin, ServerLevel destination, Entity entity, LinkInfo info) {
            super(origin, entity, info);
            this.destination = destination;
        }

        public ServerLevel getDestination() {
            return destination;
        }
    }

    /** Posted after the entity arrived. {@link #getEntity()} is the (possibly new) entity instance. */
    public static final class End extends LinkEvent {
        private final ServerLevel destination;

        public End(ServerLevel origin, ServerLevel destination, Entity entity, LinkInfo info) {
            super(origin, entity, info);
            this.destination = destination;
        }

        public ServerLevel getDestination() {
            return destination;
        }
    }

    public static final class Failed extends LinkEvent {
        private final String reason;

        public Failed(ServerLevel origin, Entity entity, LinkInfo info, String reason) {
            super(origin, entity, info);
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }
}

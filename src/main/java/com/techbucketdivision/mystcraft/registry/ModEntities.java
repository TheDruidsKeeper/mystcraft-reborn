package com.techbucketdivision.mystcraft.registry;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.entity.ColoredLightningBolt;
import com.techbucketdivision.mystcraft.entity.LinkbookEntity;
import com.techbucketdivision.mystcraft.entity.MeteorEntity;
import com.techbucketdivision.mystcraft.entity.MystFallingBlockEntity;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

/** Entity types (REQUIREMENTS §9). */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Mystcraft.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<LinkbookEntity>> LINKBOOK = register("linkbook", LinkbookEntity::new, MobCategory.MISC,
            b -> b.sized(0.25f, 0.2f).clientTrackingRange(4).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<MystFallingBlockEntity>> FALLING_BLOCK = register("falling_block", MystFallingBlockEntity::new, MobCategory.MISC,
            b -> b.sized(0.98f, 0.98f).clientTrackingRange(1).updateInterval(10));
    public static final DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> METEOR = register("meteor", MeteorEntity::new, MobCategory.MISC,
            b -> b.sized(1f, 1f).clientTrackingRange(12).updateInterval(2).fireImmune().noSave());
    public static final DeferredHolder<EntityType<?>, EntityType<ColoredLightningBolt>> LIGHTNING = register("lightning", ColoredLightningBolt::new, MobCategory.MISC,
            b -> b.sized(0f, 0f).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE).noSave());

    private static <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> register(String name, EntityType.EntityFactory<E> factory,
                                                                                          MobCategory category, UnaryOperator<EntityType.Builder<E>> builder) {
        return ENTITIES.register(name, () -> builder.apply(EntityType.Builder.of(factory, category))
                .build(ResourceKey.create(Registries.ENTITY_TYPE, MystIds.id(name))));
    }
}

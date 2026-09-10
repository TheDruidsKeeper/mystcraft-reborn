package com.techbucketdivision.mystcraft.registry;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.villager.ArchivistShop;
import net.minecraft.core.UUIDUtil;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Data attachments: last-dimension UUID on players, shop inventory on archivists. */
public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Mystcraft.MOD_ID);

    /** UUID of the Age the player was last in (empty = not an Age). Used for dead-dimension ejection on login. */
    public static final Supplier<AttachmentType<Optional<UUID>>> LAST_AGE = ATTACHMENTS.register("last_age",
            () -> AttachmentType.<Optional<UUID>>builder(() -> Optional.empty())
                    .serialize(UUIDUtil.CODEC.optionalFieldOf("uuid")).copyOnDeath().build());

    public static final Supplier<AttachmentType<ArchivistShop>> ARCHIVIST_SHOP = ATTACHMENTS.register("archivist_shop",
            () -> AttachmentType.builder(ArchivistShop::new).serialize(ArchivistShop.CODEC.fieldOf("shop")).build());
}

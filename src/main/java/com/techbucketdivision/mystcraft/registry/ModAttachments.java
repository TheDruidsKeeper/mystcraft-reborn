package com.techbucketdivision.mystcraft.registry;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.villager.ArchivistShop;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Data attachments: last-dimension UUID and symbol knowledge on players, shop inventory on archivists. */
public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Mystcraft.MOD_ID);

    /** UUID of the Age the player was last in (empty = not an Age). Used for dead-dimension ejection on login. */
    public static final Supplier<AttachmentType<Optional<UUID>>> LAST_AGE = ATTACHMENTS.register("last_age",
            () -> AttachmentType.<Optional<UUID>>builder(() -> Optional.empty())
                    .serialize(UUIDUtil.CODEC.optionalFieldOf("uuid")).copyOnDeath().build());

    /** Symbols the player knows how to write (world-building plan §4); kept over death, synced to the client. */
    public static final Supplier<AttachmentType<Set<Identifier>>> KNOWLEDGE = ATTACHMENTS.register("knowledge",
            () -> AttachmentType.<Set<Identifier>>builder(() -> Set.of())
                    .serialize(Identifier.CODEC.listOf().xmap(l -> (Set<Identifier>) new LinkedHashSet<>(l), List::copyOf).fieldOf("symbols"))
                    .copyOnDeath().build());

    public static final Supplier<AttachmentType<ArchivistShop>> ARCHIVIST_SHOP = ATTACHMENTS.register("archivist_shop",
            () -> AttachmentType.builder(ArchivistShop::new).serialize(ArchivistShop.CODEC.fieldOf("shop")).build());
}

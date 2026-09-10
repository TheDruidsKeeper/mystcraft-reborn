package com.techbucketdivision.mystcraft.registry;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.blockentity.BookBinderBlockEntity;
import com.techbucketdivision.mystcraft.blockentity.BookDisplayBlockEntity;
import com.techbucketdivision.mystcraft.blockentity.BookReceptacleBlockEntity;
import com.techbucketdivision.mystcraft.blockentity.InkMixerBlockEntity;
import com.techbucketdivision.mystcraft.blockentity.LinkModifierBlockEntity;
import com.techbucketdivision.mystcraft.blockentity.StarFissureBlockEntity;
import com.techbucketdivision.mystcraft.blockentity.WritingDeskBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity types. Every block entity class has the constructor {@code (BlockPos, BlockState)}. */
public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mystcraft.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InkMixerBlockEntity>> INK_MIXER = BLOCK_ENTITIES.register("ink_mixer",
            () -> new BlockEntityType<>(InkMixerBlockEntity::new, ModBlocks.INK_MIXER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BookBinderBlockEntity>> BOOK_BINDER = BLOCK_ENTITIES.register("book_binder",
            () -> new BlockEntityType<>(BookBinderBlockEntity::new, ModBlocks.BOOK_BINDER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BookReceptacleBlockEntity>> BOOK_RECEPTACLE = BLOCK_ENTITIES.register("book_receptacle",
            () -> new BlockEntityType<>(BookReceptacleBlockEntity::new, ModBlocks.BOOK_RECEPTACLE.get()));
    /** Shared by bookstand and lectern. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BookDisplayBlockEntity>> BOOK_DISPLAY = BLOCK_ENTITIES.register("book_display",
            () -> new BlockEntityType<>(BookDisplayBlockEntity::new, ModBlocks.BOOKSTAND.get(), ModBlocks.LECTERN.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LinkModifierBlockEntity>> LINK_MODIFIER = BLOCK_ENTITIES.register("link_modifier",
            () -> new BlockEntityType<>(LinkModifierBlockEntity::new, ModBlocks.LINK_MODIFIER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WritingDeskBlockEntity>> WRITING_DESK = BLOCK_ENTITIES.register("writing_desk",
            () -> new BlockEntityType<>(WritingDeskBlockEntity::new, ModBlocks.WRITING_DESK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarFissureBlockEntity>> STAR_FISSURE = BLOCK_ENTITIES.register("star_fissure",
            () -> new BlockEntityType<>(StarFissureBlockEntity::new, ModBlocks.STAR_FISSURE.get()));
}

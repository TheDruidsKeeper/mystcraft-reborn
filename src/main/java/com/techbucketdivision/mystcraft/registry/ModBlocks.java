package com.techbucketdivision.mystcraft.registry;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.block.BookBinderBlock;
import com.techbucketdivision.mystcraft.block.BookReceptacleBlock;
import com.techbucketdivision.mystcraft.block.BookstandBlock;
import com.techbucketdivision.mystcraft.block.CrystalBlock;
import com.techbucketdivision.mystcraft.block.DecayBlock;
import com.techbucketdivision.mystcraft.block.DecayType;
import com.techbucketdivision.mystcraft.block.InkFluidBlock;
import com.techbucketdivision.mystcraft.block.InkMixerBlock;
import com.techbucketdivision.mystcraft.block.LecternBlock;
import com.techbucketdivision.mystcraft.block.LinkModifierBlock;
import com.techbucketdivision.mystcraft.block.LinkPortalBlock;
import com.techbucketdivision.mystcraft.block.StarFissureBlock;
import com.techbucketdivision.mystcraft.block.WritingDeskBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/** All blocks (REQUIREMENTS §3.1). Block classes take {@code BlockBehaviour.Properties} only. */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mystcraft.MOD_ID);

    public static final DeferredBlock<InkMixerBlock> INK_MIXER = BLOCKS.registerBlock("ink_mixer", InkMixerBlock::new,
            p -> p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2f, 2f).noOcclusion());
    public static final DeferredBlock<BookBinderBlock> BOOK_BINDER = BLOCKS.registerBlock("book_binder", BookBinderBlock::new,
            p -> p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2f, 2f));
    public static final DeferredBlock<BookReceptacleBlock> BOOK_RECEPTACLE = BLOCKS.registerBlock("book_receptacle", BookReceptacleBlock::new,
            p -> p.mapColor(MapColor.QUARTZ).sound(SoundType.GLASS).strength(1f).noOcclusion());
    public static final DeferredBlock<BookstandBlock> BOOKSTAND = BLOCKS.registerBlock("bookstand", BookstandBlock::new,
            p -> p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2f, 2f).noOcclusion());
    public static final DeferredBlock<LecternBlock> LECTERN = BLOCKS.registerBlock("lectern", LecternBlock::new,
            p -> p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2f, 2f).noOcclusion());
    public static final DeferredBlock<LinkModifierBlock> LINK_MODIFIER = BLOCKS.registerBlock("link_modifier", LinkModifierBlock::new,
            p -> p.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(2f, 2f));
    public static final DeferredBlock<CrystalBlock> CRYSTAL = BLOCKS.registerBlock("crystal", CrystalBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).sound(SoundType.GLASS).strength(1f).lightLevel(s -> 8).noOcclusion().requiresCorrectToolForDrops());
    public static final DeferredBlock<LinkPortalBlock> LINK_PORTAL = BLOCKS.registerBlock("link_portal", LinkPortalBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLUE).sound(SoundType.GLASS).strength(-1f, 3600000f).lightLevel(s -> 11)
                    .noCollision().noOcclusion().noLootTable().randomTicks().pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<WritingDeskBlock> WRITING_DESK = BLOCKS.registerBlock("writing_desk", WritingDeskBlock::new,
            p -> p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2.5f).noOcclusion().noLootTable());
    public static final DeferredBlock<StarFissureBlock> STAR_FISSURE = BLOCKS.registerBlock("star_fissure", StarFissureBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(-1f, 3600000f).lightLevel(s -> 6).noCollision().noOcclusion()
                    .noLootTable().pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<InkFluidBlock> BLACK_INK = BLOCKS.registerBlock("black_ink", InkFluidBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).replaceable().noCollision().strength(100f).liquid().noLootTable().pushReaction(PushReaction.DESTROY));

    public static final Map<DecayType, DeferredBlock<DecayBlock>> DECAY = new EnumMap<>(DecayType.class);

    static {
        for (DecayType type : DecayType.values()) {
            DECAY.put(type, BLOCKS.registerBlock("decay_" + type.getSerializedName(),
                    props -> new DecayBlock(type, props),
                    p -> p.mapColor(type.mapColor()).sound(SoundType.SAND).strength(type.hardness(), type.explosionResistance()).noLootTable().randomTicks()));
        }
    }

    public static DeferredBlock<DecayBlock> decay(DecayType type) {
        return DECAY.get(type);
    }

    /** Ensure static init runs. */
    public static void init() {}
}

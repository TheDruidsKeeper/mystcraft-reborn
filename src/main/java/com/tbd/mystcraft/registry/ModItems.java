package com.tbd.mystcraft.registry;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.block.DecayType;
import com.tbd.mystcraft.item.BoosterItem;
import com.tbd.mystcraft.item.DecayBlockItem;
import com.tbd.mystcraft.item.DescriptiveBookItem;
import com.tbd.mystcraft.item.FolderItem;
import com.tbd.mystcraft.item.InkVialItem;
import com.tbd.mystcraft.item.LinkingBookItem;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.item.UnlinkedBookItem;
import com.tbd.mystcraft.item.WritingDeskItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/** All items (original spec §2.1). Item classes take {@code Item.Properties} only. */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mystcraft.MOD_ID);

    public static final DeferredItem<PageItem> PAGE = ITEMS.registerItem("page", PageItem::new, p -> p.stacksTo(64));
    public static final DeferredItem<DescriptiveBookItem> DESCRIPTIVE_BOOK = ITEMS.registerItem("descriptive_book", DescriptiveBookItem::new,
            p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<LinkingBookItem> LINKING_BOOK = ITEMS.registerItem("linking_book", LinkingBookItem::new,
            p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<UnlinkedBookItem> UNLINKED_BOOK = ITEMS.registerItem("unlinked_book", UnlinkedBookItem::new,
            p -> p.stacksTo(16));
    public static final DeferredItem<BoosterItem> SEALED_NOTEBOOK = ITEMS.registerItem("sealed_notebook", BoosterItem::new,
            p -> p.stacksTo(64));
    public static final DeferredItem<FolderItem> COLLATION_FOLDER = ITEMS.registerItem("collation_folder", FolderItem::new,
            p -> p.stacksTo(32).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<InkVialItem> INK_VIAL = ITEMS.registerItem("ink_vial", InkVialItem::new,
            p -> p.stacksTo(16).craftRemainder(Items.GLASS_BOTTLE));
    public static final DeferredItem<WritingDeskItem> WRITING_DESK = ITEMS.registerItem("writing_desk",
            p -> new WritingDeskItem(false, p), p -> p.stacksTo(64));
    /** Creative only (no recipe): places a desk that offers every registered symbol. */
    public static final DeferredItem<WritingDeskItem> SCHOLARS_WRITING_DESK = ITEMS.registerItem("scholars_writing_desk",
            p -> new WritingDeskItem(true, p), p -> p.stacksTo(64).rarity(Rarity.EPIC));

    // Block items
    public static final DeferredItem<BlockItem> INK_MIXER = ITEMS.registerSimpleBlockItem("ink_mixer", ModBlocks.INK_MIXER);
    public static final DeferredItem<BlockItem> BOOK_BINDER = ITEMS.registerSimpleBlockItem("book_binder", ModBlocks.BOOK_BINDER);
    public static final DeferredItem<BlockItem> BOOK_RECEPTACLE = ITEMS.registerSimpleBlockItem("book_receptacle", ModBlocks.BOOK_RECEPTACLE);
    public static final DeferredItem<BlockItem> BOOKSTAND = ITEMS.registerSimpleBlockItem("bookstand", ModBlocks.BOOKSTAND);
    public static final DeferredItem<BlockItem> LECTERN = ITEMS.registerSimpleBlockItem("lectern", ModBlocks.LECTERN);
    public static final DeferredItem<BlockItem> LINK_MODIFIER = ITEMS.registerSimpleBlockItem("link_modifier", ModBlocks.LINK_MODIFIER);
    public static final DeferredItem<BlockItem> WARDED_DOOR = ITEMS.registerSimpleBlockItem("warded_door", ModBlocks.WARDED_DOOR);
    public static final DeferredItem<BlockItem> SYMBOL_ALTAR = ITEMS.registerSimpleBlockItem("symbol_altar", ModBlocks.SYMBOL_ALTAR);
    public static final DeferredItem<BlockItem> OFFERING_PEDESTAL = ITEMS.registerSimpleBlockItem("offering_pedestal", ModBlocks.OFFERING_PEDESTAL);
    public static final DeferredItem<BlockItem> SEQUENCE_DIAL = ITEMS.registerSimpleBlockItem("sequence_dial", ModBlocks.SEQUENCE_DIAL);
    public static final DeferredItem<BlockItem> FACILITY_CACHE = ITEMS.registerSimpleBlockItem("facility_cache", ModBlocks.FACILITY_CACHE);
    public static final DeferredItem<BlockItem> CRYSTAL = ITEMS.registerSimpleBlockItem("crystal", ModBlocks.CRYSTAL);
    public static final DeferredItem<BlockItem> LINK_PORTAL = ITEMS.registerSimpleBlockItem("link_portal", ModBlocks.LINK_PORTAL);
    public static final DeferredItem<BlockItem> STAR_FISSURE = ITEMS.registerSimpleBlockItem("star_fissure", ModBlocks.STAR_FISSURE);

    public static final Map<DecayType, DeferredItem<DecayBlockItem>> DECAY = new EnumMap<>(DecayType.class);

    static {
        for (DecayType type : DecayType.values()) {
            DECAY.put(type, ITEMS.registerItem("decay_" + type.getSerializedName(),
                    p -> new DecayBlockItem(ModBlocks.decay(type).get(), type, p), p -> p.stacksTo(64)));
        }
    }

    public static Item decay(DecayType type) {
        return DECAY.get(type).get();
    }

    public static void init() {}
}

package com.techbucketdivision.mystcraft.item;

import com.techbucketdivision.mystcraft.block.DecayType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Item form of a decay block (REQUIREMENTS §2.1). One item per {@link DecayType}; registry name
 * {@code decay_<type>} so the default description id is {@code item.mystcraft.decay_<type>}.
 */
public class DecayBlockItem extends BlockItem {
    private final DecayType type;

    public DecayBlockItem(Block block, DecayType type, Item.Properties properties) {
        super(block, properties);
        this.type = type;
    }

    public DecayType getDecayType() {
        return type;
    }
}

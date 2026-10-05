package com.techbucketdivision.mystcraft.item;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.symbol.CardRanks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Sealed Notebook (original spec §2.4): right-click opens it into a Collation Folder with random pages. */
public class BoosterItem extends Item {

    public static final int VERY_COMMON = 7;
    public static final int COMMON = 4;
    public static final int UNCOMMON = 4;
    public static final int RARE = 1;

    public BoosterItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        ItemStack folder = generateBooster(player.getRandom());
        if (folder.isEmpty()) return InteractionResult.PASS;
        held.shrink(1);
        if (held.isEmpty()) {
            player.setItemInHand(hand, folder);
        } else if (!player.getInventory().add(folder)) {
            held.grow(1); // inventory full: refund
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** 7 rank-0, 4 rank-1, 4 rank-2 and 1 rank≥3 pages, each chosen by item weight. */
    public static ItemStack generateBooster(RandomSource random) {
        return generateBooster(random, VERY_COMMON, COMMON, UNCOMMON, RARE);
    }

    public static ItemStack generateBooster(RandomSource random, int veryCommon, int common, int uncommon, int rare) {
        List<ItemStack> pages = new ArrayList<>();
        addRandomPages(random, pages, veryCommon, CardRanks.ofRank(0));
        addRandomPages(random, pages, common, CardRanks.ofRank(1));
        addRandomPages(random, pages, uncommon, CardRanks.ofRank(2));
        addRandomPages(random, pages, rare, CardRanks.ofRankAtLeast(3));
        return FolderItem.create("", pages);
    }

    private static void addRandomPages(RandomSource random, List<ItemStack> pages, int count, List<AgeSymbol> candidates) {
        if (candidates.isEmpty()) return;
        for (int i = 0; i < count; i++) {
            AgeSymbol symbol = CardRanks.weightedRandom(candidates, random);
            if (symbol == null) {
                Mystcraft.LOGGER.error("Symbol from random selection null ({} candidates)", candidates.size());
                continue;
            }
            pages.add(PageItem.createSymbolPage(symbol));
        }
    }
}

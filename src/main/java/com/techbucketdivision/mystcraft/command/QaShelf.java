package com.techbucketdivision.mystcraft.command;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.block.LecternBlock;
import com.techbucketdivision.mystcraft.blockentity.BookDisplayBlockEntity;
import com.techbucketdivision.mystcraft.item.DescriptiveBookItem;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.item.component.SymbolPage;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /myst-qa-shelf}: a row of lecterns, each with a Descriptive Book already bound (fixed seed, fixed pages) to an
 * Age that exercises something only a human can judge - the look of sky and world colours, celestial modifiers,
 * weather, terrain features with materials, nether / end terrain, instability effects. Everything structural about
 * these Ages is covered by the headless tests; the shelf is for the eyes. Logged under {@code [qa]}.
 */
public final class QaShelf {
    private QaShelf() {}

    /** One shelf entry: title, seed and the pages the author "wrote" (the blueprint fills the rest deterministically). */
    public record Case(String title, long seed, List<SymbolPage> pages, String lookFor) {
        static Case of(String title, long seed, String lookFor, Object... pages) {
            List<SymbolPage> list = new ArrayList<>();
            for (Object page : pages) {
                if (page instanceof SymbolPage sp) list.add(sp);
                else list.add(SymbolPage.of(MystIds.id((String) page)));
            }
            return new Case(title, seed, list, lookFor);
        }
    }

    private static SymbolPage page(String symbol, String... modifiers) {
        List<Identifier> mods = new ArrayList<>();
        for (String m : modifiers) mods.add(MystIds.id(m));
        return new SymbolPage(MystIds.id(symbol), mods, false);
    }

    /** The QA matrix. Seeds are fixed so a report ("shelf 4 looks wrong") is reproducible. */
    public static List<Case> cases() {
        return List.of(
                Case.of("QA 01 Empty book", 1001L, "a plain, stable Age: terrain, sun, biomes - the blueprint defaults"),
                Case.of("QA 02 Dark sun, bright light", 1002L, "no sun disc, world fully lit (bright lighting), stars visible all day",
                        "sun_dark", "lighting_bright", "stars_normal"),
                Case.of("QA 03 Sky gradient", 1003L, "sky red->blue over the day, fog yellow, night sky purple",
                        page("color_sky", "mod_color_red", "mod_gradient", "mod_color_blue", "mod_gradient"),
                        page("color_fog", "mod_color_yellow"), page("color_sky_night", "mod_color_purple")),
                Case.of("QA 04 World colours", 1004L, "magenta grass, cyan foliage, red water",
                        page("color_grass", "mod_color_magenta"), page("color_foliage", "mod_color_cyan"), page("color_water", "mod_color_red")),
                Case.of("QA 05 Celestial modifiers", 1005L, "sun rising in the west (east direction = 90 deg), half-length day, green sunset; moon zenith phase",
                        page("sun_normal", "mod_east", "mod_half", "mod_color_green", "color_horizon"), page("moon_normal", "mod_noon"),
                        page("stars_twinkle", "mod_double"), "rainbow"),
                Case.of("QA 06 Storm", 1006L, "permanent thunderstorm with lightning effect, dark lighting",
                        "weather_storm", "lighting_dark", "env_lightning"),
                Case.of("QA 07 Flat obsidian, no sea", 1007L, "flat obsidian terrain without any sea, obelisks of glowstone",
                        page("terrain_flat", "block_obsidian", "no_sea"), page("obelisks", "block_glowstone")),
                Case.of("QA 08 Skylands + islands", 1008L, "skylands with floating islands of ice, huge trees, crystal formations",
                        "terrain_normal", "skylands", page("floating_islands", "block_ice"), "huge_trees", "crystal_formations"),
                Case.of("QA 09 Nether age", 1009L, "nether (cave) terrain with nether biomes, a nether fortress, lava sea",
                        page("terrain_nether", "block_lava"), "biome_medium", "biome_minecraft_crimson_forest", "biome_minecraft_nether_wastes", "nether_fortress"),
                Case.of("QA 10 End age", 1010L, "end island terrain, end sky, end biome, spikes",
                        "terrain_end", "stars_end_sky", "biome_single", "biome_minecraft_end_highlands", page("spikes", "block_obsidian")),
                Case.of("QA 11 Tiny biomes", 1011L, "tiny biome patches of desert / jungle / ice spikes side by side, villages and ravines",
                        "biome_tiny", "biome_minecraft_desert", "biome_minecraft_jungle", "biome_minecraft_ice_spikes", "villages", "ravines"),
                Case.of("QA 12 Unstable", 1012L, "meteors + accelerated + explosions: instability symptoms within minutes (decay, crumbling, effects)",
                        "env_meteors", "env_accelerated", "env_explosions", "dense_ores"),
                Case.of("QA 13 Void with star fissure", 1013L, "void terrain (nothing but the arrival platform) with a star fissure to fall into",
                        "terrain_void", "star_fissure"),
                Case.of("QA 14 Amplified deep lakes", 1014L, "amplified terrain, deep lakes of lava, tendrils, cloudy weather, no horizon band",
                        "terrain_amplified", page("lakes_deep", "block_lava"), page("tendrils", "block_nether_bricks"), "weather_cloudy", "no_horizon"));
    }

    /**
     * Builds the shelf in front of the player: one lectern per case, 2 blocks apart, facing the player, each holding
     * a bound book titled with the case. Returns the number of lecterns placed.
     */
    public static int build(ServerLevel level, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        Direction facing = player.getDirection();
        Direction right = facing.getClockWise();
        BlockPos origin = player.blockPosition().relative(facing, 3);
        int count = cases().size();
        BlockPos last = origin.relative(right, (count - 1) * 2);
        DebugScene.clearWithoutDrops(level,
                new BlockPos(Math.min(origin.getX(), last.getX()), origin.getY() - 1, Math.min(origin.getZ(), last.getZ())),
                new BlockPos(Math.max(origin.getX(), last.getX()), origin.getY() + 2, Math.max(origin.getZ(), last.getZ())));
        int placed = 0;
        for (Case qa : cases()) {
            BlockPos pos = origin.relative(right, placed * 2);
            level.setBlock(pos.below(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            level.setBlock(pos, ModBlocks.LECTERN.get().defaultBlockState().setValue(LecternBlock.FACING, facing.getOpposite()), 3);
            ItemStack book = bind(server, qa);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BookDisplayBlockEntity display) {
                display.setBook(book);
                display.setChanged();
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
            }
            AgeData data = DescriptiveBookItem.getAgeData(server, book);
            Mystcraft.LOGGER.info("[qa] shelf {} '{}' seed {} -> {} ({} symbols): look for {}", placed + 1, qa.title(), qa.seed(),
                    data == null ? "?" : data.levelKey().identifier(), data == null ? 0 : data.symbols().size(), qa.lookFor());
            placed++;
        }
        return placed;
    }

    /** An unbound book with the case's pages and seed, bound right away so the Age is fixed and inspectable. */
    private static ItemStack bind(MinecraftServer server, Case qa) {
        List<ItemStack> pages = new ArrayList<>();
        for (SymbolPage page : qa.pages()) pages.add(PageItem.createSymbolPage(page));
        return DescriptiveBookItem.createBound(server, qa.title(), qa.seed(), pages);
    }
}

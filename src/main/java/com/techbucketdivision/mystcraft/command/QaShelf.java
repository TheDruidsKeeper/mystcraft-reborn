package com.tbd.mystcraft.command;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.block.LecternBlock;
import com.tbd.mystcraft.blockentity.BookDisplayBlockEntity;
import com.tbd.mystcraft.item.DescriptiveBookItem;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.item.component.SymbolPage;
import com.tbd.mystcraft.registry.ModBlocks;
import com.tbd.mystcraft.util.MystIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /myst-dev qa-shelf}: the visual QA matrix (docs/QA.md). One lectern per QA world, each holding a Descriptive
 * Book already bound (fixed seed, fixed pages) to an Age that exercises something only a human can judge. Worlds are
 * grouped in sections (one coloured row each, labelled with a sign, walkways all round). Everything block-level about
 * these Ages is asserted by {@code QaWorldTests} on the same seeds; the shelf is for the eyes. Logged under {@code [qa]}.
 */
public final class QaShelf {
    private QaShelf() {}

    /** A section of the matrix: a row of related worlds on a floor of one colour. */
    public record Section(String id, String name, Block floor, List<Case> cases) {}

    /** One QA world: id (section letter + number), title, seed, the pages the author "wrote", what to look for. */
    public record Case(String id, String title, long seed, List<SymbolPage> pages, String lookFor) {
        public static Case of(String id, String title, long seed, String lookFor, Object... pages) {
            List<SymbolPage> list = new ArrayList<>();
            for (Object page : pages) {
                if (page instanceof SymbolPage sp) list.add(sp);
                else list.add(SymbolPage.of(MystIds.id((String) page)));
            }
            return new Case(id, title, seed, list, lookFor);
        }
    }

    private static SymbolPage page(String symbol, String... modifiers) {
        List<Identifier> mods = new ArrayList<>();
        for (String m : modifiers) mods.add(MystIds.id(m));
        return new SymbolPage(MystIds.id(symbol), mods, false);
    }

    /**
     * The QA matrix. Rules: attributes that interfere visually never share a world; orthogonal ones are stacked so
     * each world checks several things; seeds are fixed so a report ("B2 looks wrong") is reproducible.
     */
    public static List<Section> sections() {
        return List.of(
                new Section("A", "Baseline", Blocks.WHITE_CONCRETE, List.of(
                        Case.of("A1", "Empty book", 2001L, "a plain, stable Age: terrain, sun, biomes - the blueprint defaults; nothing odd"))),
                new Section("B", "Sky & celestials", Blocks.LIGHT_BLUE_CONCRETE, List.of(
                        Case.of("B1", "Sky colours", 2011L, "sky red->blue over the day, fog yellow, night sky purple; smooth sunrise/sunset, no flicker",
                                page("color_sky", "mod_color_red", "mod_gradient", "mod_color_blue", "mod_gradient"),
                                page("color_fog", "mod_color_yellow"), page("color_sky_night", "mod_color_purple")),
                        Case.of("B2", "Celestial modifiers", 2012L, "sun rises in the west on a half-length day with a green sunset; moon at zenith phase; stars twinkle at double speed; rainbow arc",
                                page("sun_normal", "mod_east", "mod_half", "mod_color_green", "color_horizon"), page("moon_normal", "mod_noon"),
                                page("stars_twinkle", "mod_double"), "rainbow"),
                        Case.of("B3", "Dark sun, bright light", 2013L, "no sun disc, world fully lit (bright lighting), stars visible all day",
                                "sun_dark", "lighting_bright", "stars_normal"),
                        Case.of("B4", "Dark light, end sky", 2014L, "dark lighting level, end-sky star texture, cloudy weather cover",
                                "lighting_dark", "stars_end_sky", "weather_cloudy"))),
                new Section("C", "World colours & weather", Blocks.LIME_CONCRETE, List.of(
                        Case.of("C1", "World colours", 2021L, "magenta grass, cyan foliage, red water; colours blend at biome borders",
                                page("color_grass", "mod_color_magenta"), page("color_foliage", "mod_color_cyan"), page("color_water", "mod_color_red")),
                        Case.of("C2", "Rain", 2022L, "permanent rain: precipitation visuals, puddle-free ground, darker sky", "weather_rain"),
                        Case.of("C3", "Snow", 2023L, "permanent snowfall: snow layers accumulate, ice forms on water", "weather_snow"),
                        Case.of("C4", "Storm", 2024L, "permanent thunderstorm with the lightning effect; storm sky", "weather_storm", "env_lightning"))),
                new Section("D", "Terrain & features", Blocks.ORANGE_CONCRETE, List.of(
                        Case.of("D1", "Flat, no sea, ravines", 2031L, "flat stone plane without any sea, split by ravines, obelisks of glowstone (silhouettes at dusk)",
                                page("terrain_flat", "no_sea"), "ravines", page("obelisks", "block_glowstone")),
                        Case.of("D2", "Skylands + islands", 2032L, "skylands with floating islands of ice, huge trees, crystal formations: island shapes, tree scale, crystal clusters",
                                "terrain_normal", "skylands", page("floating_islands", "block_ice"), "huge_trees", "crystal_formations"),
                        Case.of("D3", "Amplified deep lakes", 2033L, "amplified cliffs, deep lakes of lava, tendrils of nether bricks, no horizon band",
                                "terrain_amplified", page("lakes_deep", "block_lava"), page("tendrils", "block_nether_bricks"), "no_horizon"),
                        Case.of("D4", "Nether age", 2034L, "nether (cave) terrain with nether biomes, deep lava lakes, a nether fortress integrated into the caves",
                                "terrain_nether", page("lakes_deep", "block_lava"), "biome_medium", "biome_minecraft_crimson_forest", "biome_minecraft_nether_wastes", "nether_fortress"),
                        Case.of("D5", "End age", 2035L, "end island terrain, end biome, obsidian spikes: island edge, spike shapes",
                                "terrain_end", "biome_single", "biome_minecraft_end_highlands", page("spikes", "block_obsidian")),
                        Case.of("D6", "Void with star fissure", 2036L, "void terrain (nothing but the arrival platform) with a star fissure visible from the platform",
                                "terrain_void", "star_fissure"))),
                new Section("E", "Biomes & structures", Blocks.YELLOW_CONCRETE, List.of(
                        Case.of("E1", "Tiny biomes", 2041L, "tiny patches of desert / jungle / ice spikes side by side with villages: patchwork look, village placement",
                                "biome_tiny", "biome_minecraft_desert", "biome_minecraft_jungle", "biome_minecraft_ice_spikes", "villages"),
                        Case.of("E2", "Large biomes + Facility", 2042L, "large biome scale; the Facility entrance in view 60-120 blocks from arrival, sitting on the terrain, not floating or buried",
                                "biome_large", "vault"))),
                new Section("F", "Creatures", Blocks.RED_CONCRETE, List.of(
                        Case.of("F1", "Brutal hostile swarm", 2051L, "monsters spawn 4x as often, 4x the usual number, double health and hit hard; passives sparse: night pressure",
                                page("creatures_hostile", "mod_rate_swarm", "mod_cap_horde", "mod_difficulty_brutal"), page("creatures_passive", "mod_rate_sparse", "mod_cap_few")),
                        Case.of("F2", "Peaceful meadow", 2052L, "no hostiles ever, dense animals; neutrals normal",
                                page("creatures_hostile", "mod_rate_none"), page("creatures_passive", "mod_rate_dense", "mod_cap_many"), "creatures_neutral"),
                        Case.of("F3", "Lifeless", 2053L, "no creature spawns naturally at all (watch at night)", "creatures_none"))),
                new Section("G", "Instability", Blocks.PURPLE_CONCRETE, List.of(
                        Case.of("G1", "Unstable", 2061L, "meteors + accelerated + explosions: instability symptoms within minutes (decay spread, crumbling, meteor visuals, effect pacing)",
                                "env_meteors", "env_accelerated", "env_explosions", "dense_ores"))));
    }

    /** All cases in shelf order (for tests and the client smoke tour). */
    public static List<Case> cases() {
        List<Case> all = new ArrayList<>();
        for (Section s : sections()) all.addAll(s.cases());
        return all;
    }

    // Layout (in blocks): a section row is 3 deep (walkway, lectern line, walkway) on its coloured floor, then a
    // stone-brick path; lecterns 2 apart on 1-high pedestals; the section sign stands at the row's left end.
    private static final int ROW_PITCH = 4;
    private static final int LECTERN_PITCH = 2;

    /**
     * Builds the shelf in front of the player: sections as rows going away from the player, lecterns left to right.
     * Clears only its own footprint. Returns the number of lecterns placed.
     */
    public static int build(ServerLevel level, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        Direction facing = player.getDirection();
        Direction right = facing.getClockWise();
        List<Section> sections = sections();
        int widest = Math.max(2, sections.stream().mapToInt(s -> s.cases().size()).max().orElse(1));
        int width = (widest - 1) * LECTERN_PITCH + 3;          // floor: one block each side of the outer lecterns
        int depth = sections.size() * ROW_PITCH;
        BlockPos origin = player.blockPosition().relative(facing, 2).relative(right, -1); // front-left floor corner
        BlockPos farCorner = origin.relative(facing, depth - 1).relative(right, width);
        int y = origin.getY();
        DebugScene.clearWithoutDrops(level,
                new BlockPos(Math.min(origin.getX(), farCorner.getX()), y - 1, Math.min(origin.getZ(), farCorner.getZ())),
                new BlockPos(Math.max(origin.getX(), farCorner.getX()), y + 3, Math.max(origin.getZ(), farCorner.getZ())));

        int placed = 0;
        for (int s = 0; s < sections.size(); s++) {
            Section section = sections.get(s);
            BlockPos rowStart = origin.relative(facing, s * ROW_PITCH);
            // Floor: 3 deep of the section colour, then one row of path.
            for (int d = 0; d < ROW_PITCH; d++) {
                BlockState floor = d < 3 ? section.floor().defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                for (int w = -1; w < width; w++) {
                    level.setBlock(rowStart.relative(facing, d).relative(right, w).below(), floor, 3);
                }
            }
            BlockPos lecternLine = rowStart.relative(facing, 1);
            // Section sign at the left end of the lectern line, facing the player.
            placeStandingSign(level, lecternLine.relative(right, -1), facing.getOpposite(),
                    "Section " + section.id(), section.name(), "", "");
            for (int i = 0; i < section.cases().size(); i++) {
                Case qa = section.cases().get(i);
                BlockPos pedestal = lecternLine.relative(right, 1 + i * LECTERN_PITCH);
                level.setBlock(pedestal, Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
                BlockPos lectern = pedestal.above();
                level.setBlock(lectern, ModBlocks.LECTERN.get().defaultBlockState().setValue(LecternBlock.FACING, facing.getOpposite()), 3);
                ItemStack book = bind(server, qa);
                BlockEntity be = level.getBlockEntity(lectern);
                if (be instanceof BookDisplayBlockEntity display) {
                    display.setBook(book);
                    display.setChanged();
                    level.sendBlockUpdated(lectern, level.getBlockState(lectern), level.getBlockState(lectern), Block.UPDATE_ALL);
                }
                String[] lines = wrap(qa.title(), 3);
                placeWallSign(level, pedestal.relative(facing, -1), facing.getOpposite(), qa.id(), lines[0], lines[1], lines[2]);
                AgeData data = DescriptiveBookItem.getAgeData(server, book);
                Mystcraft.LOGGER.info("[qa] {} '{}' seed {} -> {} ({} symbols): look for {}", qa.id(), qa.title(), qa.seed(),
                        data == null ? "?" : data.levelKey().identifier(), data == null ? 0 : data.symbols().size(), qa.lookFor());
                placed++;
            }
            if (s == 0) {
                // Next to the baseline: a Linking Book back to the shelf (intra-linking + following) for the return trip.
                BlockPos pedestal = lecternLine.relative(right, 1 + section.cases().size() * LECTERN_PITCH);
                level.setBlock(pedestal, Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
                BlockPos lectern = pedestal.above();
                level.setBlock(lectern, ModBlocks.LECTERN.get().defaultBlockState().setValue(LecternBlock.FACING, facing.getOpposite()), 3);
                if (level.getBlockEntity(lectern) instanceof BookDisplayBlockEntity display) {
                    display.setBook(DebugScene.homeBook(player, "Back to the shelf"));
                    display.setChanged();
                    level.sendBlockUpdated(lectern, level.getBlockState(lectern), level.getBlockState(lectern), Block.UPDATE_ALL);
                }
                placeWallSign(level, pedestal.relative(facing, -1), facing.getOpposite(), "HOME", "Linking Book", "back to the", "shelf");
            }
        }
        return placed;
    }

    /** An unbound book with the case's pages and seed, bound right away so the Age is fixed and inspectable. */
    public static ItemStack bind(MinecraftServer server, Case qa) {
        List<ItemStack> pages = new ArrayList<>();
        for (SymbolPage page : qa.pages()) pages.add(PageItem.createSymbolPage(page));
        return DescriptiveBookItem.createBound(server, qa.id() + " " + qa.title(), qa.seed(), pages);
    }

    private static void placeStandingSign(ServerLevel level, BlockPos pos, Direction toward, String... lines) {
        int rotation = switch (toward) {
            case SOUTH -> 0;
            case WEST -> 4;
            case NORTH -> 8;
            default -> 12;
        };
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation), 3);
        writeSign(level, pos, lines);
    }

    private static void placeWallSign(ServerLevel level, BlockPos pos, Direction facing, String... lines) {
        level.setBlock(pos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing), 3);
        writeSign(level, pos, lines);
    }

    private static void writeSign(ServerLevel level, BlockPos pos, String... lines) {
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = new SignText();
            for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, Component.literal(lines[i]));
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
        }
    }

    /** Greedy word wrap into {@code n} lines of at most 15 characters (a sign line). */
    private static String[] wrap(String text, int n) {
        String[] out = new String[n];
        java.util.Arrays.fill(out, "");
        int line = 0;
        for (String word : text.split(" ")) {
            if (line >= n) break;
            if (!out[line].isEmpty() && out[line].length() + 1 + word.length() > 15) {
                line++;
                if (line >= n) break;
            }
            out[line] = out[line].isEmpty() ? word : out[line] + " " + word;
        }
        return out;
    }
}

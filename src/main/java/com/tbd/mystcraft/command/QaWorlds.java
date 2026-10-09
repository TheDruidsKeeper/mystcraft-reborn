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
import net.minecraft.world.entity.decoration.ItemFrame;
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
 * {@code /myst-dev qa-worlds}: the visual QA matrix (docs/QA.md). One lectern per QA world, each holding a Descriptive
 * Book already bound (fixed seed, fixed pages) to an Age that exercises something only a human can judge. Worlds are
 * grouped in sections (one coloured row each, labelled with a sign, walkways all round). Everything block-level about
 * these Ages is asserted by {@code QaWorldTests} on the same seeds; the worlds are for the eyes. Logged under {@code [qa]}.
 */
public final class QaWorlds {
    private QaWorlds() {}

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
                        // weather_off: stop blueprint fill from injecting cycling weather into screenshot regression.
                        Case.of("A1", "Empty book", 2001L, "a plain, stable Age: terrain, sun, biomes - the blueprint defaults; nothing odd",
                                "weather_off"))),
                new Section("B", "Sky & celestials", Blocks.LIGHT_BLUE_CONCRETE, List.of(
                        Case.of("B1", "Sky colours", 2011L, "sky red->blue over the day, fog yellow, night sky purple; smooth sunrise/sunset, no flicker",
                                page("color_sky", "mod_color_red", "mod_gradient", "mod_color_blue", "mod_gradient"),
                                page("color_fog", "mod_color_yellow"), page("color_sky_night", "mod_color_purple"), "weather_off"),
                        Case.of("B2", "Celestial modifiers", 2012L, "sun rises in the west on a half-length day with a green sunset; moon at zenith phase; stars twinkle at double speed; rainbow arc",
                                page("sun_normal", "mod_east", "mod_half", "mod_color_green", "color_horizon"), page("moon_normal", "mod_noon"),
                                page("stars_twinkle", "mod_double"), "rainbow", "weather_off"),
                        Case.of("B3", "Dark sun, bright light", 2013L, "no sun disc, world fully lit (bright lighting), stars visible all day",
                                "sun_dark", "lighting_bright", "stars_normal", "weather_off"),
                        Case.of("B4", "Dark light, end sky", 2014L, "dark lighting level, end-sky star texture, cloudy weather cover",
                                "lighting_dark", "stars_end_sky", "weather_cloudy"))),
                new Section("C", "World colours & weather", Blocks.LIME_CONCRETE, List.of(
                        Case.of("C1", "World colours", 2021L, "magenta grass, cyan foliage, red water; colours blend at biome borders",
                                page("color_grass", "mod_color_magenta"), page("color_foliage", "mod_color_cyan"), page("color_water", "mod_color_red"),
                                "weather_off"),
                        Case.of("C2", "Rain", 2022L, "permanent rain: precipitation visuals, puddle-free ground, darker sky", "weather_rain"),
                        Case.of("C3", "Snow", 2023L, "permanent snowfall: snow layers accumulate, ice forms on water", "weather_snow"),
                        Case.of("C4", "Storm", 2024L, "permanent thunderstorm with the lightning effect; storm sky", "weather_storm", "env_lightning"))),
                new Section("D", "Terrain & features", Blocks.ORANGE_CONCRETE, List.of(
                        Case.of("D1", "Flat, no sea, ravines", 2031L, "flat stone plane without any sea, split by ravines, obelisks of glowstone (silhouettes at dusk)",
                                page("terrain_flat", "no_sea"), "ravines", page("obelisks", "block_glowstone"), "weather_off"),
                        Case.of("D2", "Skylands + islands", 2032L, "skylands with floating islands of ice, huge trees, crystal formations: island shapes, tree scale, crystal clusters",
                                "terrain_normal", "skylands", page("floating_islands", "block_ice"), "huge_trees", "crystal_formations", "weather_off"),
                        Case.of("D3", "Amplified deep lakes", 2033L, "amplified cliffs, deep lakes of lava, tendrils of nether bricks, no horizon band",
                                "terrain_amplified", page("lakes_deep", "block_lava"), page("tendrils", "block_nether_bricks"), "no_horizon", "weather_off"),
                        Case.of("D4", "Nether age", 2034L, "nether (cave) terrain with nether biomes, deep lava lakes, a nether fortress integrated into the caves",
                                "terrain_nether", page("lakes_deep", "block_lava"), "biome_medium", "biome_minecraft_crimson_forest", "biome_minecraft_nether_wastes", "nether_fortress", "weather_off"),
                        Case.of("D5", "End age", 2035L, "end island terrain, end biome, obsidian spikes: island edge, spike shapes",
                                "terrain_end", "biome_single", "biome_minecraft_end_highlands", page("spikes", "block_obsidian"), "weather_off"),
                        Case.of("D6", "Void with star fissure", 2036L, "void terrain (nothing but the arrival platform) with a star fissure visible from the platform",
                                "terrain_void", "star_fissure", "weather_off"))),
                new Section("E", "Biomes & structures", Blocks.YELLOW_CONCRETE, List.of(
                        Case.of("E1", "Tiny biomes", 2041L, "tiny patches of desert / jungle / ice spikes side by side with villages: patchwork look, village placement",
                                "biome_tiny", "biome_minecraft_desert", "biome_minecraft_jungle", "biome_minecraft_ice_spikes", "villages", "weather_off"),
                        Case.of("E2", "Large biomes + Facility", 2042L, "large biome scale; the Facility entrance in view 60-120 blocks from arrival, sitting on the terrain, not floating or buried",
                                "biome_large", "vault", "weather_off"))),
                new Section("F", "Creatures", Blocks.RED_CONCRETE, List.of(
                        Case.of("F1", "Brutal hostile swarm", 2051L, "monsters spawn 4x as often, 4x the usual number, double health and hit hard; passives sparse: night pressure",
                                page("creatures_hostile", "mod_rate_swarm", "mod_cap_horde", "mod_difficulty_brutal"), page("creatures_passive", "mod_rate_sparse", "mod_cap_few"),
                                "weather_off"),
                        Case.of("F2", "Peaceful meadow", 2052L, "no hostiles ever, dense animals; neutrals normal",
                                page("creatures_hostile", "mod_rate_none"), page("creatures_passive", "mod_rate_dense", "mod_cap_many"), "creatures_neutral",
                                "weather_off"),
                        Case.of("F3", "Lifeless", 2053L, "no creature spawns naturally at all (watch at night)", "creatures_none", "weather_off"))),
                new Section("G", "Instability", Blocks.PURPLE_CONCRETE, List.of(
                        Case.of("G1", "Unstable", 2061L, "meteors + accelerated + explosions: instability symptoms within minutes (decay spread, crumbling, meteor visuals, effect pacing)",
                                "env_meteors", "env_accelerated", "env_explosions", "dense_ores", "weather_off"))));
    }

    /** All cases in row order (for tests and the client smoke tour). */
    public static List<Case> cases() {
        List<Case> all = new ArrayList<>();
        for (Section s : sections()) all.addAll(s.cases());
        return all;
    }

    // Layout (in blocks): a section row is 3 deep (walkway, lectern line, walkway) on its coloured floor, then a
    // stone-brick path; lecterns 2 apart on 1-high pedestals; the section sign stands on the lectern line directly
    // left of the first column. Row A starts with the home Linking Book lying in an item frame on a pedestal, then
    // A1. A stone-brick walkway with an oak fence rings the whole floor, open where the entry path meets it. The
    // floor is centred on the player and a two-wide stone-brick path runs from the player's feet to the entry.
    // Glowstone hangs in a grid above so the worlds read at night too.
    private static final int ROW_PITCH = 4;
    private static final int LECTERN_PITCH = 2;
    private static final int LIGHT_HEIGHT = 4;
    private static final int LIGHT_PITCH = 4;
    /** Path blocks between the player and the entry. */
    public static final int PATH_LENGTH = 4;

    /** Floor width in blocks: the item columns at odd offsets 1, 3, ... plus one free column on each side. */
    public static int width() {
        List<Section> sections = sections();
        int widest = 1;
        for (int s = 0; s < sections.size(); s++) widest = Math.max(widest, sections.get(s).cases().size() + (s == 0 ? 1 : 0));
        return 2 * widest + 1;
    }

    /** Depth of the floor (rows plus the path after each). */
    public static int depth() {
        return sections().size() * ROW_PITCH;
    }

    /**
     * Builds the worlds in front of the player: sections as rows going away from the player, lecterns left to right,
     * the whole floor centred on the player. Clears only its own footprint. Returns the number of lecterns placed.
     */
    public static int build(ServerLevel level, ServerPlayer player) {
        return build(level, player, player.blockPosition(), player.getDirection());
    }

    /**
     * Builds the worlds for {@code player} with the entry path starting at {@code start} (the block the player stands
     * on, or the end of a trunk path) and the floor extending in {@code facing}.
     */
    public static int build(ServerLevel level, ServerPlayer player, BlockPos start, Direction facing) {
        MinecraftServer server = level.getServer();
        Direction right = facing.getClockWise();
        List<Section> sections = sections();
        int width = width();
        int depth = depth();
        int centre = width / 2;
        // origin: front-left floor block (column 0, row 0); the player's column is the centre column
        BlockPos origin = start.relative(facing, PATH_LENGTH + 2).relative(right, -centre);
        // Floor columns are w = 0 .. width-1; the fenced walkway ring adds one more column/row on every side.
        int ringLeft = -1, ringRight = width, ringFront = -1, ringBack = depth;
        BlockPos nearCorner = origin.relative(facing, ringFront).relative(right, ringLeft);
        BlockPos farCorner = origin.relative(facing, ringBack).relative(right, ringRight);
        int y = origin.getY();
        QaBase.clearWithoutDrops(level,
                new BlockPos(Math.min(nearCorner.getX(), farCorner.getX()), y - 1, Math.min(nearCorner.getZ(), farCorner.getZ())),
                new BlockPos(Math.max(nearCorner.getX(), farCorner.getX()), y + LIGHT_HEIGHT + 1, Math.max(nearCorner.getZ(), farCorner.getZ())));

        // Entry path: two wide (the player's column and the one to its left), from the player's feet to the ring.
        QaBase.pavePath(level, start, facing, PATH_LENGTH + 1, -1, 0);
        // Walkway ring: stone bricks with a fence on top, open where the path arrives.
        for (int d = ringFront; d <= ringBack; d++) {
            for (int w = ringLeft; w <= ringRight; w++) {
                boolean ring = d == ringFront || d == ringBack || w == ringLeft || w == ringRight;
                if (!ring) continue;
                BlockPos pos = origin.relative(facing, d).relative(right, w);
                level.setBlock(pos.below(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                boolean gate = d == ringFront && (w == centre || w == centre - 1);
                if (!gate) level.setBlock(pos, Blocks.OAK_FENCE.defaultBlockState(), 3);
            }
        }
        // Lights: a glowstone grid over the floor.
        for (int d = 1; d < depth; d += LIGHT_PITCH) {
            for (int w = 1; w < width; w += LIGHT_PITCH) {
                level.setBlock(origin.relative(facing, d).relative(right, w).above(LIGHT_HEIGHT), Blocks.GLOWSTONE.defaultBlockState(), 3);
            }
        }

        int placed = 0;
        for (int s = 0; s < sections.size(); s++) {
            Section section = sections.get(s);
            BlockPos rowStart = origin.relative(facing, s * ROW_PITCH);
            // Floor: 3 deep of the section colour, then one row of path.
            for (int d = 0; d < ROW_PITCH; d++) {
                BlockState floor = d < 3 ? section.floor().defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                for (int w = 0; w < width; w++) {
                    level.setBlock(rowStart.relative(facing, d).relative(right, w).below(), floor, 3);
                }
            }
            BlockPos lecternLine = rowStart.relative(facing, 1);
            // Section sign directly left of the first column, facing the player.
            placeStandingSign(level, lecternLine, facing.getOpposite(), "Section " + section.id(), section.name(), "", "");
            int first = 0;
            if (s == 0) {
                // First in row A: a Linking Book back here (intra-linking + following), lying in an item frame on a
                // pedestal, turned so it reads upright from the player's side.
                BlockPos pedestal = lecternLine.relative(right, 1);
                level.setBlock(pedestal, Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
                ItemFrame frame = new ItemFrame(level, pedestal.above(), Direction.UP);
                frame.setItem(QaBase.homeBook(player, "Back to the QA worlds"), false);
                frame.setRotation(frameRotation(facing));
                level.addFreshEntity(frame);
                placeWallSign(level, pedestal.relative(facing, -1), facing.getOpposite(), "HOME", "Linking Book", "back to the", "QA worlds");
                first = 1;
            }
            for (int i = 0; i < section.cases().size(); i++) {
                Case qa = section.cases().get(i);
                BlockPos pedestal = lecternLine.relative(right, 1 + (first + i) * LECTERN_PITCH);
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
        }
        return placed;
    }

    /**
     * Rotation of an item in an upward-facing item frame so the item's top points along {@code toward}:
     * {@code ItemFrameRenderer} turns the item 45 degrees per step from north (0) clockwise seen from above.
     */
    public static int frameRotation(Direction toward) {
        return (int) ((toward.toYRot() + 180f) / 45f) & 7;
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

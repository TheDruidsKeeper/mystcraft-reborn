package com.tbd.mystcraft.command;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.age.AgeManager;
import com.tbd.mystcraft.block.BookReceptacleBlock;
import com.tbd.mystcraft.block.DecayType;
import com.tbd.mystcraft.block.LecternBlock;
import com.tbd.mystcraft.block.WritingDeskBlock;
import com.tbd.mystcraft.blockentity.BookDisplayBlockEntity;
import com.tbd.mystcraft.item.DescriptiveBookItem;
import com.tbd.mystcraft.item.LinkingBookItem;
import java.util.ArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import com.tbd.mystcraft.api.linking.LinkProperty;
import com.tbd.mystcraft.item.LinkingItem;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.registry.ModBlocks;
import com.tbd.mystcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

/**
 * {@code /myst-dev scene}: builds a showcase of every renderable Mystcraft block in front of the player, on a stone pad,
 * and positions the player to look at it. Used by the headless client self check for screenshots and by hand for
 * quick visual regression checks (docs/QA.md). Layout (player looks north, x grows to the right):
 *
 * <pre>
 *   row z-9 : crystal portal (4x5 ring, receptacle on the front at eye height) | ink pool 3x3 | star fissure 2x2 | item frames
 *   row z-6 : writing desk | bookstand+book | lectern+book | ink mixer | book binder | link modifier
 *   row z-3 : decay blocks (one of each type)        supply chest | plain writing desk
 * The viewer stands on the ground (the pad replaces the surface, nothing floats) 6 blocks south of the pad.
 * </pre>
 */
public final class DebugScene {
    private DebugScene() {}

    public static final int WIDTH = 23;
    public static final int DEPTH = 12;

    /** Last scene origin per level (for {@code /myst-dev scene closeup}). */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, BlockPos> ORIGINS = new java.util.HashMap<>();

    /** Elements of the scene a close-up can target: offset of the element from the origin. */
    public enum Element {
        DESK(1.5, 0, -6, 180f, 35f), BOOKSTAND(5, 0, -6, 180f, 35f), LECTERN(8, 0, -6, 180f, 35f), INK_MIXER(11, 0, -6, 180f, 35f),
        BOOK_BINDER(14, 0, -6, 180f, 35f), LINK_MODIFIER(17, 0, -6, 180f, 35f), PORTAL(5, 1, -9, 180f, 10f), INK(9, -1, -9, 180f, 50f),
        FISSURE(12.5, -1, -9.5, 180f, 45f), DECAY(12, 0, -3, 180f, 30f), PAGES(18.5, 0.5, -9, 180f, 15f);

        public final double dx, dy, dz;
        public final float yaw, pitch;

        Element(double dx, double dy, double dz, float yaw, float pitch) {
            this.dx = dx; this.dy = dy; this.dz = dz; this.yaw = yaw; this.pitch = pitch;
        }
    }

    /** Teleports the viewer 3 blocks south of and 2 above the element, looking at it. Returns false without a scene. */
    public static boolean closeup(ServerLevel level, ServerPlayer viewer, Element element) {
        BlockPos origin = ORIGINS.get(level.dimension());
        if (origin == null) return false;
        double x = origin.getX() + element.dx + 0.5, y = origin.getY() + element.dy, z = origin.getZ() + element.dz + 0.5;
        viewer.teleportTo(level, x, y + 2.0, z + 3.0, Set.of(), element.yaw, element.pitch, true);
        Mystcraft.LOGGER.info("[scene] close-up of {} at {}, {}, {}", element, x, y, z);
        return true;
    }

    /**
     * Right-clicks the element's block for the player (opens its screen when it has one), exactly as a player would
     * with an empty hand. Returns false without a scene or when the block did nothing.
     */
    public static boolean open(ServerLevel level, ServerPlayer viewer, Element element) {
        BlockPos origin = ORIGINS.get(level.dimension());
        if (origin == null) return false;
        BlockPos pos = new BlockPos((int) Math.floor(origin.getX() + element.dx), (int) Math.floor(origin.getY() + element.dy),
                (int) Math.floor(origin.getZ() + element.dz));
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0, 0, 0.5), Direction.SOUTH, pos, false);
        net.minecraft.world.InteractionResult result = state.useWithoutItem(level, viewer, hit);
        Mystcraft.LOGGER.info("[scene] open {} at {} ({}) -> {}", element, pos.toShortString(), state.getBlock(), result);
        return result.consumesAction();
    }

    /** Items whose screens the self check opens with {@code /myst-dev scene use <item>}. */
    public enum UsableItem { LINKING_BOOK, DESCRIPTIVE_BOOK, FOLDER, CURRENT_AGE_BOOK }

    /** Puts a fresh item of that kind in the player's main hand and uses it (opens its screen). */
    public static boolean use(ServerLevel level, ServerPlayer viewer, UsableItem kind) {
        ItemStack stack = switch (kind) {
            case LINKING_BOOK -> LinkingBookItem.createAt(viewer);
            case DESCRIPTIVE_BOOK -> descriptiveBook(level.getServer(), "Scene Book");
            case FOLDER -> {
                ItemStack folder = new ItemStack(ModItems.COLLATION_FOLDER.get());
                folder.set(com.tbd.mystcraft.registry.ModDataComponents.SLOT_PAGES.get(),
                        com.tbd.mystcraft.item.component.SlotPages.EMPTY
                                .with(0, PageItem.createSymbolPage(com.tbd.mystcraft.util.MystIds.id("sun_normal")))
                                .with(1, PageItem.createLinkPanel()));
                yield folder;
            }
            case CURRENT_AGE_BOOK -> {
                // a Descriptive Book of the Age the player stands in (shows the destination picture on its panel)
                AgeData current = AgeManager.get(level.getServer(), level.dimension());
                if (current == null) yield descriptiveBook(level.getServer(), "Scene Book");
                ItemStack book = DescriptiveBookItem.create(viewer, current.pages(), current.name());
                DescriptiveBookItem.initializeForAge(book, current);
                yield book;
            }
        };
        viewer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        viewer.inventoryMenu.broadcastChanges(); // the hand item must reach the client before the book menu opens
        net.minecraft.world.InteractionResult result = stack.use(level, viewer, net.minecraft.world.InteractionHand.MAIN_HAND);
        Mystcraft.LOGGER.info("[scene] use {} -> {}", kind, result);
        return result.consumesAction();
    }

    /**
     * Replaces every block of the box with air from the top down, without drops or neighbour reactions, and removes
     * item entities already lying in it - so building a scene does not scatter seeds, flowers and grass around.
     */
    public static void clearWithoutDrops(ServerLevel level, BlockPos min, BlockPos max) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (int y = max.getY(); y >= min.getY(); y--) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
                }
            }
        }
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 2, max.getZ() + 1))) {
            item.discard();
        }
    }

    /** Builds the scene with its south-west corner at {@code origin} (pad surface = origin.y - 1). */
    public static BlockPos build(ServerLevel level, BlockPos origin, ServerPlayer viewer) {
        MinecraftServer server = level.getServer();
        int x0 = origin.getX(), y = origin.getY(), z0 = origin.getZ();

        // Clear the volume first (top-down, no drops: grass, flowers and seeds would litter the scene), then the pad.
        clearWithoutDrops(level, new BlockPos(x0, y - 1, z0 - DEPTH + 1), new BlockPos(x0 + WIDTH - 1, y + 6, z0));
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y - 1, z0 - dz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            }
        }

        // Row A (z0-3): decay blocks, crystal column.
        int dx = 6;
        for (DecayType type : DecayType.values()) {
            level.setBlock(new BlockPos(x0 + dx, y, z0 - 3), ModBlocks.decay(type).get().defaultBlockState(), 3);
            dx += 2;
        }
        // Right end of row A: a plain (non-scholar) writing desk and a supply chest with every crafting input plus a
        // Linking Book back to this spot (intra-linking + following), so a tester can build and travel from here.
        BlockPos plainDesk = new BlockPos(x0 + WIDTH - 4, y, z0 - 3);
        BlockState plainHead = ModBlocks.WRITING_DESK.get().defaultBlockState().setValue(WritingDeskBlock.FACING, Direction.EAST);
        level.setBlock(plainDesk, plainHead, 3);
        level.setBlock(plainDesk.east(), plainHead.setValue(WritingDeskBlock.FOOT, true), 3);
        level.setBlock(plainDesk.above(), plainHead.setValue(WritingDeskBlock.TOP, true), 3);
        level.setBlock(plainDesk.east().above(), plainHead.setValue(WritingDeskBlock.TOP, true).setValue(WritingDeskBlock.FOOT, true), 3);
        BlockPos chest = new BlockPos(x0 + WIDTH - 6, y, z0 - 3);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH), 3);
        if (level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chestBe) {
            int slot = 0;
            for (ItemStack stack : supplies(viewer)) {
                if (slot < chestBe.getContainerSize()) chestBe.setItem(slot++, stack);
            }
            chestBe.setChanged();
        }

        // Row B (z0-6): workstations, all facing south (towards the player).
        BlockPos desk = new BlockPos(x0 + 1, y, z0 - 6);
        BlockState deskHead = ModBlocks.WRITING_DESK.get().defaultBlockState().setValue(WritingDeskBlock.FACING, Direction.EAST);
        level.setBlock(desk, deskHead, 3);
        level.setBlock(desk.east(), deskHead.setValue(WritingDeskBlock.FOOT, true), 3);
        level.setBlock(desk.above(), deskHead.setValue(WritingDeskBlock.TOP, true), 3);
        level.setBlock(desk.east().above(), deskHead.setValue(WritingDeskBlock.TOP, true).setValue(WritingDeskBlock.FOOT, true), 3);
        // stock the desk so the renderer's shelf books and inkwell show up in screenshots
        if (level.getBlockEntity(desk) instanceof com.tbd.mystcraft.blockentity.WritingDeskBlockEntity deskBe) {
            // a Scholar's desk (every symbol on the surface, full shelves) with a folder holding a page that carries modifiers
            deskBe.setScholar(true);
            deskBe.setInk(new net.neoforged.neoforge.fluids.FluidStack(com.tbd.mystcraft.registry.ModFluids.BLACK_INK.get(), 700));
            deskBe.main.setStack(com.tbd.mystcraft.blockentity.WritingDeskBlockEntity.SLOT_TARGET,
                    com.tbd.mystcraft.item.FolderItem.create("Scene folder", List.of(
                            PageItem.createSymbolPage(new com.tbd.mystcraft.item.component.SymbolPage(
                                    com.tbd.mystcraft.util.MystIds.id("sun_normal"),
                                    List.of(com.tbd.mystcraft.util.MystIds.id("mod_north")), false)),
                            PageItem.createSymbolPage(com.tbd.mystcraft.util.MystIds.id("terrain_flat")))));
            deskBe.main.setStack(com.tbd.mystcraft.blockentity.WritingDeskBlockEntity.SLOT_PAPER, new ItemStack(net.minecraft.world.item.Items.PAPER, 16));
            deskBe.markForUpdate();
        }

        BlockPos stand = new BlockPos(x0 + 5, y, z0 - 6);
        level.setBlock(stand, ModBlocks.BOOKSTAND.get().defaultBlockState(), 3);
        putBook(level, stand, LinkingBookItem.createAt(viewer));

        BlockPos lectern = new BlockPos(x0 + 8, y, z0 - 6);
        level.setBlock(lectern, ModBlocks.LECTERN.get().defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH), 3);
        putBook(level, lectern, descriptiveBook(server, "Lectern"));

        level.setBlock(new BlockPos(x0 + 11, y, z0 - 6), ModBlocks.INK_MIXER.get().defaultBlockState(), 3);
        level.setBlock(new BlockPos(x0 + 14, y, z0 - 6), ModBlocks.BOOK_BINDER.get().defaultBlockState(), 3);
        level.setBlock(new BlockPos(x0 + 17, y, z0 - 6), ModBlocks.LINK_MODIFIER.get().defaultBlockState(), 3);

        // Row C (z0-9): portal frame in the x/y plane (visible face towards the player) + star fissure.
        int px = x0 + 3; // 4 wide x 5 tall ring -> walkable 2x3 field
        for (int fx = 0; fx < 4; fx++) {
            for (int fy = 0; fy < 5; fy++) {
                boolean interior = fx >= 1 && fx <= 2 && fy >= 1 && fy <= 3;
                if (interior) continue;
                level.setBlock(new BlockPos(px + fx, y + fy, z0 - 9), ModBlocks.CRYSTAL.get().defaultBlockState(), 3);
            }
        }
        // Receptacle on the front (south) face of the left pillar at player height, so it can be used from the ground.
        BlockPos receptacle = new BlockPos(px, y + 1, z0 - 8);
        level.setBlock(receptacle, ModBlocks.BOOK_RECEPTACLE.get().defaultBlockState().setValue(BookReceptacleBlock.ROTATION, Direction.SOUTH), 3);
        putBook(level, receptacle, descriptiveBook(server, "Portal Scene")); // fires the portal

        // Ink pool (1 deep, sunk into the pad) between the portal and the star fissure.
        for (int ix = 8; ix <= 10; ix++) {
            for (int iz = 8; iz <= 10; iz++) {
                level.setBlock(new BlockPos(x0 + ix, y - 1, z0 - iz), ModBlocks.BLACK_INK.get().defaultBlockState(), 3);
            }
        }

        for (int fx = 0; fx < 2; fx++) {
            for (int fz = 0; fz < 2; fz++) {
                level.setBlock(new BlockPos(x0 + 12 + fx, y - 1, z0 - 9 - fz), ModBlocks.STAR_FISSURE.get().defaultBlockState(), 3);
            }
        }

        // Row C east end: a wall of item frames showing the item icons that are rendered dynamically (pages, books).
        List<ItemStack> framed = List.of(
                PageItem.createSymbolPage(com.tbd.mystcraft.util.MystIds.id("sun_normal")),
                // a page with attached modifiers (overlays on the corners) and a discovered page (different ink)
                PageItem.createSymbolPage(new com.tbd.mystcraft.item.component.SymbolPage(
                        com.tbd.mystcraft.util.MystIds.id("color_sky"),
                        List.of(com.tbd.mystcraft.util.MystIds.id("mod_color_red"), com.tbd.mystcraft.util.MystIds.id("mod_gradient"),
                                com.tbd.mystcraft.util.MystIds.id("mod_color_blue")), false)),
                PageItem.createDiscoveredPage(com.tbd.mystcraft.util.MystIds.id("terrain_normal"), List.of()),
                PageItem.createLinkPanel(),
                PageItem.createBlankPage(),
                descriptiveBook(server, "Framed"),
                LinkingBookItem.createAt(viewer));
        for (int i = 0; i < framed.size(); i++) {
            BlockPos wall = new BlockPos(x0 + 16 + i, y + 1, z0 - 10);
            level.setBlock(wall, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            net.minecraft.world.entity.decoration.ItemFrame frame =
                    new net.minecraft.world.entity.decoration.ItemFrame(level, wall.south(), Direction.SOUTH);
            frame.setItem(framed.get(i), false);
            level.addFreshEntity(frame);
        }

        // Viewer: centred, six blocks in front of the pad, standing on the ground (a 3x3 stone patch replaces the
        // surface block under the feet; nothing floats) looking north and slightly down.
        BlockPos view = new BlockPos(x0 + WIDTH / 2, y, z0 + 6);
        for (int vx = -1; vx <= 1; vx++) {
            for (int vz = -1; vz <= 1; vz++) {
                level.setBlock(view.offset(vx, -1, vz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                for (int vy = 0; vy < 3; vy++) level.setBlock(view.offset(vx, vy, vz), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        viewer.teleportTo(level, view.getX() + 0.5, view.getY(), view.getZ() + 0.5, Set.of(), 180f, 12f, true);
        ORIGINS.put(level.dimension(), origin);
        Mystcraft.LOGGER.info("[scene] built debug scene at {} in {}; viewer at {} (portal field expected at {})",
                origin.toShortString(), level.dimension().identifier(), view.toShortString(), new BlockPos(px + 1, y + 1, z0 - 9).toShortString() + " (2x3)");
        return view;
    }

    /** Full stacks of every crafting input of the mod's recipes and ink effects, plus a Linking Book back to {@code here}. */
    public static List<ItemStack> supplies(Entity here) {
        List<ItemStack> out = new ArrayList<>();
        out.add(homeBook(here, "Back to the scene"));
        for (Item item : List.of(Items.PAPER, Items.LEATHER, Items.BOOK, Items.STICK, Items.STRING, Items.STONE, Items.IRON_INGOT,
                Items.FEATHER, Items.BLACK_DYE, Items.GLASS_BOTTLE, Items.ITEM_FRAME, Items.CLAY_BALL, Items.GUNPOWDER, Items.COMPASS,
                Items.ENDER_PEARL, Items.AMETHYST_SHARD, Items.ENDER_EYE, Items.GLOWSTONE_DUST, Items.REDSTONE, Items.GOLD_INGOT, Items.DIAMOND)) {
            out.add(new ItemStack(item, item.getDefaultMaxStackSize()));
        }
        out.add(new ItemStack(Items.WATER_BUCKET));
        out.add(new ItemStack(ModItems.BLACK_INK_BUCKET.get()));
        out.add(new ItemStack(ModItems.INK_VIAL.get(), 16));
        out.add(new ItemStack(ModBlocks.CRYSTAL.get(), 64));
        return out;
    }

    /** A Linking Book bound to {@code here} with intra-linking and following, i.e. a way back for a whole party. */
    public static ItemStack homeBook(Entity here, String title) {
        ItemStack book = LinkingBookItem.createAt(here);
        LinkingItem.setLinkInfo(book, LinkingItem.getLinkInfo(book).withDisplayName(title)
                .withFlag(LinkProperty.INTRA_LINKING, true).withFlag(LinkProperty.FOLLOWING, true));
        return book;
    }

    private static void putBook(ServerLevel level, BlockPos pos, ItemStack book) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BookDisplayBlockEntity display) {
            display.setBook(book);
            display.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
        } else {
            Mystcraft.LOGGER.warn("[scene] no book display block entity at {} ({})", pos.toShortString(), level.getBlockState(pos));
        }
    }

    /** A Descriptive Book bound to a fresh Age (so receptacles power portals and lecterns show a title). */
    private static ItemStack descriptiveBook(MinecraftServer server, String name) {
        return DescriptiveBookItem.createBound(server, name, 0, List.of());
    }

}

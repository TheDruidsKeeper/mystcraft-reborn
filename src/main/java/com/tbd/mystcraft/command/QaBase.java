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
 * {@code /myst-dev qa-base}: builds a showcase of every renderable Mystcraft block north of the player, on a stone pad
 * reached by a two-wide stone-brick path, and turns the player to look at it. Used by the headless client self check
 * for screenshots and by hand for quick visual regression checks (docs/QA.md). Layout (player looks north, x grows to
 * the right):
 *
 * <pre>
 *   back row  z-9 : crystal portal (4x5 ring, receptacle on the front at eye height) | star fissure 2x2 | decay blocks (one of each)
 *   front row z-6 : supply chest + crafting table | writing desk (scholar) | ink mixer | book binder | bookstand+book | lectern+book | link modifier | plain writing desk
 * The front row reads as the workflow left to right. A fence rings the pad (open where the path arrives), glowstone
 * hangs in a grid above it. The viewer stands where the command was run (the pad replaces the surface, nothing
 * floats), {@link #VIEW_DISTANCE} blocks south of the pad's front row.
 * </pre>
 */
public final class QaBase {
    private QaBase() {}

    public static final int WIDTH = 25;
    public static final int DEPTH = 12;
    /** Blocks between the viewer and the pad's front (ring) row; the path fills them. */
    public static final int VIEW_DISTANCE = 5;
    private static final int LIGHT_HEIGHT = 7;
    private static final int LIGHT_PITCH = 4;

    /** Last scene origin per level (for {@code /myst-dev qa-base closeup}). */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, BlockPos> ORIGINS = new java.util.HashMap<>();

    /** Elements of the scene a close-up can target: offset of the element from the origin. */
    public enum Element {
        CHEST(0.5, 0, -6, 180f, 35f), DESK(4.5, 0, -6, 180f, 35f), INK_MIXER(8, 0, -6, 180f, 35f), BOOK_BINDER(11, 0, -6, 180f, 35f),
        BOOKSTAND(14, 0, -6, 180f, 35f), LECTERN(17, 0, -6, 180f, 35f), LINK_MODIFIER(20, 0, -6, 180f, 35f), PLAIN_DESK(22.5, 0, -6, 180f, 35f),
        PORTAL(4.5, 1, -9, 180f, 10f), FISSURE(9.5, -1, -9.5, 180f, 45f), DECAY(19, 0, -9, 180f, 30f);

        public final double dx, dy, dz;
        public final float yaw, pitch;

        Element(double dx, double dy, double dz, float yaw, float pitch) {
            this.dx = dx; this.dy = dy; this.dz = dz; this.yaw = yaw; this.pitch = pitch;
        }
    }

    /** Puts the viewer back on the scene's viewing spot (where {@link #build} left them). Returns false without a scene. */
    public static boolean view(ServerLevel level, ServerPlayer viewer) {
        BlockPos origin = ORIGINS.get(level.dimension());
        if (origin == null) return false;
        BlockPos view = viewPos(origin);
        viewer.teleportTo(level, view.getX() + 0.5, view.getY(), view.getZ() + 0.5, Set.of(), 180f, 12f, true);
        return true;
    }

    private static BlockPos viewPos(BlockPos origin) {
        return new BlockPos(origin.getX() + WIDTH / 2, origin.getY(), origin.getZ() + VIEW_DISTANCE + 1);
    }

    /** The pad origin (south-west corner) for a viewer standing at {@code view}. */
    public static BlockPos originFor(BlockPos view) {
        return view.offset(-WIDTH / 2, 0, -(VIEW_DISTANCE + 1));
    }

    /**
     * Paves a path of stone bricks: {@code length} blocks starting one block from {@code from} in {@code dir}, the
     * columns {@code leftOffset..rightOffset} measured towards {@code dir.getClockWise()} (so {@code -1, 0} is the
     * player's own column and the one to its left). The surface block is replaced, two blocks above are cleared.
     */
    public static void pavePath(ServerLevel level, BlockPos from, Direction dir, int length, int leftOffset, int rightOffset) {
        Direction right = dir.getClockWise();
        for (int k = 1; k <= length; k++) {
            for (int w = leftOffset; w <= rightOffset; w++) {
                BlockPos pos = from.relative(dir, k).relative(right, w);
                level.setBlock(pos.below(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                for (int h = 0; h < 2; h++) {
                    if (!level.getBlockState(pos.above(h)).isAir()) level.setBlock(pos.above(h), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    /** Teleports the viewer 3 blocks south of and 2 above the element, looking at it. Returns false without a scene. */
    public static boolean closeup(ServerLevel level, ServerPlayer viewer, Element element) {
        BlockPos origin = ORIGINS.get(level.dimension());
        if (origin == null) return false;
        double x = origin.getX() + element.dx + 0.5, y = origin.getY() + element.dy, z = origin.getZ() + element.dz + 0.5;
        viewer.teleportTo(level, x, y + 2.0, z + 3.0, Set.of(), element.yaw, element.pitch, true);
        Mystcraft.LOGGER.info("[base] close-up of {} at {}, {}, {}", element, x, y, z);
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
        Mystcraft.LOGGER.info("[base] open {} at {} ({}) -> {}", element, pos.toShortString(), state.getBlock(), result);
        return result.consumesAction();
    }

    /** Items whose screens the self check opens with {@code /myst-dev qa-base use <item>}. */
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
        Mystcraft.LOGGER.info("[base] use {} -> {}", kind, result);
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
        // The pad is ringed by a one-block walkway with a fence (open towards the viewer), lit by glowstone above.
        clearWithoutDrops(level, new BlockPos(x0 - 1, y - 1, z0 - DEPTH), new BlockPos(x0 + WIDTH, y + LIGHT_HEIGHT + 1, z0 + 1));
        for (int dx = -1; dx <= WIDTH; dx++) {
            for (int dz = -1; dz <= DEPTH; dz++) {
                boolean ring = dx == -1 || dx == WIDTH || dz == -1 || dz == DEPTH;
                level.setBlock(new BlockPos(x0 + dx, y - 1, z0 - dz), (ring ? Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE).defaultBlockState(), 3);
                boolean gate = dz == -1 && (dx == WIDTH / 2 || dx == WIDTH / 2 - 1); // where the path arrives
                if (ring && !gate) level.setBlock(new BlockPos(x0 + dx, y, z0 - dz), Blocks.OAK_FENCE.defaultBlockState(), 3);
            }
        }
        for (int dx = 2; dx < WIDTH; dx += LIGHT_PITCH) {
            for (int dz = 2; dz < DEPTH; dz += LIGHT_PITCH) {
                level.setBlock(new BlockPos(x0 + dx, y + LIGHT_HEIGHT, z0 - dz), Blocks.GLOWSTONE.defaultBlockState(), 3);
            }
        }

        // Front row (z0-6), left to right in workflow order. First the supplies: a double chest whose upper half
        // (the western, "right" chest - the first container of the pair) holds one of every mod item worth looking
        // at, with a Linking Book back to this spot (intra-linking + following) first, and whose lower half holds
        // every crafting input; then a crafting table, so a tester can build and travel from here. The client smoke
        // screenshots the open chest and scripts/docs/crop_icons.py cuts the guide's item icons out of it using the
        // [base] "chest slot" log lines below.
        BlockPos chest = new BlockPos(x0, y, z0 - 6);
        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH);
        level.setBlock(chest, chestState.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT), 3);
        level.setBlock(chest.east(), chestState.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT), 3);
        fillChest(level, chest, showcase(viewer), 0);
        fillChest(level, chest.east(), supplies(), 27);
        level.setBlock(chest.east(2), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);

        // Workstations, all facing south (towards the player).
        BlockPos desk = new BlockPos(x0 + 4, y, z0 - 6);
        WritingDeskBlock.placeDesk(level, desk, Direction.EAST, ModBlocks.WRITING_DESK.get());
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

        level.setBlock(new BlockPos(x0 + 8, y, z0 - 6), ModBlocks.INK_MIXER.get().defaultBlockState(), 3);
        level.setBlock(new BlockPos(x0 + 11, y, z0 - 6), ModBlocks.BOOK_BINDER.get().defaultBlockState(), 3);

        BlockPos stand = new BlockPos(x0 + 14, y, z0 - 6);
        level.setBlock(stand, ModBlocks.BOOKSTAND.get().defaultBlockState(), 3);
        putBook(level, stand, LinkingBookItem.createAt(viewer));

        BlockPos lectern = new BlockPos(x0 + 17, y, z0 - 6);
        level.setBlock(lectern, ModBlocks.LECTERN.get().defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH), 3);
        putBook(level, lectern, descriptiveBook(server, "Lectern"));

        level.setBlock(new BlockPos(x0 + 20, y, z0 - 6), ModBlocks.LINK_MODIFIER.get().defaultBlockState(), 3);

        // Right end of the front row: a plain (non-scholar) writing desk with a half-filled ink tank, so the shelf
        // books track the viewer's own symbol knowledge.
        BlockPos plainDesk = new BlockPos(x0 + 22, y, z0 - 6);
        WritingDeskBlock.placeDesk(level, plainDesk, Direction.EAST, ModBlocks.WRITING_DESK.get());
        if (level.getBlockEntity(plainDesk) instanceof com.tbd.mystcraft.blockentity.WritingDeskBlockEntity plainBe) {
            plainBe.setInk(new net.neoforged.neoforge.fluids.FluidStack(com.tbd.mystcraft.registry.ModFluids.BLACK_INK.get(), 400));
            plainBe.markForUpdate();
        }

        // Back row (z0-9): portal frame in the x/y plane (visible face towards the player), star fissure, decay.
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

        for (int fx = 0; fx < 2; fx++) {
            for (int fz = 0; fz < 2; fz++) {
                level.setBlock(new BlockPos(x0 + 9 + fx, y - 1, z0 - 9 - fz), ModBlocks.STAR_FISSURE.get().defaultBlockState(), 3);
            }
        }

        int dx = 13;
        for (DecayType type : DecayType.values()) {
            level.setBlock(new BlockPos(x0 + dx, y, z0 - 9), ModBlocks.decay(type).get().defaultBlockState(), 3);
            dx += 2;
        }

        // Viewer: centred, standing on the ground (a 3x3 stone patch replaces the surface block under the feet;
        // nothing floats) looking north and slightly down, with a two-wide path up to the gate.
        BlockPos view = viewPos(origin);
        for (int vx = -1; vx <= 1; vx++) {
            for (int vz = -1; vz <= 1; vz++) {
                level.setBlock(view.offset(vx, -1, vz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                for (int vy = 0; vy < 3; vy++) level.setBlock(view.offset(vx, vy, vz), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        pavePath(level, view, Direction.NORTH, VIEW_DISTANCE, -1, 0);
        viewer.teleportTo(level, view.getX() + 0.5, view.getY(), view.getZ() + 0.5, Set.of(), 180f, 12f, true);
        ORIGINS.put(level.dimension(), origin);
        Mystcraft.LOGGER.info("[base] built debug scene at {} in {}; viewer at {} (portal field expected at {})",
                origin.toShortString(), level.dimension().identifier(), view.toShortString(), new BlockPos(px + 1, y + 1, z0 - 9).toShortString() + " (2x3)");
        return view;
    }

    private static void fillChest(ServerLevel level, BlockPos pos, List<ItemStack> stacks, int firstSlot) {
        if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chestBe)) return;
        for (int i = 0; i < stacks.size() && i < chestBe.getContainerSize(); i++) {
            chestBe.setItem(i, stacks.get(i));
            Mystcraft.LOGGER.info("[base] chest slot {} = {}", firstSlot + i, iconName(stacks.get(i)));
        }
        chestBe.setChanged();
    }

    /** Item id of a stack, pages qualified by what they carry (several page kinds share one item id). */
    private static String iconName(ItemStack stack) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!(stack.getItem() instanceof PageItem)) return id;
        if (PageItem.isLinkPanel(stack)) return id + "/link_panel";
        var symbol = PageItem.getSymbolId(stack);
        if (symbol == null) return id + "/blank";
        return id + (PageItem.isDiscovered(stack) ? "/discovered_" : "/symbol_") + symbol.getPath();
    }

    /**
     * The showcase half of the supply chest (27 singles, so no stack count is drawn over the icons): a Linking Book
     * back to {@code here}, the mod items the guide pictures, then one of every crafting ingredient of the recipes
     * (the water bottle as the potion the ink recipe takes). scripts/docs/crop_icons.py turns this into the icons.
     */
    public static List<ItemStack> showcase(Entity here) {
        List<ItemStack> out = new ArrayList<>();
        out.add(homeBook(here, "Back to the scene"));
        out.add(descriptiveBook(here.level().getServer(), "Showcase"));
        out.add(new ItemStack(ModItems.UNLINKED_BOOK.get()));
        out.add(PageItem.createSymbolPage(com.tbd.mystcraft.util.MystIds.id("sun_normal")));
        out.add(PageItem.createSymbolPage(com.tbd.mystcraft.util.MystIds.id("mod_north")));
        out.add(PageItem.createLinkPanel());
        out.add(new ItemStack(ModItems.COLLATION_FOLDER.get()));
        out.add(new ItemStack(ModItems.INK_VIAL.get()));
        for (Item item : List.of(ModItems.WRITING_DESK.get(), ModItems.INK_MIXER.get(), ModItems.BOOK_BINDER.get(), ModItems.BOOKSTAND.get(),
                ModItems.LECTERN.get(), ModItems.BOOK_RECEPTACLE.get(), ModItems.CRYSTAL.get(),
                Items.OAK_PLANKS, Items.STONE, Items.IRON_INGOT, Items.STICK, Items.PAPER, Items.LEATHER, Items.STRING, Items.FEATHER,
                Items.BLACK_DYE, Items.GLASS_BOTTLE)) {
            out.add(new ItemStack(item));
        }
        out.add(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER));
        return out;
    }

    /**
     * The supply half of the chest: full stacks of every crafting input of the mod's recipes and ink effects, a
     * stack of Unlinked Books, and the remaining mod items (singles) for a tester to try.
     */
    public static List<ItemStack> supplies() {
        List<ItemStack> out = new ArrayList<>();
        for (Item item : List.of(Items.OAK_PLANKS, Items.STONE, Items.IRON_INGOT, Items.STICK, Items.PAPER, Items.LEATHER, Items.STRING,
                Items.FEATHER, Items.BLACK_DYE, Items.GLASS_BOTTLE, Items.BOOK, Items.CLAY_BALL, Items.GUNPOWDER, Items.COMPASS,
                Items.ENDER_PEARL, Items.AMETHYST_SHARD, Items.ENDER_EYE, Items.GLOWSTONE_DUST, Items.REDSTONE, Items.GOLD_INGOT, Items.DIAMOND)) {
            out.add(new ItemStack(item, item.getDefaultMaxStackSize()));
        }
        out.add(new ItemStack(ModItems.INK_VIAL.get(), 16));
        out.add(new ItemStack(ModBlocks.CRYSTAL.get(), 64));
        out.add(new ItemStack(ModItems.UNLINKED_BOOK.get(), 16));
        out.add(new ItemStack(ModItems.SCHOLARS_WRITING_DESK.get()));
        out.add(new ItemStack(ModItems.LINK_MODIFIER.get()));
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
            Mystcraft.LOGGER.warn("[base] no book display block entity at {} ({})", pos.toShortString(), level.getBlockState(pos));
        }
    }

    /** A Descriptive Book bound to a fresh Age (so receptacles power portals and lecterns show a title). */
    private static ItemStack descriptiveBook(MinecraftServer server, String name) {
        return DescriptiveBookItem.createBound(server, name, 0, List.of());
    }

}

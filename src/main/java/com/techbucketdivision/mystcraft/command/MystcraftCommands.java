package com.techbucketdivision.mystcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.api.symbol.logic.WeatherController;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import com.techbucketdivision.mystcraft.entity.MeteorEntity;
import com.techbucketdivision.mystcraft.instability.ChunkProfiler;
import com.techbucketdivision.mystcraft.instability.InstabilityController;
import com.techbucketdivision.mystcraft.item.DescriptiveBookItem;
import com.techbucketdivision.mystcraft.registry.ModItems;
import com.techbucketdivision.mystcraft.item.LinkingItem;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.linking.LinkController;
import com.techbucketdivision.mystcraft.linking.LinkPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** All Mystcraft commands (REQUIREMENTS §12), registered on {@link RegisterCommandsEvent}. */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class MystcraftCommands {
    private MystcraftCommands() {}

    private static final SimpleCommandExceptionType NOT_AN_AGE = new SimpleCommandExceptionType(Component.translatable("commands.mystcraft.fail.not_age"));
    private static final SimpleCommandExceptionType NO_WORLD = new SimpleCommandExceptionType(Component.translatable("commands.mystcraft.tpx.fail.noworld"));
    private static final SimpleCommandExceptionType NO_SUBJECT = new SimpleCommandExceptionType(Component.translatable("commands.mystcraft.tpx.fail.nosubject"));
    private static final SimpleCommandExceptionType NOT_IMPLEMENTED = new SimpleCommandExceptionType(Component.translatable("commands.mystcraft.fail.not_implemented"));
    private static final SimpleCommandExceptionType DEBUG_ADDRESS = new SimpleCommandExceptionType(Component.translatable("commands.mystcraft.debug.address.invalid"));

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(tpx());
        dispatcher.register(create());
        dispatcher.register(agebook());
        dispatcher.register(toggleInstability("myst-twi"));
        dispatcher.register(toggleInstability("myst-toggleworldinstability"));
        if (MystcraftConfig.SPAWN_METEOR_COMMAND.get()) dispatcher.register(spawnMeteor());
        dispatcher.register(permissions());
        dispatcher.register(regenChunk());
        dispatcher.register(reprofile());
        dispatcher.register(debug());
        dispatcher.register(time());
        dispatcher.register(toggleDownfall());
        dispatcher.register(scene());
        dispatcher.register(visit());
        dispatcher.register(qaShelf());
    }

    // --- /myst-qa-shelf (lecterns with preset books for the visual QA matrix) --------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> qaShelf() {
        return Commands.literal("myst-qa-shelf")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.level() instanceof ServerLevel sl ? sl : ctx.getSource().getLevel();
                    int placed = QaShelf.build(level, player);
                    ctx.getSource().sendSuccess(() -> Component.literal("QA shelf: " + placed + " lecterns placed - each book is bound to a fixed-seed Age; see [qa] in the log for what to look for"), false);
                    return placed;
                });
    }

    // --- /myst-scene (debug showcase) ----------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> scene() {
        LiteralArgumentBuilder<CommandSourceStack> closeup = Commands.literal("closeup");
        for (DebugScene.Element element : DebugScene.Element.values()) {
            closeup.then(Commands.literal(element.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ServerLevel level = player.level() instanceof ServerLevel sl ? sl : ctx.getSource().getLevel();
                if (!DebugScene.closeup(level, player, element)) {
                    ctx.getSource().sendFailure(Component.literal("No debug scene in this dimension yet - run /myst-scene first"));
                    return 0;
                }
                return 1;
            }));
        }
        LiteralArgumentBuilder<CommandSourceStack> open = Commands.literal("open");
        for (DebugScene.Element element : DebugScene.Element.values()) {
            open.then(Commands.literal(element.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ServerLevel level = player.level() instanceof ServerLevel sl ? sl : ctx.getSource().getLevel();
                if (!DebugScene.open(level, player, element)) {
                    ctx.getSource().sendFailure(Component.literal("Nothing to open for " + element + " (run /myst-scene first)"));
                    return 0;
                }
                return 1;
            }));
        }
        LiteralArgumentBuilder<CommandSourceStack> use = Commands.literal("use");
        for (DebugScene.UsableItem item : DebugScene.UsableItem.values()) {
            use.then(Commands.literal(item.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ServerLevel level = player.level() instanceof ServerLevel sl ? sl : ctx.getSource().getLevel();
                return DebugScene.use(level, player, item) ? 1 : 0;
            }));
        }
        return Commands.literal("myst-scene")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(closeup)
                .then(open)
                .then(use)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.level() instanceof ServerLevel sl ? sl : ctx.getSource().getLevel();
                    BlockPos origin = player.blockPosition().offset(-DebugScene.WIDTH / 2, 0, -3);
                    BlockPos view = DebugScene.build(level, origin, player);
                    ctx.getSource().sendSuccess(() -> Component.literal("Debug scene built; viewer at " + view.toShortString()
                            + " (see [scene] in the log)"), true);
                    return 1;
                });
    }

    // --- /myst-visit [name] (create an Age and link the player into it through the normal link path) --------------

    private static LiteralArgumentBuilder<CommandSourceStack> visit() {
        return Commands.literal("myst-visit")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> visitAge(ctx, null))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> visitAge(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    private static int visitAge(CommandContext<CommandSourceStack> ctx, @Nullable String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        ItemStack book = new ItemStack(ModItems.DESCRIPTIVE_BOOK.get());
        LinkingItem.setLinkInfo(book, LinkInfo.EMPTY.withDisplayName(name == null || name.isBlank() ? LinkInfo.DEFAULT_NAME : name)
                .withFlag(LinkProperty.GENERATE_PLATFORM, true));
        DescriptiveBookItem.setPages(book, List.of(PageItem.createLinkPanel(Set.of())));
        DescriptiveBookItem.checkFirstLink(book, server);
        LinkInfo info = LinkingItem.getLinkInfo(book);
        AgeData data = DescriptiveBookItem.getAgeData(server, book);
        if (data == null) throw NOT_AN_AGE.create();
        player.getInventory().placeItemBackInInventory(book.copy());
        boolean ok = LinkController.travelEntity(player, info.withProp(LinkProperty.PROP_SOUND, LinkingItem.SOUND_PORTAL_LINK));
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("Link refused - see [link] in the log"));
            return 0;
        }
        success(ctx.getSource(), "commands.mystcraft.create.success", data.name(), data.levelKey().identifier().toString());
        return 1;
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private static ServerLevel ageLevel(CommandContext<CommandSourceStack> ctx, String argName) throws CommandSyntaxException {
        ServerLevel level = DimensionArgument.getDimension(ctx, argName);
        if (!AgeManager.isAge(level.dimension())) throw NOT_AN_AGE.create();
        return level;
    }

    private static ServerLevel senderAgeLevel(CommandSourceStack src) throws CommandSyntaxException {
        ServerLevel level = src.getLevel();
        if (!AgeManager.isAge(level.dimension())) throw NOT_AN_AGE.create();
        return level;
    }

    private static AgeData ageData(ServerLevel level) throws CommandSyntaxException {
        MinecraftServer server = level.getServer();
        AgeData data = server == null ? null : AgeManager.get(server, level.dimension());
        if (data == null) throw NOT_AN_AGE.create();
        return data;
    }

    private static void success(CommandSourceStack src, String key, Object... args) {
        src.sendSuccess(() -> Component.translatable(key, args), true);
    }

    private static Component dimName(ServerLevel level) {
        return Component.literal(level.dimension().identifier().toString());
    }

    // --- /tpx ----------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> tpx() {
        return Commands.literal("tpx")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> tpToPlayer(ctx, ctx.getSource().getEntityOrException(), EntityArgument.getPlayer(ctx, "target"))))
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(ctx -> tpToDimension(ctx, ctx.getSource().getEntityOrException(), DimensionArgument.getDimension(ctx, "dimension"), null))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> tpToDimension(ctx, ctx.getSource().getEntityOrException(), DimensionArgument.getDimension(ctx, "dimension"), Vec3Argument.getVec3(ctx, "pos")))))
                .then(Commands.argument("subject", EntityArgument.entity())
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> tpToPlayer(ctx, EntityArgument.getEntity(ctx, "subject"), EntityArgument.getPlayer(ctx, "target"))))
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> tpToDimension(ctx, EntityArgument.getEntity(ctx, "subject"), DimensionArgument.getDimension(ctx, "dimension"), null))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> tpToDimension(ctx, EntityArgument.getEntity(ctx, "subject"), DimensionArgument.getDimension(ctx, "dimension"), Vec3Argument.getVec3(ctx, "pos"))))));
    }

    private static int tpToPlayer(CommandContext<CommandSourceStack> ctx, Entity subject, ServerPlayer target) throws CommandSyntaxException {
        if (subject == null) throw NO_SUBJECT.create();
        LinkInfo info = LinkInfo.fromPosition(target, target.getPlainTextName(), AgeData.uuidFromLevelKey(target.level().dimension()));
        return travel(ctx, subject, info);
    }

    private static int tpToDimension(CommandContext<CommandSourceStack> ctx, Entity subject, ServerLevel level, @Nullable Vec3 pos) throws CommandSyntaxException {
        if (subject == null) throw NO_SUBJECT.create();
        if (level == null) throw NO_WORLD.create();
        Optional<BlockPos> spawn = pos == null ? Optional.empty() : Optional.of(BlockPos.containing(pos));
        LinkInfo info = new LinkInfo(Optional.of(level.dimension()), Optional.ofNullable(AgeData.uuidFromLevelKey(level.dimension())),
                spawn, subject.getYRot(), level.dimension().identifier().toString(), Set.of(), Map.of());
        return travel(ctx, subject, info);
    }

    private static int travel(CommandContext<CommandSourceStack> ctx, Entity subject, LinkInfo info) {
        LinkInfo link = info.withFlag(LinkProperty.INTRA_LINKING, true).withFlag(LinkProperty.OP_TP, true);
        boolean ok = LinkController.travelEntity(subject, link);
        if (ok) {
            success(ctx.getSource(), "commands.mystcraft.tpx.success", subject.getDisplayName(), link.displayName());
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("commands.mystcraft.tpx.fail.refused"));
        return 0;
    }

    // --- /myst-create ----------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("myst-create")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .executes(ctx -> createAge(ctx, null))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> createAge(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    private static int createAge(CommandContext<CommandSourceStack> ctx, @Nullable String name) {
        MinecraftServer server = ctx.getSource().getServer();
        AgeData data = AgeManager.createAge(server);
        if (name != null && !name.isBlank()) data.setName(name);
        List<ItemStack> pages = new ArrayList<>();
        pages.add(PageItem.createLinkPanel(Set.of()));
        data.setPages(pages);
        AgeManager.getOrCreateLevel(server, data);
        success(ctx.getSource(), "commands.mystcraft.create.success", data.name(), data.levelKey().identifier().toString());
        return 1;
    }

    // --- /myst-agebook -------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> agebook() {
        return Commands.literal("myst-agebook")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> giveAgebook(ctx, senderAgeLevel(ctx.getSource())))
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(ctx -> giveAgebook(ctx, ageLevel(ctx, "dimension"))));
    }

    private static int giveAgebook(CommandContext<CommandSourceStack> ctx, ServerLevel level) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AgeData data = ageData(level);
        ItemStack book = DescriptiveBookItem.create(player, data.pages(), data.name());
        DescriptiveBookItem.initializeForAge(book, data);
        player.getInventory().placeItemBackInInventory(book);
        success(ctx.getSource(), "commands.mystcraft.agebook.success", player.getDisplayName(), data.name());
        return 1;
    }

    // --- /myst-twi -------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> toggleInstability(String name) {
        return Commands.literal(name)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> setInstability(ctx, senderAgeLevel(ctx.getSource()), null))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> setInstability(ctx, senderAgeLevel(ctx.getSource()), BoolArgumentType.getBool(ctx, "enabled"))))
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(ctx -> setInstability(ctx, ageLevel(ctx, "dimension"), null))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> setInstability(ctx, ageLevel(ctx, "dimension"), BoolArgumentType.getBool(ctx, "enabled")))));
    }

    private static int setInstability(CommandContext<CommandSourceStack> ctx, ServerLevel level, @Nullable Boolean value) throws CommandSyntaxException {
        AgeData data = ageData(level);
        boolean enabled = value != null ? value : !data.instabilityEnabled();
        data.setInstabilityEnabled(enabled);
        success(ctx.getSource(), "commands.mystcraft.twi.success", dimName(level), enabled);
        return 1;
    }

    // --- /myst-spawnmeteor ---------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> spawnMeteor() {
        return Commands.literal("myst-spawnmeteor")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> spawnMeteor(ctx, 1.0f, 0, null))
                .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1f, 20f))
                        .executes(ctx -> spawnMeteor(ctx, FloatArgumentType.getFloat(ctx, "scale"), 0, null))
                        .then(Commands.argument("penetration", IntegerArgumentType.integer(0))
                                .executes(ctx -> spawnMeteor(ctx, FloatArgumentType.getFloat(ctx, "scale"), IntegerArgumentType.getInteger(ctx, "penetration"), null))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> spawnMeteor(ctx, FloatArgumentType.getFloat(ctx, "scale"), IntegerArgumentType.getInteger(ctx, "penetration"), Vec3Argument.getVec3(ctx, "pos"))))));
    }

    private static int spawnMeteor(CommandContext<CommandSourceStack> ctx, float scale, int penetration, @Nullable Vec3 pos) {
        CommandSourceStack src = ctx.getSource();
        Vec3 at = pos == null ? src.getPosition() : pos;
        MeteorEntity.spawn(src.getLevel(), new Vec3(at.x, 500, at.z), new Vec3(0, -3, 0), scale, penetration);
        success(src, "commands.mystcraft.meteor.success", (int) at.x, (int) at.z);
        return 1;
    }

    // --- /myst-permissions ---------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> permissions() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("myst-permissions")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        var player = Commands.argument("player", StringArgumentType.word());
        for (String mode : new String[]{"restrict", "permit"}) {
            for (String direction : new String[]{"entry", "depart"}) {
                boolean permit = mode.equals("permit");
                boolean entry = direction.equals("entry");
                player.then(Commands.literal(mode).then(Commands.literal(direction)
                        .then(Commands.literal("all").executes(ctx -> setPermission(ctx, permit, entry, null)))
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> setPermission(ctx, permit, entry, DimensionArgument.getDimension(ctx, "dimension").dimension())))));
            }
        }
        return root.then(player);
    }

    private static int setPermission(CommandContext<CommandSourceStack> ctx, boolean permit, boolean entry, @Nullable ResourceKey<Level> dim) {
        String playerName = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        LinkPermissions permissions = LinkPermissions.get(server);
        if (entry) {
            if (permit) permissions.permitEntry(playerName, dim); else permissions.restrictEntry(playerName, dim);
        } else {
            if (permit) permissions.permitDepart(playerName, dim); else permissions.restrictDepart(playerName, dim);
        }
        success(ctx.getSource(), "commands.mystcraft.permissions.success", playerName, permit ? "permit" : "restrict",
                entry ? "entry" : "depart", dim == null ? "all" : dim.identifier().toString());
        return 1;
    }

    // --- /myst-regenchunk (stub) -----------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> regenChunk() {
        return Commands.literal("myst-regenchunk")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .executes(ctx -> { throw NOT_IMPLEMENTED.create(); })
                .then(Commands.argument("range", IntegerArgumentType.integer(0, 16))
                        .executes(ctx -> { throw NOT_IMPLEMENTED.create(); }));
        // TODO: unload + regenerate chunks in range (needs chunk-map surgery; not available through public 26.1 API).
    }

    // --- /myst-reprofile -------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> reprofile() {
        return Commands.literal("myst-reprofile")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .executes(ctx -> reprofile(ctx, senderAgeLevel(ctx.getSource())))
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(ctx -> reprofile(ctx, ageLevel(ctx, "dimension"))));
    }

    private static int reprofile(CommandContext<CommandSourceStack> ctx, ServerLevel level) {
        ChunkProfiler.get(level).clear();
        InstabilityController controller = InstabilityController.get(level);
        if (controller != null) controller.invalidateProfile();
        success(ctx.getSource(), "commands.mystcraft.reprofile.success", dimName(level));
        return 1;
    }

    // --- /myst-dbg -------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> debug() {
        return Commands.literal("myst-dbg")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("read").then(Commands.argument("address", StringArgumentType.greedyString())
                        .executes(ctx -> debugRead(ctx, StringArgumentType.getString(ctx, "address")))))
                .then(Commands.literal("set").then(Commands.argument("address", StringArgumentType.word())
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .executes(ctx -> debugSet(ctx, StringArgumentType.getString(ctx, "address"), StringArgumentType.getString(ctx, "value"))))))
                .then(Commands.literal("run").then(Commands.argument("address", StringArgumentType.word())
                        .executes(ctx -> debugRun(ctx, StringArgumentType.getString(ctx, "address"), null))
                        .then(Commands.argument("arg", StringArgumentType.greedyString())
                                .executes(ctx -> debugRun(ctx, StringArgumentType.getString(ctx, "address"), StringArgumentType.getString(ctx, "arg"))))));
    }

    /**
     * Debug hierarchy (addresses relative to the sender's Age): {@code instability.score}, {@code instability.debug},
     * {@code instability.symbol}, {@code instability.blocks}, {@code instability.bonus}, {@code instability.profiled_chunks},
     * {@code instability.providers}, {@code instability.decks}, {@code symbols}; {@code global.baseline}.
     */
    private static int debugRead(CommandContext<CommandSourceStack> ctx, String address) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        String value = switch (address) {
            case "global.baseline" -> com.techbucketdivision.mystcraft.instability.BaselineProfiler.all().toString();
            case "global.providers" -> com.techbucketdivision.mystcraft.instability.InstabilityManager.allProviders().toString();
            default -> {
                ServerLevel level = senderAgeLevel(src);
                AgeController age = AgeControllers.server(level);
                InstabilityController inst = InstabilityController.get(level);
                if (age == null || inst == null) throw NOT_AN_AGE.create();
                yield switch (address) {
                    case "instability", "instability.score" -> inst.score() + " (quantised " + inst.getInstabilityScore() + ")";
                    case "instability.debug" -> Integer.toString(inst.debugInstability());
                    case "instability.symbol" -> Integer.toString(age.symbolInstability());
                    case "instability.blocks" -> String.valueOf(inst.blockInstability()) + " " + String.valueOf(ChunkProfiler.get(level).lastSplit());
                    case "instability.bonus" -> Integer.toString(com.techbucketdivision.mystcraft.instability.InstabilityBonusManager.get(level).total());
                    case "instability.profiled_chunks" -> Integer.toString(inst.profiledChunks());
                    case "instability.providers" -> inst.providerLevels().toString();
                    case "instability.decks" -> {
                        StringBuilder sb = new StringBuilder();
                        for (String deck : com.techbucketdivision.mystcraft.instability.InstabilityManager.decks()) {
                            sb.append(deck).append('=').append(inst.deck(deck)).append(' ');
                        }
                        yield sb.toString().trim();
                    }
                    case "symbols" -> age.ageData().symbols().toString();
                    case "time" -> Long.toString(age.ageData().worldTime());
                    default -> throw DEBUG_ADDRESS.create();
                };
            }
        };
        src.sendSuccess(() -> Component.literal(address + " = " + value), false);
        return 1;
    }

    private static int debugSet(CommandContext<CommandSourceStack> ctx, String address, String value) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = senderAgeLevel(src);
        InstabilityController inst = InstabilityController.get(level);
        if (inst == null) throw NOT_AN_AGE.create();
        switch (address) {
            case "instability.debug" -> {
                try {
                    inst.setDebugInstability(Integer.parseInt(value.trim()));
                } catch (NumberFormatException e) {
                    throw DEBUG_ADDRESS.create();
                }
            }
            case "instability.enabled" -> ageData(level).setInstabilityEnabled(Boolean.parseBoolean(value.trim()));
            default -> throw DEBUG_ADDRESS.create();
        }
        src.sendSuccess(() -> Component.literal(address + " set to " + value), true);
        return 1;
    }

    private static int debugRun(CommandContext<CommandSourceStack> ctx, String address, @Nullable String arg) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        switch (address) {
            case "experimental.mark_dead" -> {
                ServerLevel level = senderAgeLevel(src);
                if (!AgeManager.markDead(src.getServer(), level.dimension())) throw NOT_AN_AGE.create();
                src.sendSuccess(() -> Component.translatable("commands.mystcraft.debug.marked_dead", dimName(level)), true);
            }
            case "instability.reconstruct" -> {
                InstabilityController inst = InstabilityController.get(senderAgeLevel(src));
                if (inst == null) throw NOT_AN_AGE.create();
                inst.reconstruct();
                src.sendSuccess(() -> Component.literal("Instability reconstructed"), true);
            }
            default -> throw DEBUG_ADDRESS.create();
        }
        return 1;
    }

    // --- /myst-time ------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> time() {
        return Commands.literal("myst-time")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("set")
                        .then(Commands.literal("day").executes(ctx -> setTime(ctx, senderAgeLevel(ctx.getSource()), TimeTarget.DAY, 0))
                                .then(timeScope(ctx -> setTime(ctx, ageLevel(ctx, "dimension"), TimeTarget.DAY, 0), ctx -> setTimeAll(ctx, TimeTarget.DAY, 0))))
                        .then(Commands.literal("night").executes(ctx -> setTime(ctx, senderAgeLevel(ctx.getSource()), TimeTarget.NIGHT, 0))
                                .then(timeScope(ctx -> setTime(ctx, ageLevel(ctx, "dimension"), TimeTarget.NIGHT, 0), ctx -> setTimeAll(ctx, TimeTarget.NIGHT, 0))))
                        .then(Commands.argument("value", LongArgumentType.longArg(0))
                                .executes(ctx -> setTime(ctx, senderAgeLevel(ctx.getSource()), TimeTarget.VALUE, LongArgumentType.getLong(ctx, "value")))
                                .then(timeScope(ctx -> setTime(ctx, ageLevel(ctx, "dimension"), TimeTarget.VALUE, LongArgumentType.getLong(ctx, "value")),
                                        ctx -> setTimeAll(ctx, TimeTarget.VALUE, LongArgumentType.getLong(ctx, "value"))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("value", LongArgumentType.longArg())
                                .executes(ctx -> addTime(ctx, senderAgeLevel(ctx.getSource()), LongArgumentType.getLong(ctx, "value")))
                                .then(timeScope(ctx -> addTime(ctx, ageLevel(ctx, "dimension"), LongArgumentType.getLong(ctx, "value")),
                                        ctx -> addTimeAll(ctx, LongArgumentType.getLong(ctx, "value"))))));
    }

    private enum TimeTarget { DAY, NIGHT, VALUE }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> timeScope(
            com.mojang.brigadier.Command<CommandSourceStack> perDimension, com.mojang.brigadier.Command<CommandSourceStack> all) {
        return Commands.argument("dimension", DimensionArgument.dimension()).executes(perDimension)
                .then(Commands.literal("all").executes(all));
    }

    private static long resolveTime(ServerLevel level, AgeData data, TimeTarget target, long value) {
        return switch (target) {
            case VALUE -> value;
            case DAY -> nextCelestialCrossing(level, data, 0.78f);
            case NIGHT -> nextCelestialCrossing(level, data, 0.30f);
        };
    }

    /** First future time at which the Age's celestial angle rises through {@code target} (REQUIREMENTS §12 /time). */
    private static long nextCelestialCrossing(ServerLevel level, AgeData data, float target) {
        AgeController age = AgeControllers.server(level);
        long now = data.worldTime();
        if (age == null) return now + 1;
        int step = 20;
        float prev = age.celestialAngle(now, 0f);
        for (long t = now + step; t < now + 200_000L; t += step) {
            float a = age.celestialAngle(t, 0f);
            if (prev < target && a >= target) return t;
            prev = a;
        }
        return now + 24000L;
    }

    private static int setTime(CommandContext<CommandSourceStack> ctx, ServerLevel level, TimeTarget target, long value) throws CommandSyntaxException {
        AgeData data = ageData(level);
        long time = resolveTime(level, data, target, value);
        data.setWorldTime(time);
        data.markDirty();
        success(ctx.getSource(), "commands.mystcraft.time.set", time, dimName(level));
        return 1;
    }

    private static int setTimeAll(CommandContext<CommandSourceStack> ctx, TimeTarget target, long value) {
        long last = value;
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            if (!AgeManager.isAge(level.dimension())) continue;
            AgeData data = AgeManager.get(ctx.getSource().getServer(), level.dimension());
            if (data == null) continue;
            last = resolveTime(level, data, target, value);
            data.setWorldTime(last);
            data.markDirty();
        }
        success(ctx.getSource(), "commands.mystcraft.time.set.all", last);
        return 1;
    }

    private static int addTime(CommandContext<CommandSourceStack> ctx, ServerLevel level, long value) throws CommandSyntaxException {
        AgeData data = ageData(level);
        data.setWorldTime(Math.max(0, data.worldTime() + value));
        data.markDirty();
        success(ctx.getSource(), "commands.mystcraft.time.added", value, dimName(level));
        return 1;
    }

    private static int addTimeAll(CommandContext<CommandSourceStack> ctx, long value) {
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            if (!AgeManager.isAge(level.dimension())) continue;
            AgeData data = AgeManager.get(ctx.getSource().getServer(), level.dimension());
            if (data == null) continue;
            data.setWorldTime(Math.max(0, data.worldTime() + value));
            data.markDirty();
        }
        success(ctx.getSource(), "commands.mystcraft.time.added.all", value);
        return 1;
    }

    // --- /myst-toggledownfall ---------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> toggleDownfall() {
        return Commands.literal("myst-toggledownfall")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> toggleDownfall(ctx, ctx.getSource().getLevel()))
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(ctx -> toggleDownfall(ctx, DimensionArgument.getDimension(ctx, "dimension"))));
    }

    private static int toggleDownfall(CommandContext<CommandSourceStack> ctx, ServerLevel level) throws CommandSyntaxException {
        AgeController age = AgeControllers.server(level);
        WeatherController weather = age == null ? null : age.weather();
        if (weather != null) {
            weather.togglePrecipitation();
            ageData(level).markDirty();
        } else {
            // Vanilla levels share one server-global weather state in 26.1.
            MinecraftServer server = ctx.getSource().getServer();
            boolean raining = level.isRaining();
            server.setWeatherParameters(raining ? 6000 : 0, raining ? 0 : 6000, !raining, !raining);
        }
        success(ctx.getSource(), "commands.mystcraft.downfall.success", dimName(level));
        return 1;
    }
}

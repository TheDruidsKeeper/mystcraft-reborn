package com.tbd.mystcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.age.AgeController;
import com.tbd.mystcraft.age.AgeControllers;
import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.age.AgeManager;
import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.api.linking.LinkProperty;
import com.tbd.mystcraft.api.symbol.logic.WeatherController;
import com.tbd.mystcraft.config.MystcraftConfig;
import com.tbd.mystcraft.entity.MeteorEntity;
import com.tbd.mystcraft.instability.ChunkProfiler;
import com.tbd.mystcraft.instability.InstabilityBonusManager;
import com.tbd.mystcraft.instability.InstabilityController;
import com.tbd.mystcraft.instability.InstabilityManager;
import com.tbd.mystcraft.item.DescriptiveBookItem;
import com.tbd.mystcraft.item.LinkingItem;
import com.tbd.mystcraft.linking.LinkController;
import com.tbd.mystcraft.linking.LinkPermissions;
import com.tbd.mystcraft.facility.FacilityProtection;
import com.tbd.mystcraft.facility.FacilityState;
import com.tbd.mystcraft.world.structure.FacilityLocator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The mod's commands (docs/DEVELOPMENT.md "Commands"):
 * <ul>
 *   <li>{@code /myst …} (gamemasters): everyday Age administration and QA — {@code visit}, {@code create}, {@code book},
 *       {@code locate facility}, {@code facility status|solve}, {@code time}, {@code weather}, {@code instability},
 *       {@code permissions}, {@code retire}.</li>
 *   <li>{@code /myst-dev …} (admins): the debug showcase ({@code scene}, driven by the client self-check) and the
 *       visual QA matrix ({@code qa-shelf}).</li>
 * </ul>
 * Every subcommand that takes a dimension defaults to the sender's Age. Messages are lang keys {@code commands.mystcraft.*}.
 */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class MystcraftCommands {
    private MystcraftCommands() {}

    private static final SimpleCommandExceptionType NOT_AN_AGE = new SimpleCommandExceptionType(Component.translatable("commands.mystcraft.fail.not_age"));

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("myst")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(visit())
                .then(create())
                .then(book())
                .then(locate())
                .then(facility())
                .then(time())
                .then(weather())
                .then(instability())
                .then(permissions())
                .then(retire()));

        dispatcher.register(Commands.literal("myst-dev")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(scene())
                .then(qaShelf())
                .then(qaVisit())
                .then(facilityTp()));
    }

    // --- /myst visit [name] --------------------------------------------------------------------------------------

    /** Creates an Age (optionally titled) and links the player in through the normal link path. */
    private static LiteralArgumentBuilder<CommandSourceStack> visit() {
        return Commands.literal("visit")
                .executes(ctx -> visitAge(ctx, null))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> visitAge(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    private static int visitAge(CommandContext<CommandSourceStack> ctx, @Nullable String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        ItemStack book = DescriptiveBookItem.createBound(server, name == null || name.isBlank() ? LinkInfo.DEFAULT_NAME : name, 0, List.of());
        LinkInfo info = LinkingItem.getLinkInfo(book);
        AgeData data = DescriptiveBookItem.getAgeData(server, book);
        if (data == null) throw NOT_AN_AGE.create();
        player.getInventory().placeItemBackInInventory(book.copy());
        boolean ok = LinkController.travelEntity(player, info.withProp(LinkProperty.PROP_SOUND, LinkingItem.SOUND_PORTAL_LINK));
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatable("commands.mystcraft.link.refused"));
            return 0;
        }
        success(ctx.getSource(), "commands.mystcraft.create.success", data.name(), data.levelKey().identifier().toString());
        return 1;
    }

    // --- /myst create [name] -------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("create")
                .executes(ctx -> createAge(ctx, null))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> createAge(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    private static int createAge(CommandContext<CommandSourceStack> ctx, @Nullable String name) {
        MinecraftServer server = ctx.getSource().getServer();
        ItemStack book = DescriptiveBookItem.createBound(server, name == null || name.isBlank() ? LinkInfo.DEFAULT_NAME : name, 0, List.of());
        AgeData data = DescriptiveBookItem.getAgeData(server, book);
        if (data == null) throw new IllegalStateException("book did not bind");
        AgeManager.getOrCreateLevel(server, data);
        success(ctx.getSource(), "commands.mystcraft.create.success", data.name(), data.levelKey().identifier().toString());
        return 1;
    }

    // --- /myst book [dimension] ----------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> book() {
        return Commands.literal("book")
                .executes(ctx -> giveBook(ctx, senderAgeLevel(ctx.getSource())))
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(ctx -> giveBook(ctx, ageLevel(ctx, "dimension"))));
    }

    private static int giveBook(CommandContext<CommandSourceStack> ctx, ServerLevel level) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AgeData data = ageData(level);
        ItemStack book = DescriptiveBookItem.create(player, data.pages(), data.name());
        DescriptiveBookItem.initializeForAge(book, data);
        player.getInventory().placeItemBackInInventory(book);
        success(ctx.getSource(), "commands.mystcraft.book.success", player.getDisplayName(), data.name());
        return 1;
    }

    // --- /myst locate facility -----------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> locate() {
        return Commands.literal("locate").then(Commands.literal("facility").executes(ctx -> {
            ServerLevel level = senderAgeLevel(ctx.getSource());
            ChunkPos chunk = FacilityLocator.facilityChunk(level);
            if (chunk == null) {
                ctx.getSource().sendFailure(Component.translatable("commands.mystcraft.locate.facility.none"));
                return 0;
            }
            int x = chunk.getMiddleBlockX();
            int z = chunk.getMiddleBlockZ();
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            success(ctx.getSource(), "commands.mystcraft.locate.facility.success", chunk.toString(), x + " " + y + " " + z);
            return 1;
        }));
    }

    // --- /myst-dev facility-tp entrance|lobby|vault ---------------------------------------------------------------

    /** Teleports into this Age's generated Facility (QA; generates the needed chunks). */
    private static LiteralArgumentBuilder<CommandSourceStack> facilityTp() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("facility-tp");
        for (FacilityLocator.Spot spot : FacilityLocator.Spot.values()) {
            root.then(Commands.literal(spot.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ServerLevel level = senderAgeLevel(ctx.getSource());
                FacilityLocator.View view = FacilityLocator.find(level, spot);
                if (view == null) {
                    ctx.getSource().sendFailure(Component.translatable("commands.mystcraft.locate.facility.none"));
                    return 0;
                }
                BlockPos pos = view.pos();
                level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, java.util.Set.of(), view.yaw(), view.pitch(), false);
                Mystcraft.LOGGER.info("[facility] teleported {} to the {} at {}", player.getScoreboardName(), spot, pos.toShortString());
                success(ctx.getSource(), "commands.mystcraft.facility_tp.success", spot.name().toLowerCase(java.util.Locale.ROOT), pos.toShortString());
                return 1;
            }));
        }
        return root;
    }

    // --- /myst facility status|solve ------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> facility() {
        return Commands.literal("facility")
                .then(Commands.literal("status").executes(ctx -> {
                    ServerLevel level = senderAgeLevel(ctx.getSource());
                    boolean solved = FacilityState.isSolved(level);
                    boolean protects = FacilityProtection.enabled() && !solved;
                    success(ctx.getSource(), "commands.mystcraft.facility.status",
                            Component.translatable(solved ? "commands.mystcraft.facility.solved" : "commands.mystcraft.facility.unsolved"),
                            Component.translatable(protects ? "commands.mystcraft.facility.protected" : "commands.mystcraft.facility.unprotected"));
                    return solved ? 1 : 0;
                }))
                .then(Commands.literal("solve").executes(ctx -> {
                    ServerLevel level = senderAgeLevel(ctx.getSource());
                    FacilityState.markSolved(level);
                    success(ctx.getSource(), "commands.mystcraft.facility.solve.success");
                    return 1;
                }));
    }

    // --- /myst time set <day|night|value> [dimension|all] / add <value> [dimension|all] ---------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> time() {
        return Commands.literal("time")
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

    private static ArgumentBuilder<CommandSourceStack, ?> timeScope(com.mojang.brigadier.Command<CommandSourceStack> perDimension,
                                                                    com.mojang.brigadier.Command<CommandSourceStack> all) {
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

    /** First future time at which the Age's celestial angle rises through {@code target} (Ages have their own clocks). */
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
            AgeData data = AgeManager.isAge(level.dimension()) ? AgeManager.get(ctx.getSource().getServer(), level.dimension()) : null;
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
            AgeData data = AgeManager.isAge(level.dimension()) ? AgeManager.get(ctx.getSource().getServer(), level.dimension()) : null;
            if (data == null) continue;
            data.setWorldTime(Math.max(0, data.worldTime() + value));
            data.markDirty();
        }
        success(ctx.getSource(), "commands.mystcraft.time.added.all", value);
        return 1;
    }

    // --- /myst weather toggle [dimension] ------------------------------------------------------------------------

    /** Toggles precipitation of an Age's {@link WeatherController}; in vanilla levels toggles the shared server weather. */
    private static LiteralArgumentBuilder<CommandSourceStack> weather() {
        return Commands.literal("weather").then(Commands.literal("toggle")
                .executes(ctx -> toggleDownfall(ctx, ctx.getSource().getLevel()))
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(ctx -> toggleDownfall(ctx, DimensionArgument.getDimension(ctx, "dimension")))));
    }

    private static int toggleDownfall(CommandContext<CommandSourceStack> ctx, ServerLevel level) throws CommandSyntaxException {
        AgeController age = AgeControllers.server(level);
        WeatherController weather = age == null ? null : age.weather();
        if (weather != null) {
            weather.togglePrecipitation();
            ageData(level).markDirty();
        } else {
            MinecraftServer server = ctx.getSource().getServer();
            boolean raining = level.isRaining();
            server.setWeatherParameters(raining ? 6000 : 0, raining ? 0 : 6000, !raining, !raining);
        }
        success(ctx.getSource(), "commands.mystcraft.weather.toggled", dimName(level));
        return 1;
    }

    // --- /myst instability status|toggle [enabled]|reprofile|meteor [scale [penetration [pos]]] ---------------------

    private static LiteralArgumentBuilder<CommandSourceStack> instability() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("instability")
                .then(Commands.literal("status").executes(ctx -> instabilityStatus(ctx, senderAgeLevel(ctx.getSource())))
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> instabilityStatus(ctx, ageLevel(ctx, "dimension")))))
                .then(Commands.literal("toggle").executes(ctx -> setInstability(ctx, senderAgeLevel(ctx.getSource()), null))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> setInstability(ctx, senderAgeLevel(ctx.getSource()), BoolArgumentType.getBool(ctx, "enabled"))))
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> setInstability(ctx, ageLevel(ctx, "dimension"), null))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> setInstability(ctx, ageLevel(ctx, "dimension"), BoolArgumentType.getBool(ctx, "enabled"))))))
                .then(Commands.literal("reprofile").executes(ctx -> reprofile(ctx, senderAgeLevel(ctx.getSource())))
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> reprofile(ctx, ageLevel(ctx, "dimension")))));
        if (MystcraftConfig.SPAWN_METEOR_COMMAND.get()) {
            root.then(Commands.literal("meteor")
                    .executes(ctx -> spawnMeteor(ctx, 1.0f, 0, null))
                    .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1f, 20f))
                            .executes(ctx -> spawnMeteor(ctx, FloatArgumentType.getFloat(ctx, "scale"), 0, null))
                            .then(Commands.argument("penetration", IntegerArgumentType.integer(0))
                                    .executes(ctx -> spawnMeteor(ctx, FloatArgumentType.getFloat(ctx, "scale"), IntegerArgumentType.getInteger(ctx, "penetration"), null))
                                    .then(Commands.argument("pos", Vec3Argument.vec3())
                                            .executes(ctx -> spawnMeteor(ctx, FloatArgumentType.getFloat(ctx, "scale"), IntegerArgumentType.getInteger(ctx, "penetration"), Vec3Argument.getVec3(ctx, "pos")))))));
        }
        return root;
    }

    /** One readable line per instability input: score, symbol / block / bonus parts, provider levels, decks, profiled chunks. */
    private static int instabilityStatus(CommandContext<CommandSourceStack> ctx, ServerLevel level) throws CommandSyntaxException {
        AgeController age = AgeControllers.server(level);
        InstabilityController inst = InstabilityController.get(level);
        AgeData data = ageData(level);
        if (age == null || inst == null) throw NOT_AN_AGE.create();
        StringBuilder decks = new StringBuilder();
        for (String deck : InstabilityManager.decks()) decks.append(deck).append('=').append(inst.deck(deck)).append(' ');
        List<Component> lines = List.of(
                Component.literal(dimName(level).getString() + " instability " + (data.instabilityEnabled() ? "enabled" : "DISABLED")),
                Component.literal("score " + inst.score() + " (quantised " + inst.getInstabilityScore() + ")"
                        + " = symbols " + age.symbolInstability() + " + blocks " + inst.blockInstability()
                        + " + bonus " + InstabilityBonusManager.get(level).total() + " + debug " + inst.debugInstability()),
                Component.literal("providers " + inst.providerLevels()),
                Component.literal("decks " + decks.toString().trim()),
                Component.literal("profiled chunks " + inst.profiledChunks() + ", last split " + ChunkProfiler.get(level).lastSplit()));
        for (Component line : lines) ctx.getSource().sendSuccess(() -> line, false);
        return 1;
    }

    private static int setInstability(CommandContext<CommandSourceStack> ctx, ServerLevel level, @Nullable Boolean value) throws CommandSyntaxException {
        AgeData data = ageData(level);
        boolean enabled = value != null ? value : !data.instabilityEnabled();
        data.setInstabilityEnabled(enabled);
        success(ctx.getSource(), "commands.mystcraft.instability.toggled", dimName(level), enabled);
        return 1;
    }

    private static int reprofile(CommandContext<CommandSourceStack> ctx, ServerLevel level) {
        ChunkProfiler.get(level).clear();
        InstabilityController controller = InstabilityController.get(level);
        if (controller != null) controller.invalidateProfile();
        success(ctx.getSource(), "commands.mystcraft.instability.reprofiled", dimName(level));
        return 1;
    }

    private static int spawnMeteor(CommandContext<CommandSourceStack> ctx, float scale, int penetration, @Nullable Vec3 pos) {
        CommandSourceStack src = ctx.getSource();
        Vec3 at = pos == null ? src.getPosition() : pos;
        MeteorEntity.spawn(src.getLevel(), new Vec3(at.x, 500, at.z), new Vec3(0, -3, 0), scale, penetration);
        success(src, "commands.mystcraft.meteor.success", (int) at.x, (int) at.z);
        return 1;
    }

    // --- /myst permissions <player> <restrict|permit> <entry|depart> <all|dimension> -----------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> permissions() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("permissions");
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
        LinkPermissions permissions = LinkPermissions.get(ctx.getSource().getServer());
        if (entry) {
            if (permit) permissions.permitEntry(playerName, dim); else permissions.restrictEntry(playerName, dim);
        } else {
            if (permit) permissions.permitDepart(playerName, dim); else permissions.restrictDepart(playerName, dim);
        }
        success(ctx.getSource(), "commands.mystcraft.permissions.success", playerName, permit ? "permit" : "restrict",
                entry ? "entry" : "depart", dim == null ? "all" : dim.identifier().toString());
        return 1;
    }

    // --- /myst retire [dimension] --------------------------------------------------------------------------------

    /** Marks an Age dead: links into it are refused and its data can be recycled (what a collapsed Age does on its own). */
    private static LiteralArgumentBuilder<CommandSourceStack> retire() {
        return Commands.literal("retire")
                .executes(ctx -> retireAge(ctx, senderAgeLevel(ctx.getSource())))
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(ctx -> retireAge(ctx, ageLevel(ctx, "dimension"))));
    }

    private static int retireAge(CommandContext<CommandSourceStack> ctx, ServerLevel level) throws CommandSyntaxException {
        if (!AgeManager.markDead(ctx.getSource().getServer(), level.dimension())) throw NOT_AN_AGE.create();
        success(ctx.getSource(), "commands.mystcraft.retire.success", dimName(level));
        return 1;
    }

    // --- /myst-dev scene [closeup|open|use <element>] ------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> scene() {
        LiteralArgumentBuilder<CommandSourceStack> closeup = Commands.literal("closeup");
        LiteralArgumentBuilder<CommandSourceStack> open = Commands.literal("open");
        for (DebugScene.Element element : DebugScene.Element.values()) {
            String name = element.name().toLowerCase(Locale.ROOT);
            closeup.then(Commands.literal(name).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (!DebugScene.closeup(serverLevel(ctx, player), player, element)) {
                    ctx.getSource().sendFailure(Component.translatable("commands.mystcraft.scene.missing"));
                    return 0;
                }
                return 1;
            }));
            open.then(Commands.literal(name).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (!DebugScene.open(serverLevel(ctx, player), player, element)) {
                    ctx.getSource().sendFailure(Component.translatable("commands.mystcraft.scene.missing"));
                    return 0;
                }
                return 1;
            }));
        }
        LiteralArgumentBuilder<CommandSourceStack> use = Commands.literal("use");
        for (DebugScene.UsableItem item : DebugScene.UsableItem.values()) {
            use.then(Commands.literal(item.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return DebugScene.use(serverLevel(ctx, player), player, item) ? 1 : 0;
            }));
        }
        return Commands.literal("scene")
                .then(closeup)
                .then(open)
                .then(use)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    BlockPos origin = player.blockPosition().offset(-DebugScene.WIDTH / 2, 0, -3);
                    BlockPos view = DebugScene.build(serverLevel(ctx, player), origin, player);
                    success(ctx.getSource(), "commands.mystcraft.scene.built", view.toShortString());
                    return 1;
                });
    }

    // --- /myst-dev qa-shelf --------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> qaShelf() {
        return Commands.literal("qa-shelf").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            int placed = QaShelf.build(serverLevel(ctx, player), player);
            success(ctx.getSource(), "commands.mystcraft.qa_shelf.built", placed);
            return placed;
        });
    }

    // --- /myst-dev qa-visit <id> ---------------------------------------------------------------------------------

    /** Binds the QA case {@code id} (fixed seed and pages) and links the player in; what the client smoke tour drives. */
    private static LiteralArgumentBuilder<CommandSourceStack> qaVisit() {
        return Commands.literal("qa-visit").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String id = StringArgumentType.getString(ctx, "id");
            QaShelf.Case qa = QaShelf.cases().stream().filter(c -> c.id().equalsIgnoreCase(id)).findFirst().orElse(null);
            if (qa == null) {
                ctx.getSource().sendFailure(Component.translatable("commands.mystcraft.qa_visit.unknown", id));
                return 0;
            }
            ItemStack book = QaShelf.bind(ctx.getSource().getServer(), qa);
            LinkInfo info = LinkingItem.getLinkInfo(book);
            if (!LinkController.travelEntity(player, info)) {
                ctx.getSource().sendFailure(Component.translatable("commands.mystcraft.link.refused"));
                return 0;
            }
            success(ctx.getSource(), "commands.mystcraft.qa_visit.success", qa.id(), qa.title(), qa.lookFor());
            return 1;
        }));
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    private static ServerLevel serverLevel(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        return player.level() instanceof ServerLevel sl ? sl : ctx.getSource().getLevel();
    }

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
}

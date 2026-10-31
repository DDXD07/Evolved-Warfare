package com.evolvedwarfare.command;

import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.faction.Reputation;
import com.evolvedwarfare.world.FactionRaids;
import com.evolvedwarfare.world.Hordes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import com.evolvedwarfare.config.EwConfig;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Comandos de administración (nivel 2):
 * <pre>
 * /evolvedwarfare reputation &lt;jugadores&gt;                          ver la reputación
 * /evolvedwarfare reputation &lt;jugadores&gt; &lt;facción&gt; set|add &lt;n&gt;  cambiarla
 * /evolvedwarfare raid &lt;facción&gt; [siege]                           lanzar un asalto aquí (con máquinas de asedio)
 * /evolvedwarfare horde [&lt;facción&gt; [&lt;semana&gt;]]                      lanzar ya una horda contra tu base (o contra ti)
 * /evolvedwarfare config [&lt;opción&gt; [&lt;valor&gt;] | reload | reset]       ver o cambiar la configuración
 * </pre>
 */
public final class ModCommands {
	private static final SimpleCommandExceptionType UNKNOWN_FACTION =
			new SimpleCommandExceptionType(Text.translatable("commands.evolvedwarfare.unknown_faction"));
	private static final SimpleCommandExceptionType RAID_FAILED =
			new SimpleCommandExceptionType(Text.translatable("commands.evolvedwarfare.raid.failed"));
	private static final SimpleCommandExceptionType HORDE_FAILED =
			new SimpleCommandExceptionType(Text.translatable("commands.evolvedwarfare.horde.failed"));

	private ModCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("evolvedwarfare")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("reputation")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.executes(ModCommands::show)
								.then(CommandManager.argument("faction", StringArgumentType.word())
										.suggests((context, builder) -> CommandSource.suggestMatching(
												java.util.Arrays.stream(Faction.values()).map(Faction::id), builder))
										.then(CommandManager.literal("set")
												.then(CommandManager.argument("value", IntegerArgumentType.integer(Reputation.MIN, Reputation.MAX))
														.executes(context -> modify(context, true))))
										.then(CommandManager.literal("add")
												.then(CommandManager.argument("value", IntegerArgumentType.integer(-200, 200))
														.executes(context -> modify(context, false)))))))
				.then(CommandManager.literal("raid")
						.then(CommandManager.argument("faction", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(
										java.util.Arrays.stream(Faction.values()).map(Faction::id), builder))
								.executes(context -> raid(context, false))
								.then(CommandManager.literal("siege")
										.executes(context -> raid(context, true)))))
				.then(CommandManager.literal("horde")
						.executes(context -> horde(context, null, -1))
						.then(CommandManager.argument("faction", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(
										java.util.Arrays.stream(Faction.values()).map(Faction::id), builder))
								.executes(context -> horde(context, faction(context), -1))
								.then(CommandManager.argument("week", IntegerArgumentType.integer(1, 100))
										.executes(context -> horde(context, faction(context), IntegerArgumentType.getInteger(context, "week"))))))
				.then(CommandManager.literal("config")
						.executes(ModCommands::configList)
						.then(CommandManager.literal("reload").executes(context -> {
							EwConfig.load();
							context.getSource().sendFeedback(() -> Text.translatable("commands.evolvedwarfare.config.reloaded"), true);
							return 1;
						}))
						.then(CommandManager.literal("reset").executes(context -> {
							EwConfig.reset();
							context.getSource().sendFeedback(() -> Text.translatable("commands.evolvedwarfare.config.reset"), true);
							return 1;
						}))
						.then(CommandManager.argument("option", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(
										EwConfig.OPTIONS.stream().map(EwConfig.Option::key), builder))
								.executes(ModCommands::configShow)
								.then(CommandManager.argument("value", StringArgumentType.word())
										.executes(ModCommands::configSet)))));
		SkillCommands.register(dispatcher);
	}

	private static final SimpleCommandExceptionType UNKNOWN_OPTION =
			new SimpleCommandExceptionType(Text.translatable("commands.evolvedwarfare.config.unknown"));
	private static final SimpleCommandExceptionType BAD_VALUE =
			new SimpleCommandExceptionType(Text.translatable("commands.evolvedwarfare.config.bad_value"));

	private static MutableText optionLine(EwConfig.Option option) {
		EwConfig config = EwConfig.get();
		return Text.literal(option.key()).formatted(net.minecraft.util.Formatting.YELLOW)
				.append(Text.literal(" = " + config.display(option)).formatted(net.minecraft.util.Formatting.WHITE))
				.append(Text.literal("  ").append(Text.translatable(option.translationKey())).formatted(net.minecraft.util.Formatting.GRAY));
	}

	private static int configList(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		for (String category : EwConfig.categories()) {
			source.sendFeedback(() -> Text.translatable("config.evolvedwarfare.category." + category)
					.formatted(net.minecraft.util.Formatting.GOLD, net.minecraft.util.Formatting.BOLD), false);
			for (EwConfig.Option option : EwConfig.OPTIONS) {
				if (option.category().equals(category)) {
					source.sendFeedback(() -> optionLine(option), false);
				}
			}
		}
		return EwConfig.OPTIONS.size();
	}

	private static EwConfig.Option option(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		EwConfig.Option option = EwConfig.option(StringArgumentType.getString(context, "option"));
		if (option == null) {
			throw UNKNOWN_OPTION.create();
		}
		return option;
	}

	private static int configShow(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		EwConfig.Option option = option(context);
		context.getSource().sendFeedback(() -> optionLine(option), false);
		return 1;
	}

	private static int configSet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		EwConfig.Option option = option(context);
		Double value = EwConfig.parse(option, StringArgumentType.getString(context, "value"));
		if (value == null) {
			throw BAD_VALUE.create();
		}
		EwConfig.get().set(option, value);
		EwConfig.save();
		context.getSource().sendFeedback(() -> Text.translatable("commands.evolvedwarfare.config.set").append(" ").append(optionLine(option)), true);
		return 1;
	}

	private static Faction faction(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Faction faction = Faction.byId(StringArgumentType.getString(context, "faction"));
		if (faction == null) {
			throw UNKNOWN_FACTION.create();
		}
		return faction;
	}

	private static int show(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		ServerCommandSource source = context.getSource();
		for (ServerPlayerEntity player : players) {
			MutableText line = Text.empty().append(player.getDisplayName()).append(":");
			for (Faction faction : Faction.values()) {
				int rep = Reputation.get(source.getServer(), player.getUuid(), faction);
				var standing = Reputation.standing(source.getServer(), player.getUuid(), faction);
				line.append(" ").append(faction.displayName()).append(" " + rep + " (")
						.append(Text.translatable(standing.translationKey()).formatted(standing.color())).append(")");
			}
			source.sendFeedback(() -> line, false);
		}
		return players.size();
	}

	private static int modify(CommandContext<ServerCommandSource> context, boolean set) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		Faction faction = faction(context);
		int value = IntegerArgumentType.getInteger(context, "value");
		ServerCommandSource source = context.getSource();
		for (ServerPlayerEntity player : players) {
			if (set) {
				Reputation.set(source.getServer(), player.getUuid(), faction, value);
			} else {
				Reputation.change(source.getServer(), player.getUuid(), faction, value);
			}
		}
		int count = players.size();
		source.sendFeedback(() -> Text.translatable("commands.evolvedwarfare.reputation.changed", faction.displayName(), count), true);
		return count;
	}

	/** Una horda ya: contra la base de quien lo manda (o contra él, si no está en ella), de la semana de hoy o la que se diga. */
	private static int horde(CommandContext<ServerCommandSource> context, @org.jetbrains.annotations.Nullable Faction faction, int week)
			throws CommandSyntaxException {
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getWorld();
		if (world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) {
			throw HORDE_FAILED.create();
		}
		net.minecraft.util.math.random.Random random = world.getRandom();
		Faction chosen = faction != null ? faction : Faction.values()[random.nextInt(Faction.values().length)];
		int every = Math.max(1, EwConfig.get().hordeEveryDays);
		int chosenWeek = week > 0 ? week : Hordes.week(Hordes.day(world.getTimeOfDay()), every);
		ServerPlayerEntity player = source.getPlayer();
		BlockPos home = player != null ? Hordes.home(world, player) : null;
		BlockPos center = home != null ? home : BlockPos.ofFloored(source.getPosition());
		java.util.UUID hunted = home == null && player != null ? player.getUuid() : null;
		java.util.List<java.util.UUID> defenders = player != null ? java.util.List.of(player.getUuid()) : java.util.List.of();
		Hordes.Horde horde = Hordes.start(world, random, chosen, center, hunted, chosenWeek, defenders, Hordes.EDGE);
		if (horde == null) {
			horde = Hordes.start(world, random, chosen, center, hunted, chosenWeek, defenders, Hordes.EDGE / 2);
		}
		if (horde == null) {
			throw HORDE_FAILED.create();
		}
		source.sendFeedback(() -> Text.translatable("commands.evolvedwarfare.horde.started", chosen.displayName(), chosenWeek), true);
		return 1;
	}

	private static int raid(CommandContext<ServerCommandSource> context, boolean siege) throws CommandSyntaxException {
		Faction faction = faction(context);
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getWorld();
		BlockPos center = BlockPos.ofFloored(source.getPosition());
		if (!FactionRaids.tryStart(world, world.getRandom(), faction, siege) && !FactionRaids.start(world, world.getRandom(), faction, center, 48,
				siege ? FactionRaids.SIEGE_FORCED : FactionRaids.SIEGE_NONE)) {
			throw RAID_FAILED.create();
		}
		source.sendFeedback(() -> Text.translatable("commands.evolvedwarfare.raid.started", faction.displayName()), true);
		return 1;
	}
}

package com.evolvedwarfare.command;

import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.faction.Reputation;
import com.evolvedwarfare.world.FactionRaids;
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
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Comandos de administración (nivel 2):
 * <pre>
 * /evolvedwarfare reputation &lt;jugadores&gt;                          ver la reputación
 * /evolvedwarfare reputation &lt;jugadores&gt; &lt;facción&gt; set|add &lt;n&gt;  cambiarla
 * /evolvedwarfare raid &lt;facción&gt;                                   lanzar un asalto aquí
 * </pre>
 */
public final class ModCommands {
	private static final SimpleCommandExceptionType UNKNOWN_FACTION =
			new SimpleCommandExceptionType(Text.translatable("commands.evolvedwarfare.unknown_faction"));
	private static final SimpleCommandExceptionType RAID_FAILED =
			new SimpleCommandExceptionType(Text.translatable("commands.evolvedwarfare.raid.failed"));

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
								.executes(ModCommands::raid))));
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

	private static int raid(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Faction faction = faction(context);
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getWorld();
		BlockPos center = BlockPos.ofFloored(source.getPosition());
		if (!FactionRaids.tryStart(world, world.getRandom(), faction) && !FactionRaids.start(world, world.getRandom(), faction, center, 40)) {
			throw RAID_FAILED.create();
		}
		source.sendFeedback(() -> Text.translatable("commands.evolvedwarfare.raid.started", faction.displayName()), true);
		return 1;
	}
}

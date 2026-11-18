package com.evolvedwarfare.command;

import com.evolvedwarfare.skill.Engram;
import com.evolvedwarfare.skill.Engrams;
import com.evolvedwarfare.skill.SkillData;
import com.evolvedwarfare.skill.SkillState;
import com.evolvedwarfare.skill.Skills;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.stream.Stream;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * <pre>
 * /evolvedwarfare engrams &lt;jugadores&gt; learn &lt;engrama|all&gt;   aprenderlo (sin pedir nada)
 * /evolvedwarfare engrams &lt;jugadores&gt; reset                 olvidarlos todos y devolver los puntos
 * /evolvedwarfare engrams &lt;jugadores&gt; points &lt;n&gt;            dar (o quitar) puntos de engrama
 * /evolvedwarfare engrams &lt;jugadores&gt; xp &lt;n&gt;                dar experiencia de superviviente
 * /evolvedwarfare engrams &lt;jugadores&gt; attributes reset      devolver los puntos de atributo
 * /evolvedwarfare engrams &lt;jugadores&gt; attributes points &lt;n&gt; dar (o quitar) puntos de atributo
 * </pre>
 */
final class EngramCommands {
	private EngramCommands() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("evolvedwarfare")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("engrams")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.then(CommandManager.literal("learn")
										.then(CommandManager.argument("engram", StringArgumentType.word())
												.suggests((context, builder) -> CommandSource.suggestMatching(
														Stream.concat(Stream.of("all"), Engrams.all().stream().map(e -> e.id)), builder))
												.executes(EngramCommands::learn)))
								.then(CommandManager.literal("reset").executes(EngramCommands::reset))
								.then(CommandManager.literal("points")
										.then(CommandManager.argument("amount", IntegerArgumentType.integer(-5000, 5000))
												.executes(EngramCommands::points)))
								.then(CommandManager.literal("attributes")
										.then(CommandManager.literal("reset").executes(EngramCommands::attributesReset))
										.then(CommandManager.literal("points")
												.then(CommandManager.argument("amount", IntegerArgumentType.integer(-1000, 1000))
														.executes(EngramCommands::attributePoints))))
								.then(CommandManager.literal("xp")
										.then(CommandManager.argument("amount", FloatArgumentType.floatArg(0.0F, 1_000_000.0F))
												.executes(EngramCommands::xp))))));
	}

	private static int learn(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		String id = StringArgumentType.getString(context, "engram");
		int count = 0;
		for (ServerPlayerEntity player : players) {
			for (Engram e : Engrams.all()) {
				if ("all".equals(id) || e.id.equals(id)) {
					Engrams.grant(player, e.id);
					count++;
				}
			}
		}
		int n = count;
		context.getSource().sendFeedback(() -> Text.translatable("commands.evolvedwarfare.engrams.learned", n, players.size()), true);
		return count;
	}

	private static int reset(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		for (ServerPlayerEntity player : players) {
			Engrams.reset(player);
		}
		context.getSource().sendFeedback(() -> Text.translatable("commands.evolvedwarfare.engrams.reset", players.size()), true);
		return players.size();
	}

	private static int points(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		int amount = IntegerArgumentType.getInteger(context, "amount");
		for (ServerPlayerEntity player : players) {
			SkillState state = SkillState.get(player.getServer());
			SkillData data = state.of(player.getUuid());
			data.engramPoints = Math.max(0, data.engramPoints + amount);
			state.markDirty();
			Skills.sync(player, false);
		}
		context.getSource().sendFeedback(() -> Text.literal(amount + " → " + players.size()), true);
		return players.size();
	}

	private static int xp(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		float amount = FloatArgumentType.getFloat(context, "amount");
		for (ServerPlayerEntity player : players) {
			Skills.survivorXp(player, amount);
		}
		context.getSource().sendFeedback(() -> Text.literal("+" + amount + " XP → " + players.size()), true);
		return players.size();
	}

	private static int attributesReset(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		for (ServerPlayerEntity player : players) {
			com.evolvedwarfare.skill.SurvivorAttributes.reset(player);
		}
		context.getSource().sendFeedback(() -> Text.translatable("commands.evolvedwarfare.attributes.reset", players.size()), true);
		return players.size();
	}

	private static int attributePoints(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		int amount = IntegerArgumentType.getInteger(context, "amount");
		for (ServerPlayerEntity player : players) {
			SkillState state = SkillState.get(player.getServer());
			SkillData data = state.of(player.getUuid());
			data.attributePoints = Math.max(0, data.attributePoints + amount);
			state.markDirty();
			Skills.sync(player, false);
		}
		context.getSource().sendFeedback(() -> Text.literal(amount + " → " + players.size()), true);
		return players.size();
	}
}

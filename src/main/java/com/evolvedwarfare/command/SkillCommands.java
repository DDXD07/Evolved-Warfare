package com.evolvedwarfare.command;

import com.evolvedwarfare.skill.Perk;
import com.evolvedwarfare.skill.Skill;
import com.evolvedwarfare.skill.SkillData;
import com.evolvedwarfare.skill.Skills;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * <pre>
 * /evolvedwarfare skills &lt;jugadores&gt;                                ver niveles, puntos y ventajas
 * /evolvedwarfare skills &lt;jugadores&gt; points &lt;n&gt;                     dar (o quitar) puntos de ventaja
 * /evolvedwarfare skills &lt;jugadores&gt; level &lt;habilidad&gt; &lt;nivel&gt;       poner una habilidad en un nivel
 * /evolvedwarfare skills &lt;jugadores&gt; perk &lt;ventaja|all&gt; [rango]       dar una ventaja (sin pedir nada)
 * /evolvedwarfare skills &lt;jugadores&gt; reset                          quitar las ventajas y devolver los puntos
 * </pre>
 */
final class SkillCommands {
	private static final SimpleCommandExceptionType UNKNOWN_SKILL =
			new SimpleCommandExceptionType(Text.translatable("commands.evolvedwarfare.skills.unknown_skill"));
	private static final SimpleCommandExceptionType UNKNOWN_PERK =
			new SimpleCommandExceptionType(Text.translatable("commands.evolvedwarfare.skills.unknown_perk"));

	private SkillCommands() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("evolvedwarfare")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("skills")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.executes(SkillCommands::show)
								.then(CommandManager.literal("points")
										.then(CommandManager.argument("amount", IntegerArgumentType.integer(-1000, 1000))
												.executes(SkillCommands::points)))
								.then(CommandManager.literal("level")
										.then(CommandManager.argument("skill", StringArgumentType.word())
												.suggests((context, builder) -> CommandSource.suggestMatching(Stream.of(Skill.values()).map(Skill::id),
														builder))
												.then(CommandManager.argument("level", IntegerArgumentType.integer(0, Skill.MAX_LEVEL))
														.executes(SkillCommands::level))))
								.then(CommandManager.literal("perk")
										.then(CommandManager.argument("perk", StringArgumentType.word())
												.suggests((context, builder) -> CommandSource.suggestMatching(
														Stream.concat(Stream.of("all"), Stream.of(Perk.values()).map(Perk::id)), builder))
												.executes(context -> perk(context, -1))
												.then(CommandManager.argument("rank", IntegerArgumentType.integer(0, 5))
														.executes(context -> perk(context, IntegerArgumentType.getInteger(context, "rank"))))))
								.then(CommandManager.literal("reset")
										.executes(SkillCommands::reset)))));
	}

	private static int show(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		ServerCommandSource source = context.getSource();
		for (ServerPlayerEntity player : players) {
			SkillData data = Skills.get(source.getServer(), player.getUuid());
			MutableText line = Text.empty().append(player.getDisplayName()).append(" ")
					.append(Text.translatable("commands.evolvedwarfare.skills.summary", data.level, data.points, data.spentPoints())
							.formatted(Formatting.GOLD));
			for (Skill skill : Skill.values()) {
				line.append("\n  ").append(Text.translatable(skill.translationKey())).append(": " + data.level(skill));
			}
			source.sendFeedback(() -> line, false);
		}
		return players.size();
	}

	private static int points(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		int amount = IntegerArgumentType.getInteger(context, "amount");
		for (ServerPlayerEntity player : players) {
			Skills.addPoints(player, amount);
		}
		context.getSource().sendFeedback(() -> Text.translatable("commands.evolvedwarfare.skills.points", amount, players.size()), true);
		return players.size();
	}

	private static int level(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		Skill skill = Skill.byId(StringArgumentType.getString(context, "skill"));
		if (skill == null) {
			throw UNKNOWN_SKILL.create();
		}
		int level = IntegerArgumentType.getInteger(context, "level");
		for (ServerPlayerEntity player : players) {
			Skills.setLevel(player, skill, level);
		}
		context.getSource().sendFeedback(() -> Text.translatable("commands.evolvedwarfare.skills.level", Text.translatable(skill.translationKey()),
				level, players.size()), true);
		return players.size();
	}

	private static int perk(CommandContext<ServerCommandSource> context, int rank) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		String name = StringArgumentType.getString(context, "perk");
		List<Perk> perks;
		if (name.equals("all")) {
			perks = List.of(Perk.values());
		} else {
			Perk perk = Perk.byId(name);
			if (perk == null) {
				throw UNKNOWN_PERK.create();
			}
			perks = List.of(perk);
		}
		for (ServerPlayerEntity player : players) {
			for (Perk perk : perks) {
				Skills.grant(player, perk, rank < 0 ? perk.ranks() : rank);
			}
		}
		context.getSource().sendFeedback(() -> Text.translatable("commands.evolvedwarfare.skills.perk", perks.size(), players.size()), true);
		return players.size();
	}

	private static int reset(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		for (ServerPlayerEntity player : players) {
			Skills.reset(player);
		}
		context.getSource().sendFeedback(() -> Text.translatable("commands.evolvedwarfare.skills.reset", players.size()), true);
		return players.size();
	}
}

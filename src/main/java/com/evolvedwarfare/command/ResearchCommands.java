package com.evolvedwarfare.command;

import com.evolvedwarfare.research.Research;
import com.evolvedwarfare.research.ResearchState;
import com.evolvedwarfare.research.Tech;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
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
 * /evolvedwarfare research &lt;jugadores&gt;                          ver lo investigado
 * /evolvedwarfare research &lt;jugadores&gt; grant|revoke &lt;técnica|all&gt;  dar o quitar técnicas
 * </pre>
 */
final class ResearchCommands {
	private static final SimpleCommandExceptionType UNKNOWN_TECH =
			new SimpleCommandExceptionType(Text.translatable("commands.evolvedwarfare.research.unknown"));

	private ResearchCommands() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("evolvedwarfare")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("research")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.executes(ResearchCommands::show)
								.then(CommandManager.literal("grant")
										.then(CommandManager.argument("tech", StringArgumentType.word())
												.suggests((context, builder) -> CommandSource.suggestMatching(techNames(), builder))
												.executes(context -> change(context, true))))
								.then(CommandManager.literal("revoke")
										.then(CommandManager.argument("tech", StringArgumentType.word())
												.suggests((context, builder) -> CommandSource.suggestMatching(techNames(), builder))
												.executes(context -> change(context, false)))))));
	}

	private static Stream<String> techNames() {
		return Stream.concat(Stream.of("all"), Stream.of(Tech.values()).map(Tech::id));
	}

	private static List<Tech> techs(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		String name = StringArgumentType.getString(context, "tech");
		if (name.equals("all")) {
			return List.of(Tech.values());
		}
		Tech tech = Tech.byId(name);
		if (tech == null) {
			throw UNKNOWN_TECH.create();
		}
		return List.of(tech);
	}

	private static int show(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		ServerCommandSource source = context.getSource();
		for (ServerPlayerEntity player : players) {
			ResearchState.Entry entry = ResearchState.get(source.getServer()).of(player.getUuid());
			MutableText line = Text.empty().append(player.getDisplayName()).append(": ");
			List<Text> parts = new ArrayList<>();
			for (Tech tech : entry.done) {
				parts.add(Text.translatable(tech.translationKey()).formatted(Formatting.GREEN));
			}
			if (entry.current != null) {
				parts.add(Text.translatable(entry.current.translationKey()).formatted(Formatting.YELLOW)
						.append(String.format(" (%d%%)", (int) (100 * entry.progress / entry.current.ticks()))));
			}
			for (int i = 0; i < parts.size(); i++) {
				if (i > 0) {
					line.append(", ");
				}
				line.append(parts.get(i));
			}
			if (parts.isEmpty()) {
				line.append(Text.translatable("commands.evolvedwarfare.research.nothing").formatted(Formatting.GRAY));
			}
			source.sendFeedback(() -> line, false);
		}
		return players.size();
	}

	private static int change(CommandContext<ServerCommandSource> context, boolean grant) throws CommandSyntaxException {
		Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "targets");
		List<Tech> techs = techs(context);
		ServerCommandSource source = context.getSource();
		for (ServerPlayerEntity player : players) {
			for (Tech tech : techs) {
				if (grant) {
					Research.grant(source.getServer(), player.getUuid(), tech);
				} else {
					Research.revoke(source.getServer(), player.getUuid(), tech);
				}
			}
		}
		int count = players.size();
		source.sendFeedback(() -> Text.translatable(grant ? "commands.evolvedwarfare.research.granted" : "commands.evolvedwarfare.research.revoked",
				techs.size(), count), true);
		return count;
	}
}

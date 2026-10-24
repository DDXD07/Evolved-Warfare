package com.evolvedwarfare.faction;

import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.NpcRole;
import com.evolvedwarfare.network.DiplomacyNetworking;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Reputación de cada jugador con cada facción, de -100 a 100.
 * <ul>
 *   <li>Por debajo de {@value #HOSTILE_BELOW}: <b>hostil</b> (te atacan y asaltan tu pueblo), salvo con tregua.</li>
 *   <li>De {@value #HOSTILE_BELOW} a {@value #FRIENDLY_AT}: <b>neutral</b>.</li>
 *   <li>Desde {@value #FRIENDLY_AT}: <b>amistosa</b> (mercenarios). Desde {@value #ALLIED_AT} se puede firmar una
 *       <b>alianza</b>, que se rompe si baja de {@value #ALLIANCE_BREAKS_BELOW}.</li>
 * </ul>
 * Matar a uno de los suyos la baja (y sube un poco con sus enemigos); atacarles la baja algo y les enfada un minuto.
 * Los tributos la suben (ver la pantalla de diplomacia).
 */
public final class Reputation {
	public static final int MIN = -100;
	public static final int MAX = 100;
	public static final int HOSTILE_BELOW = -20;
	public static final int FRIENDLY_AT = 20;
	public static final int ALLIED_AT = 60;
	public static final int ALLIANCE_BREAKS_BELOW = 40;
	/** Tiempo que te tienen ganas después de atacarles. */
	public static final long PROVOKED_TICKS = 1200;

	private Reputation() {
	}

	private static long now(MinecraftServer server) {
		return server.getOverworld().getTime();
	}

	@Nullable
	private static MinecraftServer server(@Nullable World world) {
		return world == null || world.isClient ? null : world.getServer();
	}

	// ------------------------------------------------------------------ consultas

	public static int get(MinecraftServer server, UUID player, Faction faction) {
		return ReputationState.get(server).record(player).reputation[faction.ordinal()];
	}

	public static Standing standing(MinecraftServer server, UUID player, Faction faction) {
		ReputationState.PlayerRecord record = ReputationState.get(server).record(player);
		int i = faction.ordinal();
		if (record.allied[i]) {
			return Standing.ALLIED;
		}
		int rep = record.reputation[i];
		if (rep < HOSTILE_BELOW) {
			return record.truceUntil[i] > now(server) ? Standing.TRUCE : Standing.HOSTILE;
		}
		return rep >= FRIENDLY_AT ? Standing.FRIENDLY : Standing.NEUTRAL;
	}

	/** ¿Atacan a este jugador (o a los NPCs de este dueño)? Enemigos o recién atacados por él. */
	public static boolean hostile(@Nullable World world, @Nullable UUID player, Faction faction) {
		MinecraftServer server = server(world);
		if (server == null || player == null) {
			return false;
		}
		return standing(server, player, faction) == Standing.HOSTILE || provoked(server, player, faction);
	}

	public static boolean provoked(MinecraftServer server, UUID player, Faction faction) {
		return ReputationState.get(server).record(player).provokedUntil[faction.ordinal()] > now(server);
	}

	public static boolean allied(@Nullable World world, @Nullable UUID player, Faction faction) {
		MinecraftServer server = server(world);
		return server != null && player != null && standing(server, player, faction) == Standing.ALLIED;
	}

	public static long truceUntil(MinecraftServer server, UUID player, Faction faction) {
		return ReputationState.get(server).record(player).truceUntil[faction.ordinal()];
	}

	public static long aidReadyAt(MinecraftServer server, UUID player, Faction faction) {
		return ReputationState.get(server).record(player).aidReadyAt[faction.ordinal()];
	}

	// ------------------------------------------------------------------ cambios

	/** Sube o baja la reputación y avisa al jugador si cambia el trato. */
	public static void change(MinecraftServer server, UUID player, Faction faction, int delta) {
		if (delta == 0) {
			return;
		}
		ReputationState state = ReputationState.get(server);
		ReputationState.PlayerRecord record = state.record(player);
		int i = faction.ordinal();
		Standing before = standing(server, player, faction);
		int old = record.reputation[i];
		record.reputation[i] = MathHelper.clamp(old + delta, MIN, faction.maxReputation());
		if (record.allied[i] && record.reputation[i] < ALLIANCE_BREAKS_BELOW) {
			record.allied[i] = false;
			tell(server, player, Text.translatable("message.evolvedwarfare.diplomacy.alliance_broken", faction.displayName())
					.formatted(Formatting.RED), false);
		}
		state.markDirty();
		Standing after = standing(server, player, faction);
		if (record.reputation[i] != old) {
			String key = delta > 0 ? "message.evolvedwarfare.reputation.up" : "message.evolvedwarfare.reputation.down";
			tell(server, player, Text.translatable(key, faction.displayName(), record.reputation[i], (delta > 0 ? "+" : "") + delta), true);
		}
		if (after != before) {
			announce(server, player, faction, after);
		}
		DiplomacyNetworking.sendUpdate(server, player);
	}

	/** Pone la reputación a un valor (comandos). */
	public static void set(MinecraftServer server, UUID player, Faction faction, int value) {
		change(server, player, faction, MathHelper.clamp(value, MIN, faction.maxReputation()) - get(server, player, faction));
	}

	/** Le has atacado: un minuto de enfado (y adiós a la tregua). */
	public static void provoke(MinecraftServer server, UUID player, Faction faction) {
		ReputationState state = ReputationState.get(server);
		ReputationState.PlayerRecord record = state.record(player);
		int i = faction.ordinal();
		long now = now(server);
		if (record.truceUntil[i] > now) {
			record.truceUntil[i] = 0L;
			state.markDirty();
			tell(server, player, Text.translatable("message.evolvedwarfare.diplomacy.truce_broken", faction.displayName())
					.formatted(Formatting.RED), false);
			DiplomacyNetworking.sendUpdate(server, player);
		}
		record.provokedUntil[i] = now + PROVOKED_TICKS;
	}

	public static void setTruce(MinecraftServer server, UUID player, Faction faction, long until) {
		ReputationState state = ReputationState.get(server);
		state.record(player).truceUntil[faction.ordinal()] = until;
		state.record(player).provokedUntil[faction.ordinal()] = 0L;
		state.markDirty();
		DiplomacyNetworking.sendUpdate(server, player);
	}

	public static void setAllied(MinecraftServer server, UUID player, Faction faction, boolean allied) {
		ReputationState state = ReputationState.get(server);
		Standing before = standing(server, player, faction);
		state.record(player).allied[faction.ordinal()] = allied;
		state.markDirty();
		Standing after = standing(server, player, faction);
		if (after != before) {
			announce(server, player, faction, after);
		}
		DiplomacyNetworking.sendUpdate(server, player);
	}

	public static void setAidReadyAt(MinecraftServer server, UUID player, Faction faction, long time) {
		ReputationState state = ReputationState.get(server);
		state.record(player).aidReadyAt[faction.ordinal()] = time;
		state.markDirty();
		DiplomacyNetworking.sendUpdate(server, player);
	}

	// ------------------------------------------------------------------ sucesos

	/** Quién responde de lo que hace una entidad: el jugador, o el dueño del NPC o de la mascota. */
	@Nullable
	public static UUID responsiblePlayer(@Nullable Entity entity) {
		if (entity instanceof PlayerEntity player) {
			return player.getUuid();
		}
		if (entity instanceof AbstractNpcEntity npc) {
			return npc.getOwnerUuid();
		}
		if (entity instanceof Tameable tameable) {
			return tameable.getOwnerUuid();
		}
		return null;
	}

	/** Alguien de una facción ha muerto a manos de un jugador o de sus NPCs. */
	public static void onMemberKilled(FactionNpcEntity victim, @Nullable Entity killer) {
		MinecraftServer server = server(victim.getWorld());
		UUID player = responsiblePlayer(killer);
		if (server == null || player == null) {
			return;
		}
		Faction faction = victim.getFaction();
		boolean captain = victim.getRole() == NpcRole.CAPTAIN;
		boolean byPlayer = killer instanceof PlayerEntity;
		int loss = captain ? (byPlayer ? 12 : 8) : (byPlayer ? 4 : 2);
		// a los escoltas que te prestan los aliados no se les mata sin consecuencias
		if (victim.isEscortOf(player)) {
			loss *= 3;
		}
		change(server, player, faction, -loss);
		for (Faction other : Faction.values()) {
			if (other.enemies(faction)) {
				change(server, player, other, captain ? 3 : 1);
			}
		}
	}

	/** Un jugador (o sus NPCs) ha atacado a alguien de una facción. */
	public static void onMemberAttacked(FactionNpcEntity victim, @Nullable Entity attacker) {
		MinecraftServer server = server(victim.getWorld());
		UUID player = responsiblePlayer(attacker);
		if (server == null || player == null) {
			return;
		}
		Faction faction = victim.getFaction();
		boolean alreadyAngry = provoked(server, player, faction);
		Standing standing = standing(server, player, faction);
		provoke(server, player, faction);
		if (!alreadyAngry && standing != Standing.HOSTILE) {
			change(server, player, faction, -2);
		}
	}

	// ------------------------------------------------------------------ avisos

	private static void announce(MinecraftServer server, UUID player, Faction faction, Standing standing) {
		Text message = Text.translatable("message.evolvedwarfare.reputation.standing", faction.displayName(),
				Text.translatable(standing.translationKey()).formatted(standing.color(), Formatting.BOLD));
		tell(server, player, message, false);
	}

	private static void tell(MinecraftServer server, UUID player, Text message, boolean actionBar) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
		if (online != null) {
			online.sendMessage(message, actionBar);
		}
	}
}

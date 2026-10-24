package com.evolvedwarfare.network;

import com.evolvedwarfare.faction.Diplomacy;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.faction.Reputation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Manda al cliente la reputación (al entrar, al cambiar y al abrir la diplomacia) y recibe los botones. */
public final class DiplomacyNetworking {
	private DiplomacyNetworking() {
	}

	static void register() {
		ServerPlayNetworking.registerGlobalReceiver(DiplomacyActionPayload.ID, (payload, context) -> {
			Faction[] factions = Faction.values();
			if (payload.faction() >= 0 && payload.faction() < factions.length) {
				Diplomacy.perform(context.player(), factions[payload.faction()], payload.action());
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer(), false));
	}

	private static List<DiplomacyDataPayload.Status> statuses(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		long now = server.getOverworld().getTime();
		UUID id = player.getUuid();
		List<DiplomacyDataPayload.Status> list = new ArrayList<>();
		for (Faction faction : Faction.values()) {
			long truce = Math.max(0L, Reputation.truceUntil(server, id, faction) - now);
			long aid = Math.max(0L, Reputation.aidReadyAt(server, id, faction) - now);
			list.add(new DiplomacyDataPayload.Status(Reputation.get(server, id, faction),
					Reputation.standing(server, id, faction).ordinal(), (int) Math.min(truce, Integer.MAX_VALUE),
					(int) Math.min(aid, Integer.MAX_VALUE)));
		}
		return list;
	}

	private static void send(ServerPlayerEntity player, boolean open) {
		if (player.getServer() != null && ServerPlayNetworking.canSend(player, DiplomacyDataPayload.ID)) {
			ServerPlayNetworking.send(player, new DiplomacyDataPayload(open, statuses(player)));
		}
	}

	/** Abre la pantalla de diplomacia. */
	public static void open(ServerPlayerEntity player) {
		send(player, true);
	}

	/** Actualiza lo que ve el jugador (si está conectado). */
	public static void sendUpdate(MinecraftServer server, UUID player) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
		if (online != null) {
			send(online, false);
		}
	}
}

package com.evolvedwarfare.client;

import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.network.DiplomacyDataPayload;
import java.util.List;

/** Lo último que mandó el servidor sobre tu reputación con cada facción. */
public final class ClientDiplomacy {
	private static List<DiplomacyDataPayload.Status> statuses = List.of();
	private static long receivedAt;

	private ClientDiplomacy() {
	}

	public static void update(List<DiplomacyDataPayload.Status> list) {
		statuses = List.copyOf(list);
		receivedAt = System.currentTimeMillis();
	}

	public static DiplomacyDataPayload.Status get(Faction faction) {
		int i = faction.ordinal();
		return i < statuses.size() ? statuses.get(i) : new DiplomacyDataPayload.Status(faction.startReputation(), 0, 0, 0);
	}

	/** Ticks pasados desde que llegaron los datos (para descontar de los tiempos que quedan). */
	public static int ticksSinceUpdate() {
		return (int) ((System.currentTimeMillis() - receivedAt) / 50L);
	}
}

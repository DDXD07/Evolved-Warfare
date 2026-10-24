package com.evolvedwarfare.faction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** Reputación, treguas y alianzas de cada jugador con cada facción (se guarda con el mundo, en el Overworld). */
public class ReputationState extends PersistentState {
	private static final String ID = "evolvedwarfare_reputation";
	public static final Type<ReputationState> TYPE = new Type<>(ReputationState::new, ReputationState::fromNbt, null);

	/** Lo de un jugador con todas las facciones. */
	public static final class PlayerRecord {
		final int[] reputation = new int[Faction.values().length];
		final long[] truceUntil = new long[Faction.values().length];
		final boolean[] allied = new boolean[Faction.values().length];
		final long[] aidReadyAt = new long[Faction.values().length];
		/** Solo en memoria: hasta cuándo te tienen ganas por haberles atacado (aunque no seáis enemigos). */
		final long[] provokedUntil = new long[Faction.values().length];

		PlayerRecord() {
			for (Faction faction : Faction.values()) {
				reputation[faction.ordinal()] = faction.startReputation();
			}
		}
	}

	private final Map<UUID, PlayerRecord> players = new HashMap<>();

	public static ReputationState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	/** El registro de un jugador (lo crea con la reputación inicial si no existe). */
	PlayerRecord record(UUID player) {
		return players.computeIfAbsent(player, id -> new PlayerRecord());
	}

	private static ReputationState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		ReputationState state = new ReputationState();
		NbtCompound all = nbt.getCompound("Players");
		for (String key : all.getKeys()) {
			UUID id;
			try {
				id = UUID.fromString(key);
			} catch (IllegalArgumentException e) {
				continue;
			}
			NbtCompound tag = all.getCompound(key);
			PlayerRecord record = new PlayerRecord();
			for (Faction faction : Faction.values()) {
				NbtCompound f = tag.getCompound(faction.id());
				int i = faction.ordinal();
				if (f.contains("Rep")) {
					record.reputation[i] = f.getInt("Rep");
				}
				record.truceUntil[i] = f.getLong("Truce");
				record.allied[i] = f.getBoolean("Allied");
				record.aidReadyAt[i] = f.getLong("Aid");
			}
			state.players.put(id, record);
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtCompound all = new NbtCompound();
		for (Map.Entry<UUID, PlayerRecord> e : players.entrySet()) {
			NbtCompound tag = new NbtCompound();
			PlayerRecord record = e.getValue();
			for (Faction faction : Faction.values()) {
				int i = faction.ordinal();
				NbtCompound f = new NbtCompound();
				f.putInt("Rep", record.reputation[i]);
				f.putLong("Truce", record.truceUntil[i]);
				f.putBoolean("Allied", record.allied[i]);
				f.putLong("Aid", record.aidReadyAt[i]);
				tag.put(faction.id(), f);
			}
			all.put(e.getKey().toString(), tag);
		}
		nbt.put("Players", all);
		return nbt;
	}
}

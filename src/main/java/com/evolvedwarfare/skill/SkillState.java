package com.evolvedwarfare.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

/**
 * Las habilidades de todos los jugadores, guardadas con el mundo (no en el jugador: así las mejoras de liderazgo
 * valen para tus soldados aunque no estés conectado, y no se pierden al morir).
 */
public class SkillState extends PersistentState {
	private static final String ID = "evolvedwarfare_skills";
	public static final Type<SkillState> TYPE = new Type<>(SkillState::new, SkillState::fromNbt, null);

	private final Map<UUID, SkillData> players = new HashMap<>();

	public static SkillState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	public SkillData of(UUID player) {
		return players.computeIfAbsent(player, id -> {
			markDirty();
			return new SkillData();
		});
	}

	@Nullable
	public SkillData peek(UUID player) {
		return players.get(player);
	}

	private static SkillState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		SkillState state = new SkillState();
		NbtCompound all = nbt.getCompound("Players");
		for (String key : all.getKeys()) {
			try {
				state.players.put(UUID.fromString(key), SkillData.fromNbt(all.getCompound(key)));
			} catch (IllegalArgumentException ignored) {
				// una entrada rota: se descarta
			}
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtCompound all = new NbtCompound();
		for (Map.Entry<UUID, SkillData> e : players.entrySet()) {
			all.put(e.getKey().toString(), e.getValue().toNbt());
		}
		nbt.put("Players", all);
		return nbt;
	}
}

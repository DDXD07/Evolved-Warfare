package com.evolvedwarfare.research;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

/** Lo investigado por cada jugador (y lo que tiene a medias), guardado con el mundo. */
public class ResearchState extends PersistentState {
	private static final String ID = "evolvedwarfare_research";
	public static final Type<ResearchState> TYPE = new Type<>(ResearchState::new, ResearchState::fromNbt, null);

	public static final class Entry {
		public final Set<Tech> done = EnumSet.noneOf(Tech.class);
		@Nullable
		public Tech current;
		/** Tics hechos de la técnica en curso (a velocidad normal). */
		public double progress;
	}

	private final Map<UUID, Entry> players = new HashMap<>();

	public static ResearchState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	public Entry of(UUID player) {
		return players.computeIfAbsent(player, id -> new Entry());
	}

	@Nullable
	public Entry peek(UUID player) {
		return players.get(player);
	}

	private static ResearchState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		ResearchState state = new ResearchState();
		NbtCompound all = nbt.getCompound("Players");
		for (String key : all.getKeys()) {
			UUID id;
			try {
				id = UUID.fromString(key);
			} catch (IllegalArgumentException e) {
				continue;
			}
			NbtCompound tag = all.getCompound(key);
			Entry entry = state.of(id);
			NbtList done = tag.getList("Done", NbtElement.STRING_TYPE);
			for (int i = 0; i < done.size(); i++) {
				Tech tech = Tech.byId(done.getString(i));
				if (tech != null) {
					entry.done.add(tech);
				}
			}
			entry.current = tag.contains("Current") ? Tech.byId(tag.getString("Current")) : null;
			entry.progress = tag.getDouble("Progress");
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtCompound all = new NbtCompound();
		for (Map.Entry<UUID, Entry> e : players.entrySet()) {
			NbtCompound tag = new NbtCompound();
			NbtList done = new NbtList();
			for (Tech tech : e.getValue().done) {
				done.add(NbtString.of(tech.id()));
			}
			tag.put("Done", done);
			if (e.getValue().current != null) {
				tag.putString("Current", e.getValue().current.id());
			}
			tag.putDouble("Progress", e.getValue().progress);
			all.put(e.getKey().toString(), tag);
		}
		nbt.put("Players", all);
		return nbt;
	}
}

package com.evolvedwarfare.atlas;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * Lo que guarda el Atlas (del mundo normal): cómo se ve cada chunk explorado (16 x 16 tonos de tinta), qué chunks ha
 * explorado cada jugador, las estructuras que se han visto y los territorios reclamados.
 */
public class AtlasState extends PersistentState {
	public static final String ID = "evolvedwarfare_atlas";
	public static final Type<AtlasState> TYPE = new Type<>(AtlasState::new, AtlasState::fromNbt, null);

	/** Una estructura vista: su id (namespace:path), dónde está (centro) y su nombre (vacío = el de su tipo). */
	public record Marker(long key, String id, int x, int z, String name) {
	}

	/** Un territorio reclamado. */
	public static final class Territory {
		public final int id;
		public String name;
		public final UUID owner;
		public String ownerName;
		public final int color;
		public final LongOpenHashSet chunks = new LongOpenHashSet();

		public Territory(int id, String name, UUID owner, String ownerName, int color) {
			this.id = id;
			this.name = name;
			this.owner = owner;
			this.ownerName = ownerName;
			this.color = color;
		}
	}

	final Long2ObjectMap<byte[]> images = new Long2ObjectOpenHashMap<>();
	final Map<UUID, LongOpenHashSet> explored = new HashMap<>();
	final Long2ObjectMap<Marker> markers = new Long2ObjectOpenHashMap<>();
	final List<Territory> territories = new ArrayList<>();
	int nextId = 1;

	public static AtlasState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	public LongOpenHashSet explored(UUID player) {
		return explored.computeIfAbsent(player, k -> new LongOpenHashSet());
	}

	public List<Territory> territories() {
		return territories;
	}

	public Long2ObjectMap<Marker> markers() {
		return markers;
	}

	/** El territorio que tiene ese chunk, o null. */
	public Territory owner(long chunk) {
		for (Territory t : territories) {
			if (t.chunks.contains(chunk)) {
				return t;
			}
		}
		return null;
	}

	private static AtlasState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		AtlasState s = new AtlasState();
		for (NbtElement e : nbt.getList("Images", NbtElement.COMPOUND_TYPE)) {
			NbtCompound c = (NbtCompound) e;
			byte[] img = c.getByteArray("S");
			if (img.length == 256) {
				s.images.put(c.getLong("P"), img);
			}
		}
		NbtCompound ex = nbt.getCompound("Explored");
		for (String k : ex.getKeys()) {
			try {
				s.explored.put(UUID.fromString(k), new LongOpenHashSet(ex.getLongArray(k)));
			} catch (IllegalArgumentException ignored) {
				// una clave rara: se ignora
			}
		}
		for (NbtElement e : nbt.getList("Markers", NbtElement.COMPOUND_TYPE)) {
			NbtCompound c = (NbtCompound) e;
			s.markers.put(c.getLong("K"), new Marker(c.getLong("K"), c.getString("Id"), c.getInt("X"), c.getInt("Z"), c.getString("Name")));
		}
		for (NbtElement e : nbt.getList("Territories", NbtElement.COMPOUND_TYPE)) {
			NbtCompound c = (NbtCompound) e;
			if (!c.containsUuid("Owner")) {
				continue;
			}
			Territory t = new Territory(c.getInt("Id"), c.getString("Name"), c.getUuid("Owner"), c.getString("OwnerName"), c.getInt("Color"));
			for (long l : c.getLongArray("Chunks")) {
				t.chunks.add(l);
			}
			s.territories.add(t);
		}
		s.nextId = Math.max(1, nbt.getInt("NextId"));
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtList images = new NbtList();
		for (Long2ObjectMap.Entry<byte[]> e : this.images.long2ObjectEntrySet()) {
			NbtCompound c = new NbtCompound();
			c.putLong("P", e.getLongKey());
			c.putByteArray("S", e.getValue());
			images.add(c);
		}
		nbt.put("Images", images);
		NbtCompound ex = new NbtCompound();
		for (Map.Entry<UUID, LongOpenHashSet> e : explored.entrySet()) {
			ex.putLongArray(e.getKey().toString(), e.getValue().toLongArray());
		}
		nbt.put("Explored", ex);
		NbtList markers = new NbtList();
		for (Marker m : this.markers.values()) {
			NbtCompound c = new NbtCompound();
			c.putLong("K", m.key());
			c.putString("Id", m.id());
			c.putInt("X", m.x());
			c.putInt("Z", m.z());
			c.putString("Name", m.name());
			markers.add(c);
		}
		nbt.put("Markers", markers);
		NbtList terr = new NbtList();
		for (Territory t : territories) {
			NbtCompound c = new NbtCompound();
			c.putInt("Id", t.id);
			c.putString("Name", t.name);
			c.putUuid("Owner", t.owner);
			c.putString("OwnerName", t.ownerName);
			c.putInt("Color", t.color);
			c.putLongArray("Chunks", t.chunks.toLongArray());
			terr.add(c);
		}
		nbt.put("Territories", terr);
		nbt.putInt("NextId", nextId);
		return nbt;
	}
}

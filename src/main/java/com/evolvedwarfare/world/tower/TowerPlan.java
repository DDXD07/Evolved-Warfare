package com.evolvedwarfare.world.tower;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que sale del generador de torres (Java puro, sin Minecraft: también lo usan las vistas previas de tools/). En
 * coordenadas locales con el centro de la torre en (0, 0), x = este, y = arriba, z = sur y la puerta mirando al norte
 * (-z); y = 0 es el suelo (la capa de hierba se sustituye).
 */
public final class TowerPlan {
	/** Bajo este bloque se rellena hasta el suelo (cimientos): la torre nunca queda colgando en una ladera. */
	public static final int FOUNDATION = 1;
	/** Hueco: se deja aire (quita la tierra o la hierba alta que hubiera dentro). */
	public static final int AIR = 2;
	/** Solo se pone si ahí hay suelo natural (los caminos y la grava del patio). */
	public static final int GROUND = 4;

	public record Block(int x, int y, int z, String state, int flags) {
		public boolean has(int flag) {
			return (flags & flag) != 0;
		}
	}

	/** Un cofre o barril con su tabla de botín. */
	public record Loot(int x, int y, int z, String table) {
	}

	/** Un recluta: el bloque donde aparece (encima del suelo) y hacia dónde mira (grados de Minecraft). */
	public record Guard(int x, int y, int z, float yaw) {
	}

	private final Map<Long, Block> blocks = new LinkedHashMap<>();
	private final List<Loot> loot = new ArrayList<>();
	private final List<Guard> guards = new ArrayList<>();
	/** Lo que ha salido de la semilla (para las vistas previas y los registros). */
	public String description = "";

	private static long key(int x, int y, int z) {
		return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (long) (z & 0xFFFFF);
	}

	public void set(int x, int y, int z, String state, int flags) {
		String full = state.indexOf(':') < 0 ? "minecraft:" + state : state;
		blocks.put(key(x, y, z), new Block(x, y, z, full, flags));
	}

	public void set(int x, int y, int z, String state) {
		set(x, y, z, state, 0);
	}

	/** Solo si está libre. */
	public void setIfEmpty(int x, int y, int z, String state) {
		if (!has(x, y, z)) {
			set(x, y, z, state, 0);
		}
	}

	public void air(int x, int y, int z) {
		set(x, y, z, "air", AIR);
	}

	public boolean has(int x, int y, int z) {
		return blocks.containsKey(key(x, y, z));
	}

	/** El bloque que hay (o null). */
	public String get(int x, int y, int z) {
		Block b = blocks.get(key(x, y, z));
		return b == null ? null : b.state();
	}

	/** ¿Hay algo que no sea aire? */
	public boolean solid(int x, int y, int z) {
		String s = get(x, y, z);
		return s != null && !s.equals("minecraft:air");
	}

	public void remove(int x, int y, int z) {
		blocks.remove(key(x, y, z));
	}

	public void loot(int x, int y, int z, String table) {
		loot.add(new Loot(x, y, z, table));
	}

	public void guard(int x, int y, int z, float yaw) {
		guards.add(new Guard(x, y, z, yaw));
	}

	public Collection<Block> blocks() {
		return blocks.values();
	}

	public List<Loot> lootSpots() {
		return loot;
	}

	public List<Guard> guards() {
		return guards;
	}

	/** {minX, minY, minZ, maxX, maxY, maxZ} de todo lo que se pone. */
	public int[] bounds() {
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (Block block : blocks.values()) {
			b[0] = Math.min(b[0], block.x());
			b[1] = Math.min(b[1], block.y());
			b[2] = Math.min(b[2], block.z());
			b[3] = Math.max(b[3], block.x());
			b[4] = Math.max(b[4], block.y());
			b[5] = Math.max(b[5], block.z());
		}
		return b;
	}
}

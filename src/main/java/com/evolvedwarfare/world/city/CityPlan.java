package com.evolvedwarfare.world.city;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que sale del diseñador de ciudades (Java puro, sin Minecraft: también lo usan las vistas previas de tools/). En
 * coordenadas locales con el centro de la plaza en (0, 0): x = este, y = arriba, z = sur; y = 0 es la capa del suelo (la
 * hierba se sustituye por calles, suelos y patios).
 */
public final class CityPlan {
	/** Bajo este bloque se rellena hasta el suelo firme (la ciudad nunca queda colgando). */
	public static final int FOUNDATION = 1;
	/** Hueco: se deja aire. */
	public static final int AIR = 2;
	/** Solo se pone si ahí hay suelo natural (los caminos de fuera de las puertas). */
	public static final int GROUND = 4;

	public record Block(int x, int y, int z, String state, int flags) {
		public boolean has(int flag) {
			return (flags & flag) != 0;
		}
	}

	/** Un cofre o barril con su tabla de botín. */
	public record Loot(int x, int y, int z, String table) {
	}

	/**
	 * Un mercader: dónde está detrás del mostrador (el bloque donde se pone de pie), hacia dónde mira (a los clientes)
	 * y su cama (la cabecera).
	 */
	public record Trader(String type, int x, int y, int z, float yaw, int bedX, int bedY, int bedZ) {
	}

	/** Un guardia: su puesto, hacia dónde mira y su clase (warrior, archer, crossbowman). */
	public record Guard(String role, int x, int y, int z, float yaw) {
	}

	/** Un vecino (recluta que se puede contratar), en su casa. */
	public record Resident(int x, int y, int z) {
	}

	/**
	 * Una cama con su dueño (fase 58): la cabecera, de qué es ("house": un vecino; "barracks": un guardia; las de las
	 * tiendas son de su mercader y no se apuntan) y su casa (los de la misma casa son familia).
	 */
	public record Bed(int x, int y, int z, String role, int household) {
	}

	private final Map<Long, Block> blocks = new HashMap<>();
	private final List<Loot> loot = new ArrayList<>();
	private final List<Trader> traders = new ArrayList<>();
	private final List<Guard> guards = new ArrayList<>();
	private final List<Resident> residents = new ArrayList<>();
	private final List<Bed> beds = new ArrayList<>();
	/** Medio ancho de la muralla (de la plaza al borde de fuera) en x y en z. */
	public int halfX;
	public int halfZ;
	/** Lo que ha salido de la semilla (para las vistas previas y los registros). */
	public String description = "";

	public static long key(int x, int y, int z) {
		return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (long) (z & 0xFFFFF);
	}

	public void set(int x, int y, int z, String state, int flags) {
		String full = state.indexOf(':') < 0 ? "minecraft:" + state : state;
		blocks.put(key(x, y, z), new Block(x, y, z, full, flags));
	}

	public void set(int x, int y, int z, String state) {
		set(x, y, z, state, 0);
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

	public void remove(int x, int y, int z) {
		blocks.remove(key(x, y, z));
	}

	public void loot(int x, int y, int z, String table) {
		loot.add(new Loot(x, y, z, table));
	}

	public void trader(Trader trader) {
		traders.add(trader);
	}

	public void guard(Guard guard) {
		guards.add(guard);
	}

	public void resident(int x, int y, int z) {
		residents.add(new Resident(x, y, z));
	}

	public Collection<Block> blocks() {
		return blocks.values();
	}

	public List<Loot> lootSpots() {
		return loot;
	}

	public List<Trader> traders() {
		return traders;
	}

	public List<Guard> guards() {
		return guards;
	}

	public List<Resident> residents() {
		return residents;
	}

	public void bed(Bed bed) {
		beds.add(bed);
	}

	public List<Bed> beds() {
		return beds;
	}

	/** ¿Está dentro de la muralla (o en ella)? Ahí se limpia todo lo que no sea de la ciudad. */
	public boolean inside(int x, int z) {
		return Math.abs(x) <= halfX && Math.abs(z) <= halfZ;
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

package com.evolvedwarfare.building;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plano de un edificio, en coordenadas relativas al puesto de trabajo y con el puesto mirando al norte:
 * x = este, y = arriba, z = sur (el frente del puesto es -z). El puesto está en (0, 0, 0) y el suelo natural en y = -1.
 *
 * <p>Cada pieza dice qué bloque va (como texto: {@code minecraft:oak_stairs[facing=east]}), qué material gasta y
 * cómo se pone. Las piezas sin bloque son huecos: solo se limpian (hierba, tierra, árboles...).</p>
 */
public final class Blueprint {
	/** Hueco: no se pone nada, solo se quita lo natural que haya. */
	public static final int AIR = 1;
	/** Bajo esta pieza se rellenan con tierra los huecos (hasta 4 bloques): suelos y cosas apoyadas en el suelo. */
	public static final int FOUNDATION = 2;
	/** Va al final, cuando ya hay paredes donde apoyarse (antorchas, faroles, alfombras). */
	public static final int LATE = 4;
	/** Sigue el terreno: su y cuenta desde el suelo de esa columna (las vallas de un cercado). */
	public static final int GROUND = 8;
	/** Mitad de arriba de una puerta: la pone la de abajo. */
	public static final int UPPER_HALF = 16;

	public record Piece(int x, int y, int z, String state, Material material, int flags) {
		public boolean isAir() {
			return (flags & AIR) != 0;
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}
	}

	private final Map<Long, Piece> pieces = new LinkedHashMap<>();

	private static long key(int x, int y, int z) {
		return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (long) (z & 0xFFFFF);
	}

	/** Pone (o cambia) la pieza de esa posición. La posición del puesto no se toca nunca. */
	public void set(int x, int y, int z, String state, Material material, int flags) {
		if (x == 0 && z == 0 && (y == 0 || y == 1)) {
			return;
		}
		pieces.put(key(x, y, z), new Piece(x, y, z, state, material, flags));
	}

	public void set(int x, int y, int z, String state, Material material) {
		set(x, y, z, state, material, 0);
	}

	/** Hueco que hay que dejar libre (no pisa piezas que ya tengan bloque). */
	public void air(int x, int y, int z) {
		if (!pieces.containsKey(key(x, y, z))) {
			set(x, y, z, null, Material.FREE, AIR);
		}
	}

	public void remove(int x, int y, int z) {
		pieces.remove(key(x, y, z));
	}

	public Piece get(int x, int y, int z) {
		return pieces.get(key(x, y, z));
	}

	public Collection<Piece> pieces() {
		return pieces.values();
	}

	/** Solo las piezas con bloque. */
	public List<Piece> blocks() {
		List<Piece> list = new ArrayList<>();
		for (Piece piece : pieces.values()) {
			if (!piece.isAir()) {
				list.add(piece);
			}
		}
		return list;
	}

	/** Cuánto hace falta de cada material (sin contar lo gratis). */
	public Map<Material, Integer> materials() {
		Map<Material, Integer> totals = new EnumMap<>(Material.class);
		for (Piece piece : pieces.values()) {
			if (!piece.isAir() && piece.material() != Material.FREE) {
				totals.merge(piece.material(), 1, Integer::sum);
			}
		}
		return totals;
	}
}

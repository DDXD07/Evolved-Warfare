package com.evolvedwarfare.world.city;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Comprobaciones de una ciudad diseñada (Java puro, para las pruebas y las vistas previas): que cada mercader pueda ir
 * andando de su mostrador a su cama y que desde cada puerta se llegue a la plaza. Anda como una criatura: un bloque a la
 * vez, subiendo o bajando como mucho uno, con dos de alto libres.
 */
public final class CityChecks {
	private CityChecks() {
	}

	/** ¿Se puede estar de pie dentro de ese bloque (los pies)? */
	static boolean passable(String state) {
		if (state == null) {
			return true;
		}
		String name = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
		return name.equals("minecraft:air") || name.endsWith("_door") || name.endsWith("torch") || name.endsWith("_carpet")
				|| name.endsWith("_banner") || name.equals("minecraft:ladder") || name.endsWith("wheat") || name.endsWith("carrots")
				|| name.endsWith("potatoes") || name.endsWith("beetroots");
	}

	/** ¿Se puede pisar (hay algo debajo de los pies)? */
	static boolean floor(String state) {
		return state != null && !passable(state) && !state.startsWith("minecraft:water");
	}

	private static boolean standable(CityPlan plan, int x, int y, int z) {
		return passable(plan.get(x, y, z)) && passable(plan.get(x, y + 1, z)) && floor(plan.get(x, y - 1, z));
	}

	/** Cuántos pasos hay de (x, y, z) a algún sitio a menos de {@code near} bloques del destino (o -1). */
	public static int walk(CityPlan plan, int x, int y, int z, int tx, int ty, int tz, int near) {
		int limit = Math.max(plan.halfX, plan.halfZ) + 16;
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		queue.add(new int[] {x, y, z, 0});
		seen.add(CityPlan.key(x, y, z));
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			if (Math.abs(c[0] - tx) + Math.abs(c[2] - tz) <= near && Math.abs(c[1] - ty) <= 1) {
				return c[3];
			}
			for (int[] d : dirs) {
				int nx = c[0] + d[0];
				int nz = c[2] + d[1];
				if (Math.abs(nx) > limit || Math.abs(nz) > limit) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					int ny = c[1] + dy;
					// subir a un bloque entero es un salto (hace falta sitio encima); a una escalera se sube andando
					String step = plan.get(nx, c[1], nz);
					if (dy == 1 && !(step != null && step.contains("_stairs")) && !passable(plan.get(c[0], c[1] + 2, c[2]))) {
						continue;
					}
					if (standable(plan, nx, ny, nz) && seen.add(CityPlan.key(nx, ny, nz))) {
						queue.add(new int[] {nx, ny, nz, c[3] + 1});
						break;
					}
				}
			}
		}
		return -1;
	}

	/** Mercaderes que no pueden llegar a su cama (0 = todos llegan). */
	public static int strandedTraders(CityPlan plan) {
		int bad = 0;
		for (CityPlan.Trader t : plan.traders()) {
			if (!standable(plan, t.x(), t.y(), t.z()) || walk(plan, t.x(), t.y(), t.z(), t.bedX(), t.bedY(), t.bedZ(), 2) < 0) {
				bad++;
			}
		}
		return bad;
	}

	/** ¿Se llega andando de fuera de cada puerta a la plaza? Devuelve cuántas puertas no llegan. */
	public static int closedGates(CityPlan plan) {
		int bad = 0;
		int[][] outs = {{0, -(plan.halfZ + 2)}, {0, plan.halfZ + 2}, {-(plan.halfX + 2), 0}, {plan.halfX + 2, 0}};
		for (int[] o : outs) {
			String road = plan.get(o[0], 0, o[1]);
			if (road == null) {
				continue; // no hay puerta en ese lado
			}
			if (walk(plan, o[0], 1, o[1], 4, 1, 0, 1) < 0 && walk(plan, o[0], 1, o[1], -4, 1, 0, 1) < 0
					&& walk(plan, o[0], 1, o[1], 0, 1, 4, 1) < 0 && walk(plan, o[0], 1, o[1], 0, 1, -4, 1) < 0) {
				bad++;
			}
		}
		return bad;
	}
}

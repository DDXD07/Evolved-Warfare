package com.evolvedwarfare.world.tower;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Comprobaciones de una torre generada (Java puro: las usan las pruebas y las vistas previas). */
public final class TowerChecks {
	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private TowerChecks() {
	}

	/** ¿Se puede pasar por ahí? (aire, alfombras, nieve fina, escaleras de mano, puertas, trampillas, hiedra...) */
	public static boolean passable(String state) {
		if (state == null || state.equals("minecraft:air")) {
			return true;
		}
		return state.contains("carpet") || state.contains("snow[layers=1]") || state.contains("ladder") || state.contains("_door[")
				|| state.contains("trapdoor") || state.contains("vine") || state.contains("banner") || state.contains("torch")
				|| state.contains("fence_gate");
	}

	private static boolean ladder(String state) {
		return state != null && state.contains("ladder");
	}

	/** Lo que no está en el plano: por debajo de y = 1, suelo natural; por encima, aire. */
	private static String at(TowerPlan plan, int x, int y, int z) {
		String s = plan.get(x, y, z);
		if (s == null && y <= 0) {
			return "minecraft:dirt";
		}
		return s;
	}

	private static boolean stand(TowerPlan plan, int x, int y, int z) {
		String here = at(plan, x, y, z);
		String head = at(plan, x, y + 1, z);
		String floor = at(plan, x, y - 1, z);
		return passable(here) && passable(head) && (!passable(floor) || ladder(here) || ladder(floor));
	}

	/**
	 * ¿Se puede entrar por la puerta y subir hasta arriba? Busca a pie (andar, subir un escalón, trepar por la
	 * escalera de mano) desde delante de la puerta hasta pisar la plataforma.
	 */
	public static boolean reachesTop(TowerPlan plan, int deck) {
		int[] b = plan.bounds();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		int startZ = b[2] - 1;
		int[] start = {0, 1, startZ};
		// delante de la puerta: lo primero libre bajando por el camino
		for (int z = startZ; z < 0; z++) {
			if (stand(plan, 0, 1, z)) {
				start = new int[] {0, 1, z};
				break;
			}
		}
		queue.add(start);
		seen.add(key(start[0], start[1], start[2]));
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			int x = p[0];
			int y = p[1];
			int z = p[2];
			if (y >= deck + 1 && Math.abs(x) <= b[3] && Math.abs(z) <= b[5]) {
				String floor = at(plan, x, y - 1, z);
				if (floor != null && !passable(floor)) {
					return true;
				}
			}
			for (int[] s : SIDES) {
				int nx = x + s[0];
				int nz = z + s[1];
				if (nx < b[0] - 2 || nx > b[3] + 2 || nz < b[2] - 2 || nz > b[5] + 2) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					int ny = y + dy;
					if (dy == 1 && !passable(at(plan, x, y + 2, z))) {
						continue;
					}
					if (stand(plan, nx, ny, nz) && seen.add(key(nx, ny, nz))) {
						queue.add(new int[] {nx, ny, nz});
					}
				}
			}
			// trepar
			if (ladder(at(plan, x, y, z)) || ladder(at(plan, x, y + 1, z))) {
				if (passable(at(plan, x, y + 1, z)) && passable(at(plan, x, y + 2, z)) && seen.add(key(x, y + 1, z))) {
					queue.add(new int[] {x, y + 1, z});
				}
			}
		}
		return false;
	}

	/** Bloques de la torre que no son de Minecraft (para la prueba de que todos existen). */
	public static Set<String> blockIds(TowerPlan plan) {
		Set<String> ids = new HashSet<>();
		for (TowerPlan.Block block : plan.blocks()) {
			String s = block.state();
			int bracket = s.indexOf('[');
			ids.add(bracket < 0 ? s : s.substring(0, bracket));
		}
		return ids;
	}

	/** La altura de la plataforma (la de la trampilla). */
	public static int deck(TowerPlan plan) {
		int deck = -1;
		for (TowerPlan.Block block : plan.blocks()) {
			if (block.state().contains("trapdoor")) {
				deck = Math.max(deck, block.y());
			}
		}
		return deck;
	}

	public static List<TowerPlan.Guard> guards(TowerPlan plan) {
		return plan.guards();
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (long) (z & 0xFFFFF);
	}
}

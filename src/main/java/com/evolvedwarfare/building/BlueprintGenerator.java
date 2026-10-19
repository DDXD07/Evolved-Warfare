package com.evolvedwarfare.building;

import java.util.Random;

/**
 * Genera el edificio de cada oficio a partir de una semilla. La misma semilla da siempre el mismo edificio, en el
 * cliente (vista previa) y en el servidor (obra).
 *
 * <p>Java puro, sin clases de Minecraft, para poder probarlo y dibujarlo fuera del juego
 * ({@code tools/ew_buildings.py}).</p>
 *
 * <p>Todos los edificios dejan libre la zona de trabajo del oficio, que empieza delante del puesto (z = -2 hacia
 * delante): el edificio va del puesto hacia atrás y a los lados.</p>
 */
public final class BlueprintGenerator {
	static final String[] WOODS = {"oak", "spruce", "birch", "dark_oak", "jungle", "acacia"};
	/** Medio lado del cercado del pastor y del criador: el mismo que su zona de trabajo. */
	public static final int PEN_RADIUS = 8;

	private BlueprintGenerator() {
	}

	public static Blueprint generate(String profession, int seed) {
		Random r = new Random(mix(seed * 0x9E3779B97F4A7C15L + profession.hashCode()));
		Blueprint b = new Blueprint();
		switch (profession) {
			case "farmer" -> farm(b, r);
			case "lumberjack" -> cabin(b, r);
			case "miner" -> mineBarn(b, r);
			case "digger" -> diggerShed(b, r);
			case "fisherman" -> fishingHut(b, r);
			case "shepherd" -> stable(b, r);
			case "breeder" -> coop(b, r);
			case "beekeeper" -> apiary(b, r);
			case "gardener" -> greenhouse(b, r);
			case "blacksmith" -> forge(b, r);
			case "cook" -> kitchen(b, r);
			case "charcoal_burner" -> kiln(b, r);
			case "stonemason" -> masonry(b, r);
			case "fletcher" -> fletchery(b, r);
			default -> farm(b, r);
		}
		return b;
	}

	/** Mezcla la semilla (con semillas seguidas, java.util.Random da casi los mismos primeros números). */
	static long mix(long z) {
		z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
		z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return z ^ (z >>> 33);
	}

	// ================================================================== estados de bloque

	static String s(String name, String... props) {
		StringBuilder sb = new StringBuilder("minecraft:").append(name);
		if (props.length > 0) {
			sb.append('[');
			for (int i = 0; i < props.length; i += 2) {
				if (i > 0) {
					sb.append(',');
				}
				sb.append(props[i]).append('=').append(props[i + 1]);
			}
			sb.append(']');
		}
		return sb.toString();
	}

	static String pick(Random r, String... options) {
		return options[r.nextInt(options.length)];
	}

	static String planks(String wood) {
		return s(wood + "_planks");
	}

	static String log(String wood, String axis) {
		return s(wood + "_log", "axis", axis);
	}

	static String stairs(String wood, String facing) {
		return s(wood + "_stairs", "facing", facing, "half", "bottom", "shape", "straight");
	}

	static String slab(String wood) {
		return s(wood + "_slab", "type", "bottom");
	}

	static String topSlab(String wood) {
		return s(wood + "_slab", "type", "top");
	}

	static String fence(String wood, boolean north, boolean east, boolean south, boolean west) {
		return s(wood + "_fence", "north", "" + north, "east", "" + east, "south", "" + south, "west", "" + west);
	}

	static String pane(boolean alongX) {
		return s("glass_pane", "north", "" + !alongX, "south", "" + !alongX, "east", "" + alongX, "west", "" + alongX);
	}

	static String wallTorch(String facing) {
		return s("wall_torch", "facing", facing);
	}

	/** Material de una variante de piedra o de madera, según su nombre. */
	static Material materialOf(String state) {
		String n = state.substring("minecraft:".length());
		int bracket = n.indexOf('[');
		if (bracket >= 0) {
			n = n.substring(0, bracket);
		}
		if (n.endsWith("_log") || n.endsWith("_wood") || n.equals("campfire")) {
			return Material.LOGS;
		}
		if (n.contains("cobble") || n.contains("stone") || n.contains("andesite") || n.contains("brick")) {
			return Material.STONE;
		}
		if (n.contains("glass")) {
			return Material.GLASS;
		}
		if (n.contains("torch") || n.contains("lantern")) {
			return Material.TORCH;
		}
		if (n.endsWith("_wool") || n.endsWith("_carpet")) {
			return Material.WOOL;
		}
		if (n.equals("hay_block")) {
			return Material.HAY;
		}
		if (n.equals("dirt") || n.equals("coarse_dirt") || n.equals("dirt_path") || n.equals("gravel")) {
			return Material.FREE;
		}
		return Material.PLANKS;
	}

	static void put(Blueprint b, int x, int y, int z, String state) {
		b.set(x, y, z, state, materialOf(state));
	}

	static void put(Blueprint b, int x, int y, int z, String state, int flags) {
		b.set(x, y, z, state, materialOf(state), flags);
	}

	/** Algo apoyado en el suelo fuera de la casa: rellena el hueco de debajo si el terreno baja. */
	static void ground(Blueprint b, int x, int y, int z, String state) {
		b.set(x, y, z, state, materialOf(state), y == 0 ? Blueprint.FOUNDATION : 0);
	}

	// ================================================================== la casa genérica

	enum Walls { PLANKS, COBBLE_BASE, LOGS, TIMBER, STONE }

	/** Una casa rectangular con tejado a dos aguas. El frente está en z = -1 y la pared de atrás en z = back. */
	static final class House {
		int half = 2;          // de -half a half en x
		int back = 4;          // pared de atrás
		int height = 3;        // alto de las paredes
		String wood = "oak";
		String roofWood = "spruce";
		String trim = "oak";   // troncos de las esquinas (madera de entramado)
		Walls walls = Walls.PLANKS;
		boolean roofAlongZ = true;
		int doorX = 1;
		boolean glass = true;
		boolean stoneFloor = false;
		boolean openFront = false;

		int roofTop() {
			int span = roofAlongZ ? half : (back + 1) / 2;
			return height + span;
		}
	}

	static House randomHouse(Random r, String... woods) {
		House h = new House();
		h.half = r.nextInt(3) == 0 ? 3 : 2;
		h.back = 3 + r.nextInt(3);
		h.height = r.nextInt(3) == 0 ? 4 : 3;
		h.wood = woods.length > 0 ? pick(r, woods) : pick(r, WOODS);
		h.roofWood = r.nextBoolean() ? h.wood : pick(r, "spruce", "dark_oak", "oak");
		h.trim = r.nextBoolean() ? h.wood : pick(r, "spruce", "dark_oak", "oak");
		h.walls = Walls.values()[r.nextInt(4)];
		h.roofAlongZ = (h.back % 2 == 0) || r.nextBoolean();
		h.doorX = r.nextBoolean() ? 1 : -1;
		h.glass = r.nextInt(4) != 0;
		h.stoneFloor = r.nextInt(3) == 0;
		return h;
	}

	static String wallBlock(House h, int y, boolean alongX) {
		String axis = alongX ? "x" : "z";
		return switch (h.walls) {
			case PLANKS -> planks(h.wood);
			case COBBLE_BASE -> y == 0 ? s("cobblestone") : planks(h.wood);
			case LOGS -> log(h.wood, axis);
			case TIMBER -> y == h.height - 1 ? log(h.trim, axis) : planks(h.wood.equals("birch") ? "oak" : "birch");
			case STONE -> y == 0 ? s("cobblestone") : s("stone_bricks");
		};
	}

	static String gableBlock(House h) {
		return switch (h.walls) {
			case STONE -> s("stone_bricks");
			case TIMBER -> planks(h.wood.equals("birch") ? "oak" : "birch");
			default -> planks(h.wood);
		};
	}

	/** Suelo, paredes, puerta, ventanas, tejado y antorchas. */
	static void house(Blueprint b, House h) {
		int a = h.half;
		int zb = h.back;
		// suelo
		String floor = h.stoneFloor ? s("cobblestone") : planks(h.wood);
		for (int x = -a; x <= a; x++) {
			for (int z = -1; z <= zb; z++) {
				put(b, x, -1, z, floor, Blueprint.FOUNDATION);
			}
		}
		// paredes y hueco interior
		for (int y = 0; y < h.height; y++) {
			for (int x = -a; x <= a; x++) {
				for (int z = -1; z <= zb; z++) {
					boolean side = Math.abs(x) == a;
					boolean frontBack = z == -1 || z == zb;
					if (side && frontBack) {
						put(b, x, y, z, h.walls == Walls.STONE ? s("stone_bricks") : log(h.trim, "y"));
					} else if (side) {
						put(b, x, y, z, wallBlock(h, y, false));
					} else if (frontBack) {
						if (z == -1 && h.openFront && y < h.height - 1) {
							b.air(x, y, z);
						} else {
							put(b, x, y, z, wallBlock(h, y, true));
						}
					} else {
						b.air(x, y, z);
					}
				}
			}
		}
		// puerta (y el paso de delante libre)
		if (!h.openFront) {
			b.set(h.doorX, 0, -1, s(h.wood + "_door", "facing", "south", "half", "lower", "hinge", h.doorX > 0 ? "left" : "right",
					"open", "false"), Material.PLANKS);
			b.set(h.doorX, 1, -1, s(h.wood + "_door", "facing", "south", "half", "upper", "hinge", h.doorX > 0 ? "left" : "right",
					"open", "false"), Material.FREE, Blueprint.UPPER_HALF);
		}
		b.air(h.doorX, 0, -2);
		b.air(h.doorX, 1, -2);
		// ventanas
		int wy = 1;
		for (int z = 1; z <= zb - 1; z += 2) {
			window(b, h, -a, wy, z, false);
			window(b, h, a, wy, z, false);
		}
		if (!h.openFront && a >= 2) {
			window(b, h, -h.doorX, wy, -1, true);
		}
		if (a >= 3) {
			window(b, h, -1, wy, zb, true);
			window(b, h, 1, wy, zb, true);
		} else {
			window(b, h, 0, wy, zb, true);
		}
		roof(b, h);
		// antorcha dentro (pared de atrás) y fuera, junto a la puerta
		put(b, -h.doorX * (a - 1), 2, zb - 1, wallTorch("north"), Blueprint.LATE);
		if (!h.openFront) {
			int tx = h.doorX + (h.doorX > 0 ? 1 : -1);
			put(b, tx, 2, -2, wallTorch("north"), Blueprint.LATE);
		}
	}

	static void window(Blueprint b, House h, int x, int y, int z, boolean alongX) {
		if (h.glass) {
			put(b, x, y, z, pane(alongX));
			if (h.height >= 4) {
				put(b, x, y + 1, z, pane(alongX));
			}
		} else {
			// postigos: hueco con contraventanas abiertas por fuera
			put(b, x, y, z, fence(h.wood, !alongX, alongX, !alongX, alongX));
		}
	}

	/** Tejado a dos aguas con alero de un bloque y frontones. */
	static void roof(Blueprint b, House h) {
		int a = h.half;
		int zb = h.back;
		String gable = gableBlock(h);
		if (h.roofAlongZ) {
			for (int k = 0; k <= a; k++) {
				int xs = a + 1 - k;
				int y = h.height - 1 + k;
				for (int z = -2; z <= zb + 1; z++) {
					put(b, -xs, y, z, stairs(h.roofWood, "east"));
					put(b, xs, y, z, stairs(h.roofWood, "west"));
				}
			}
			for (int z = -2; z <= zb + 1; z++) {
				put(b, 0, h.height + a, z, slab(h.roofWood));
			}
			for (int j = 0; j < a; j++) {
				for (int x = -(a - 1 - j); x <= a - 1 - j; x++) {
					put(b, x, h.height + j, -1, gable);
					put(b, x, h.height + j, zb, gable);
					for (int z = 0; z < zb; z++) {
						b.air(x, h.height + j, z);
					}
				}
			}
		} else {
			int c = (zb + 1) / 2;
			int zc = zb - c;
			for (int k = 0; k <= c; k++) {
				int zs = c + 1 - k;
				int y = h.height - 1 + k;
				for (int x = -a - 1; x <= a + 1; x++) {
					put(b, x, y, zc - zs, stairs(h.roofWood, "south"));
					put(b, x, y, zc + zs, stairs(h.roofWood, "north"));
				}
			}
			for (int x = -a - 1; x <= a + 1; x++) {
				put(b, x, h.height + c, zc, slab(h.roofWood));
			}
			for (int j = 0; j < c; j++) {
				for (int z = zc - (c - 1 - j); z <= zc + (c - 1 - j); z++) {
					put(b, -a, h.height + j, z, gable);
					put(b, a, h.height + j, z, gable);
					for (int x = -a + 1; x < a; x++) {
						b.air(x, h.height + j, z);
					}
				}
			}
		}
	}

	/** Celdas del suelo de dentro libres para muebles (ni el puesto ni el paso desde la puerta). */
	static boolean freeInside(House h, int x, int z) {
		if (Math.abs(x) >= h.half || z < 0 || z >= h.back) {
			return false;
		}
		if (x == 0 && z == 0) {
			return false;
		}
		return !(x == h.doorX && z <= 1);
	}

	/** Pone muebles junto a la pared de atrás y los lados, sin tapar el paso. */
	static void furnish(Blueprint b, House h, Random r, String... items) {
		int placed = 0;
		int zb = h.back;
		int[][] spots = new int[(2 * h.half) * 3][];
		int n = 0;
		for (int x = -h.half + 1; x <= h.half - 1; x++) {
			spots[n++] = new int[] {x, zb - 1};
		}
		for (int z = zb - 2; z >= 1; z--) {
			spots[n++] = new int[] {-h.half + 1, z};
			spots[n++] = new int[] {h.half - 1, z};
		}
		for (int i = 0; i < n && placed < items.length; i++) {
			int[] spot = spots[(i * 7 + r.nextInt(3)) % n];
			if (freeInside(h, spot[0], spot[1]) && b.get(spot[0], 0, spot[1]) != null && b.get(spot[0], 0, spot[1]).isAir()) {
				put(b, spot[0], 0, spot[1], items[placed++]);
			}
		}
	}

	// ================================================================== cercado (pastor y criador)

	/** Valla en el borde de la zona de trabajo (delante del puesto), con una cancela para el jugador al fondo. */
	static void pen(Blueprint b, House h, String wood) {
		int r = PEN_RADIUS;
		int zFront = -2 - 2 * r;
		int zBack = -2;
		for (int x = -r; x <= r; x++) {
			for (int z = zFront; z <= zBack; z++) {
				boolean edgeX = Math.abs(x) == r;
				boolean edgeZ = z == zFront || z == zBack;
				if (!edgeX && !edgeZ) {
					continue;
				}
				// la casilla delante de la puerta de la casa queda abierta: se entra al cercado por la casa
				if (z == zBack && x == h.doorX) {
					continue;
				}
				if (z == zFront && x == 0) {
					b.set(x, 0, z, s(wood + "_fence_gate", "facing", "north", "open", "false", "in_wall", "false"),
							Material.PLANKS, Blueprint.GROUND);
					continue;
				}
				boolean north = edgeX && z > zFront;
				boolean south = edgeX && z < zBack;
				boolean east = edgeZ && x < r;
				boolean west = edgeZ && x > -r;
				// junto a la puerta de la casa la valla se corta
				if (z == zBack && x == h.doorX - 1) {
					east = false;
				}
				if (z == zBack && x == h.doorX + 1) {
					west = false;
				}
				b.set(x, 0, z, fence(wood, north, east, south, west), Material.PLANKS, Blueprint.GROUND);
			}
		}
	}

	// ================================================================== oficios

	/** Granja: casa de labranza con pacas de heno y compostador. */
	static void farm(Blueprint b, Random r) {
		House h = randomHouse(r, "oak", "spruce", "birch", "dark_oak");
		house(b, h);
		furnish(b, h, r, s("composter"), s("barrel", "facing", "up"), s("hay_block", "axis", "y"), s("crafting_table"));
		// pacas fuera, a un lado
		int side = r.nextBoolean() ? 1 : -1;
		int x = side * (h.half + 2);
		for (int z = 0; z <= Math.min(2, h.back - 1); z++) {
			ground(b, x, 0, z, s("hay_block", "axis", r.nextBoolean() ? "x" : "y"));
			if (z == 1) {
				put(b, x, 1, z, s("hay_block", "axis", "z"));
			}
		}
	}

	/** Cabaña de troncos con leñera cubierta al lado. */
	static void cabin(Blueprint b, Random r) {
		House h = randomHouse(r, "spruce", "dark_oak", "oak", "jungle");
		h.walls = r.nextInt(3) == 0 ? Walls.TIMBER : Walls.LOGS;
		house(b, h);
		furnish(b, h, r, s("crafting_table"), s("barrel", "facing", "up"));
		// leñera: troncos apilados bajo un tejadillo
		int side = -h.doorX;
		int x0 = side * (h.half + 2);
		int x1 = side * (h.half + 3);
		for (int z = 0; z <= Math.min(3, h.back); z++) {
			for (int x : new int[] {x0, x1}) {
				ground(b, x, 0, z, log(h.wood, "z"));
				put(b, x, 1, z, log(h.wood, "z"));
				put(b, x, 2, z, slab(h.roofWood));
			}
		}
		// tocón para partir leña
		ground(b, side * (h.half + 2), 0, -1, s("stripped_" + h.wood + "_log", "axis", "y"));
	}

	/** Granero sobre la entrada de la mina: portón abierto, sin suelo (la escalera baja por dentro). */
	static void mineBarn(Blueprint b, Random r) {
		String wood = pick(r, "oak", "spruce", "dark_oak", "birch");
		String roofWood = r.nextBoolean() ? wood : pick(r, "spruce", "dark_oak");
		String trim = r.nextBoolean() ? wood : pick(r, "spruce", "dark_oak", "oak");
		boolean cobbleBase = r.nextBoolean();
		boolean glass = r.nextInt(3) == 0;
		int back = 5 + r.nextInt(2);
		int height = r.nextInt(3) == 0 ? 4 : 3;
		int a = 2;
		for (int y = 0; y < height; y++) {
			for (int z = 1; z <= back; z++) {
				for (int x = -a; x <= a; x++) {
					boolean side = Math.abs(x) == a;
					boolean corner = side && (z == 1 || z == back);
					if (corner) {
						put(b, x, y, z, log(trim, "y"));
					} else if (side) {
						boolean window = y == 1 && z == (1 + back) / 2;
						if (window) {
							put(b, x, y, z, glass ? pane(false) : fence(wood, true, false, true, false));
						} else {
							put(b, x, y, z, cobbleBase && y == 0 ? s("cobblestone") : planks(wood));
						}
					} else if (z == back || (z == 1 && y == height - 1)) {
						put(b, x, y, z, cobbleBase && y == 0 ? s("cobblestone") : planks(wood));
					} else {
						b.air(x, y, z);
					}
				}
			}
		}
		// tejado a dos aguas con alero delante y detrás
		for (int k = 0; k <= a; k++) {
			int xs = a + 1 - k;
			int y = height - 1 + k;
			for (int z = 0; z <= back + 1; z++) {
				put(b, -xs, y, z, stairs(roofWood, "east"));
				put(b, xs, y, z, stairs(roofWood, "west"));
			}
		}
		for (int z = 0; z <= back + 1; z++) {
			put(b, 0, height + a, z, slab(roofWood));
		}
		for (int j = 0; j < a; j++) {
			for (int x = -(a - 1 - j); x <= a - 1 - j; x++) {
				put(b, x, height + j, 1, planks(wood));
				put(b, x, height + j, back, planks(wood));
				for (int z = 2; z < back; z++) {
					b.air(x, height + j, z);
				}
			}
		}
		// farol colgado del caballete y antorchas a los lados del portón
		put(b, 0, height + a - 1, 3, s("lantern", "hanging", "true"), Blueprint.LATE);
		put(b, -1, 1, back - 1, wallTorch("north"), Blueprint.LATE);
		put(b, 1, 1, back - 1, wallTorch("north"), Blueprint.LATE);
	}

	/** Cobertizo abierto del excavador (el pozo empieza detrás, en z = 2). */
	static void diggerShed(Blueprint b, Random r) {
		String wood = pick(r, WOODS);
		String trim = pick(r, "spruce", "dark_oak", "oak", wood);
		boolean stairsRoof = r.nextBoolean();
		boolean halfWall = r.nextBoolean();
		for (int x = -2; x <= 2; x++) {
			for (int z = -1; z <= 1; z++) {
				put(b, x, -1, z, s(r.nextInt(4) == 0 ? "gravel" : "coarse_dirt"), Blueprint.FOUNDATION);
				for (int y = 0; y < 3; y++) {
					b.air(x, y, z);
				}
			}
		}
		for (int x : new int[] {-2, 2}) {
			for (int z : new int[] {-1, 1}) {
				for (int y = 0; y < 3; y++) {
					put(b, x, y, z, log(trim, "y"));
				}
			}
			if (halfWall) {
				put(b, x, 0, 0, planks(wood));
			}
		}
		for (int x = -3; x <= 3; x++) {
			for (int z = -2; z <= 1; z++) {
				if (stairsRoof && (z == -2)) {
					put(b, x, 3, z, stairs(wood, "south"));
				} else {
					put(b, x, 3, z, slab(wood));
				}
			}
		}
		put(b, -1, 0, 1, s("barrel", "facing", "up"));
		put(b, 1, 0, 1, s("crafting_table"));
		put(b, 0, 2, 0, s("lantern", "hanging", "true"), Blueprint.LATE);
	}

	/** Caseta del pescador, con barriles y un farol en un poste. */
	static void fishingHut(Blueprint b, Random r) {
		House h = randomHouse(r, "spruce", "oak", "birch", "dark_oak");
		h.half = 2;
		h.back = 2 + r.nextInt(2);
		h.height = 3;
		h.roofAlongZ = h.back % 2 == 0 || r.nextBoolean();
		house(b, h);
		furnish(b, h, r, s("barrel", "facing", "up"));
		int side = -h.doorX;
		ground(b, side * (h.half + 1), 0, -1, s("barrel", "facing", "up"));
		ground(b, side * (h.half + 2), 0, 0, s("barrel", "facing", "north"));
		int px = h.doorX * (h.half + 2);
		ground(b, px, 0, -1, fence(h.wood, false, false, false, false));
		put(b, px, 1, -1, s("lantern", "hanging", "false"), Blueprint.LATE);
	}

	/** Establo y cercado: el cercado rodea la zona de trabajo y se entra a él por la casa. */
	static void stable(Blueprint b, Random r) {
		House h = randomHouse(r, "spruce", "oak", "dark_oak");
		h.half = 3;
		house(b, h);
		furnish(b, h, r, s("hay_block", "axis", "y"), s("hay_block", "axis", "x"), s("barrel", "facing", "up"));
		pen(b, h, h.trim);
	}

	/** Gallinero y corral. */
	static void coop(Blueprint b, Random r) {
		House h = randomHouse(r, "oak", "birch", "spruce", "jungle");
		h.half = 2;
		h.back = 3 + r.nextInt(2);
		house(b, h);
		furnish(b, h, r, s("hay_block", "axis", "y"), s("composter"));
		// franja de lana de color bajo el alero
		String color = pick(r, "red", "yellow", "white", "orange");
		for (int z = 0; z < h.back; z++) {
			if (b.get(-h.half, h.height - 1, z) != null) {
				put(b, -h.half, h.height - 1, z, s(color + "_wool"));
				put(b, h.half, h.height - 1, z, s(color + "_wool"));
			}
		}
		pen(b, h, h.wood);
	}

	/** Colmenar: casita con toldo de lana amarilla y jardineras. */
	static void apiary(Blueprint b, Random r) {
		House h = randomHouse(r, "birch", "oak", "spruce", "jungle");
		h.half = 2;
		house(b, h);
		furnish(b, h, r, s("barrel", "facing", "up"), s("crafting_table"));
		// tejadillo de trampillas sobre la puerta y franja de lana bajo el alero
		for (int dx = -1; dx <= 1; dx++) {
			put(b, h.doorX + dx, 2, -2, s(h.wood + "_trapdoor", "facing", "north", "half", "top", "open", "false"));
		}
		String color = pick(r, "yellow", "orange");
		for (int z = 0; z < h.back; z++) {
			put(b, -h.half, h.height - 1, z, s(color + "_wool"));
			put(b, h.half, h.height - 1, z, s(color + "_wool"));
		}
		// jardineras a los lados
		for (int side : new int[] {-1, 1}) {
			int x = side * (h.half + 1);
			for (int z = 0; z <= 1; z++) {
				ground(b, x, 0, z, s("coarse_dirt"));
				put(b, x + side, 0, z, s(h.wood + "_trapdoor", "facing", side > 0 ? "east" : "west", "half", "bottom", "open", "true"));
			}
		}
	}

	/** Invernadero: armazón de troncos, paredes y techo de cristal, bancales de tierra dentro. */
	static void greenhouse(Blueprint b, Random r) {
		String wood = pick(r, "oak", "spruce", "birch", "dark_oak", "jungle");
		int a = r.nextBoolean() ? 3 : 2;
		int back = 4 + r.nextInt(3);
		int height = 3;
		int doorX = r.nextBoolean() ? 1 : -1;
		for (int x = -a; x <= a; x++) {
			for (int z = -1; z <= back; z++) {
				boolean bed = Math.abs(x) == a - 1 && z >= 1 && z <= back - 1;
				put(b, x, -1, z, bed ? s("dirt") : s(x == 0 || Math.abs(x) == a || z == -1 || z == back ? "stone_bricks" : "gravel"),
						Blueprint.FOUNDATION);
				for (int y = 0; y < height; y++) {
					boolean side = Math.abs(x) == a;
					boolean fb = z == -1 || z == back;
					boolean corner = side && fb;
					if (corner) {
						put(b, x, y, z, log(wood, "y"));
					} else if (side) {
						put(b, x, y, z, y == 0 ? planks(wood) : pane(false));
					} else if (fb) {
						put(b, x, y, z, y == 0 ? planks(wood) : pane(true));
					} else {
						b.air(x, y, z);
					}
				}
			}
		}
		b.set(doorX, 0, -1, s(wood + "_door", "facing", "south", "half", "lower", "hinge", "left", "open", "false"), Material.PLANKS);
		b.set(doorX, 1, -1, s(wood + "_door", "facing", "south", "half", "upper", "hinge", "left", "open", "false"),
				Material.FREE, Blueprint.UPPER_HALF);
		b.air(doorX, 0, -2);
		b.air(doorX, 1, -2);
		// techo de cristal escalonado con vigas de troncos en los bordes
		for (int k = 0; k <= a; k++) {
			int y = height + k;
			for (int z = -1; z <= back; z++) {
				boolean beam = z == -1 || z == back;
				for (int x : new int[] {-(a - k), a - k}) {
					put(b, x, y, z, beam ? log(wood, "z") : s("glass"));
				}
			}
		}
		for (int x = -a + 1; x <= a - 1; x++) {
			for (int z = 0; z < back; z++) {
				for (int k = 0; k < a; k++) {
					if (Math.abs(x) < a - k) {
						b.air(x, height + k, z);
					}
				}
			}
		}
		// bancales: tierra con un borde de troncos ya está en el suelo; antorchas en la pared del fondo
		put(b, -1, 1, back - 1, wallTorch("north"), Blueprint.LATE);
		put(b, 1, 1, back - 1, wallTorch("north"), Blueprint.LATE);
		put(b, -(a - 1), 0, back - 1, s("composter"));
	}

	/** Chimenea de piedra de 3x3 con una hoguera encendida dentro (hogar abierto hacia el interior). */
	static void chimney(Blueprint b, int cx, int cz, int top, String openTo) {
		for (int y = 0; y <= top; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = cx + dx;
					int z = cz + dz;
					boolean center = dx == 0 && dz == 0;
					boolean mouth = y <= 1 && switch (openTo) {
						case "north" -> dz == -1 && dx == 0;
						case "east" -> dx == 1 && dz == 0;
						case "west" -> dx == -1 && dz == 0;
						default -> false;
					};
					if (center) {
						if (y == 0) {
							put(b, x, y, z, s("campfire", "lit", "true", "facing", "south", "signal_fire", "false"));
						} else {
							b.remove(x, y, z);
							b.air(x, y, z);
						}
					} else if (mouth) {
						b.remove(x, y, z);
						b.air(x, y, z);
					} else {
						put(b, x, y, z, s(y == 0 ? "cobblestone" : "stone_bricks"));
					}
				}
			}
		}
	}

	/** Forja: taller de piedra abierto por delante, con chimenea y hogar. */
	static void forge(Blueprint b, Random r) {
		House h = randomHouse(r, "dark_oak", "spruce", "oak");
		h.walls = r.nextInt(3) == 0 ? Walls.COBBLE_BASE : Walls.STONE;
		h.half = 3;
		h.back = 4 + r.nextInt(2);
		h.height = 3 + r.nextInt(2);
		h.stoneFloor = true;
		h.openFront = r.nextBoolean();
		h.roofAlongZ = true;
		house(b, h);
		int cx = r.nextBoolean() ? 2 : -2;
		chimney(b, cx, h.back, h.roofTop(), "north");
		furnish(b, h, r, s("barrel", "facing", "up"), s("crafting_table"), s("smooth_stone_slab", "type", "double"));
	}

	/** Cocina: casa con chimenea y hogar, barriles y mesa. */
	static void kitchen(Blueprint b, Random r) {
		House h = randomHouse(r, "oak", "spruce", "birch", "dark_oak");
		h.half = 3;
		h.back = 4 + r.nextInt(2);
		h.roofAlongZ = true;
		if (h.walls == Walls.LOGS) {
			h.walls = Walls.COBBLE_BASE;
		}
		house(b, h);
		int cx = -h.doorX * 2;
		chimney(b, cx, h.back, h.roofTop(), "north");
		furnish(b, h, r, s("barrel", "facing", "up"), s("barrel", "facing", "up"), s("crafting_table"));
	}

	/** Carbonera: horno de cúpula de piedra con una hoguera dentro, tejadillo sobre el puesto y troncos apilados. */
	static void kiln(Blueprint b, Random r) {
		String wood = pick(r, "spruce", "dark_oak", "oak", "jungle");
		int side = r.nextBoolean() ? 1 : -1;
		boolean stairsRoof = r.nextBoolean();
		boolean backWall = r.nextBoolean();
		// tejadillo sobre el puesto
		for (int x = -3; x <= 3; x++) {
			for (int z = -1; z <= 1; z++) {
				put(b, x, -1, z, s("coarse_dirt"), Blueprint.FOUNDATION);
			}
		}
		for (int x : new int[] {-2, 2}) {
			for (int z : new int[] {-1, 1}) {
				for (int y = 0; y < 3; y++) {
					put(b, x, y, z, log(wood, "y"));
				}
			}
		}
		if (backWall) {
			for (int x = -1; x <= 1; x++) {
				for (int y = 0; y < 3; y++) {
					put(b, x, y, 1, planks(wood));
				}
			}
		}
		for (int x = -3; x <= 3; x++) {
			for (int z = -2; z <= 2; z++) {
				put(b, x, 3, z, stairsRoof && z == -2 ? stairs(wood, "south") : stairsRoof && z == 2 ? stairs(wood, "north") : slab(wood));
			}
			for (int z = -1; z <= 1; z++) {
				for (int y = 0; y < 3; y++) {
					b.air(x, y, z);
				}
			}
		}
		put(b, -side, 0, 0, s("barrel", "facing", "up"));
		put(b, 0, 2, -1, s("lantern", "hanging", "true"), Blueprint.LATE);
		// cúpula al lado: anillos cada vez más pequeños y un agujero arriba para el humo
		int radius = r.nextBoolean() ? 3 : 2;
		int cx = side * (radius + 4);
		int cz = r.nextInt(2);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int d = Math.max(Math.abs(dx), Math.abs(dz));
				boolean rounded = Math.abs(dx) == radius && Math.abs(dz) == radius;
				if (!rounded) {
					put(b, cx + dx, -1, cz + dz, s("cobblestone"), Blueprint.FOUNDATION);
				}
				for (int y = 0; y < radius; y++) {
					int ring = radius - y;
					boolean corner = Math.abs(dx) == ring && Math.abs(dz) == ring && ring >= 2;
					if (d == ring && !corner) {
						put(b, cx + dx, y, cz + dz, y == radius - 1 ? s("cobblestone_slab", "type", "bottom") : s("cobblestone"));
					} else if (d < ring) {
						b.air(cx + dx, y, cz + dz);
					}
				}
			}
		}
		put(b, cx, 0, cz, s("campfire", "lit", "true", "facing", "south", "signal_fire", "false"));
		for (int y = 1; y <= radius; y++) {
			b.air(cx, y, cz);
		}
		// pila de troncos detrás del tejadillo
		int length = 2 + r.nextInt(3);
		for (int i = 0; i < length; i++) {
			int x = side * (1 + i);
			ground(b, x, 0, 3, log(wood, "x"));
			if (i < length - 1) {
				put(b, x, 1, 3, log(wood, "x"));
			}
		}
	}

	/** Taller de cantería: caseta de piedra y patio con bloques apilados. */
	static void masonry(Blueprint b, Random r) {
		House h = randomHouse(r, "spruce", "dark_oak", "oak");
		h.walls = r.nextBoolean() ? Walls.STONE : Walls.COBBLE_BASE;
		h.half = 2;
		h.stoneFloor = true;
		h.openFront = r.nextBoolean();
		house(b, h);
		furnish(b, h, r, s("crafting_table"), s("barrel", "facing", "up"));
		int side = -h.doorX;
		int x0 = side * (h.half + 2);
		String[] stacks = {"cobblestone", "stone_bricks", "stone", "andesite"};
		for (int z = 0; z <= 2; z++) {
			ground(b, x0, 0, z, s(stacks[r.nextInt(stacks.length)]));
			if (r.nextBoolean()) {
				put(b, x0, 1, z, s(stacks[r.nextInt(stacks.length)]));
			}
			ground(b, x0 + side, 0, z, s(stacks[r.nextInt(stacks.length)]));
		}
		ground(b, x0, 0, -1, s("stone_brick_slab", "type", "double"));
	}

	/** Taller del flechero: casa de madera con dianas de paja. */
	static void fletchery(Blueprint b, Random r) {
		House h = randomHouse(r, "birch", "oak", "spruce", "jungle", "acacia");
		h.half = 2;
		house(b, h);
		furnish(b, h, r, s("crafting_table"), s("barrel", "facing", "up"));
		int side = -h.doorX;
		int x = side * (h.half + 2);
		for (int z : new int[] {0, 2}) {
			ground(b, x, 0, z, fence(h.wood, false, false, false, false));
			put(b, x, 1, z, s("hay_block", "axis", "x"));
			put(b, x, 2, z, s("red_wool"));
		}
	}
}

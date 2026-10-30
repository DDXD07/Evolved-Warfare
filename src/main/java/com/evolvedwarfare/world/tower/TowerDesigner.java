package com.evolvedwarfare.world.tower;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Generador de torres de vigilancia (Java puro): cada semilla da una torre distinta.
 *
 * <ul>
 *   <li><b>Forma</b>: cuadrada (con esquinas de sillar y, a veces, contrafuertes), octogonal o redonda; de 7, 9 o (rara
 *   vez) 11 bloques de ancho.</li>
 *   <li><b>Altura</b>: de 2 a 4 pisos de 4 o 5 bloques, con talud en la base a veces e impostas entre pisos.</li>
 *   <li><b>Remate</b>: almenas con banderas y mástil, almenas con hoguera de señales, mirador abierto con tejado y
 *   campana de alarma, o garita de entramado con tejado. A veces, con voladizo sobre ménsulas y matacanes, y pináculos
 *   en las almenas.</li>
 *   <li><b>Muros</b>: musgo abajo y grietas arriba (más cuanto más vieja), alféizares bajo las saeteras.</li>
 *   <li><b>Dentro</b>: puerta, escalera de mano y trampilla arriba; abajo el cuerpo de guardia (cofre de armas,
 *   banco de trabajo, horno, barriles...), encima la cuadra de literas (camas, cofre, alfombra) y más arriba la
 *   armería o la sala de mapas. Saeteras en cada piso y faroles colgados.</li>
 *   <li><b>Fuera</b>: a veces una cerca (valla, empalizada o murete) con portón y camino; y algunos de: almiar, leñera,
 *   hoguera con bancos, dianas, muñeco de entrenamiento, cobertizo, cajas y pozo; a veces una cuadra adosada (heno y
 *   abrevadero) y farolas en el camino. Hiedra en los muros viejos.</li>
 *   <li><b>Materiales</b> según el bioma: piedra o mampostería en llanuras y bosques, mampostería y abeto (con
 *   nieve) en las taigas, adobe o arenisca y acacia en la sabana, pizarra profunda o toba en las montañas y
 *   costas.</li>
 * </ul>
 */
public final class TowerDesigner {
	public enum Style {
		TEMPERATE, BOREAL, SAVANNA, ROCKY
	}

	enum Shape {
		SQUARE, OCTAGON, ROUND
	}

	enum Top {
		PARAPET, BRAZIER, ROOF, LOOKOUT
	}

	private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	private static final String[] DIR_NAMES = {"north", "east", "south", "west"};
	private static final String[] BANNERS = {"red", "blue", "yellow", "green", "white", "black", "orange", "purple",
			"light_blue", "cyan"};

	public static final String LOOT_GUARD = "minecraft:chests/village/village_weaponsmith";
	public static final String LOOT_ARMOR = "minecraft:chests/village/village_armorer";
	public static final String LOOT_HOUSE = "minecraft:chests/village/village_plains_house";
	public static final String LOOT_TOOLS = "minecraft:chests/village/village_toolsmith";

	/** Los bloques de mampostería de un juego (muro, escaleras, losa, murete, esquinas, zócalo y su escalera). */
	private record Masonry(String name, String wall, String[] weather, String stairs, String slab, String wallBlock, String accent,
			String plinth, String plinthStairs) {
	}

	private static final Masonry STONE_BRICK = new Masonry("piedra", "stone_bricks",
			new String[] {"mossy_stone_bricks", "cracked_stone_bricks"}, "stone_brick_stairs", "stone_brick_slab",
			"stone_brick_wall", "polished_andesite", "cobblestone", "cobblestone_stairs");
	private static final Masonry COBBLE = new Masonry("mampostería", "cobblestone", new String[] {"mossy_cobblestone", "stone"},
			"cobblestone_stairs", "cobblestone_slab", "cobblestone_wall", "stone_bricks", "mossy_cobblestone", "mossy_cobblestone_stairs");
	private static final Masonry MUD = new Masonry("adobe", "mud_bricks", new String[] {"packed_mud"}, "mud_brick_stairs",
			"mud_brick_slab", "mud_brick_wall", "stripped_acacia_log", "cobblestone", "cobblestone_stairs");
	private static final Masonry SANDSTONE = new Masonry("arenisca", "smooth_sandstone", new String[] {"sandstone", "cut_sandstone"},
			"smooth_sandstone_stairs", "smooth_sandstone_slab", "sandstone_wall", "chiseled_sandstone", "sandstone", "sandstone_stairs");
	private static final Masonry DEEPSLATE = new Masonry("pizarra", "cobbled_deepslate",
			new String[] {"deepslate_bricks", "cracked_deepslate_bricks"}, "cobbled_deepslate_stairs", "cobbled_deepslate_slab",
			"cobbled_deepslate_wall", "polished_deepslate", "deepslate_bricks", "deepslate_brick_stairs");
	private static final Masonry TUFF = new Masonry("toba", "tuff_bricks", new String[] {"polished_tuff", "tuff"}, "tuff_brick_stairs",
			"tuff_brick_slab", "tuff_brick_wall", "chiseled_tuff_bricks", "cobbled_deepslate", "cobbled_deepslate_stairs");

	private final Random rnd;
	private final Style style;
	private final boolean snowy;
	private final TowerPlan plan = new TowerPlan();
	/** Huecos de dentro que no se amueblan (la salida al balcón). */
	private final java.util.Set<Integer> reserved = new java.util.HashSet<>();

	private Masonry m;
	private String wood;
	private String roofStairs;
	private String roofBlock;
	private String infill;
	private String banner;
	private String window;
	private Shape shape;
	private Top top;
	/** Radio del muro (la torre mide 2r + 1). */
	private int r;
	private int stories;
	private int storyHeight;
	/** Altura de la plataforma de arriba. */
	private int deck;
	private int overhang;
	private boolean battered;
	private boolean courses;
	/** Contrafuertes en las esquinas (solo las cuadradas). */
	private boolean buttresses;
	/** Pináculos en las almenas. */
	private boolean pinnacles;
	private double weathering;
	/** Radio del remate (muro + voladizo). */
	private int crown;

	private TowerDesigner(long seed, Style style, boolean snowy) {
		this.rnd = new Random(seed);
		this.style = style;
		this.snowy = snowy;
	}

	public static TowerPlan design(long seed, Style style, boolean snowy) {
		TowerDesigner d = new TowerDesigner(seed, style, snowy);
		d.build();
		return d.plan;
	}

	// ------------------------------------------------------------------ elección

	private <T> T pick(T[] options) {
		return options[rnd.nextInt(options.length)];
	}

	private boolean chance(double p) {
		return rnd.nextDouble() < p;
	}

	private void choose() {
		switch (style) {
			case TEMPERATE -> {
				m = chance(0.55) ? STONE_BRICK : COBBLE;
				wood = pick(new String[] {"spruce", "oak", "dark_oak", "spruce"});
				infill = pick(new String[] {"packed_mud", "white_terracotta", "stripped_birch_log"});
			}
			case BOREAL -> {
				m = chance(0.6) ? COBBLE : STONE_BRICK;
				wood = chance(0.8) ? "spruce" : "dark_oak";
				infill = pick(new String[] {"spruce_planks", "stripped_spruce_log", "packed_mud"});
			}
			case SAVANNA -> {
				m = chance(0.6) ? MUD : SANDSTONE;
				wood = "acacia";
				infill = pick(new String[] {"white_terracotta", "packed_mud", "stripped_acacia_log"});
			}
			case ROCKY -> {
				double p = rnd.nextDouble();
				m = p < 0.5 ? DEEPSLATE : p < 0.75 ? TUFF : STONE_BRICK;
				wood = chance(0.5) ? "dark_oak" : "spruce";
				infill = pick(new String[] {"spruce_planks", "packed_mud"});
			}
		}
		double roof = rnd.nextDouble();
		if (style == Style.ROCKY || (style == Style.TEMPERATE && roof < 0.3)) {
			roofStairs = "deepslate_tile_stairs";
			roofBlock = "deepslate_tiles";
		} else if (style == Style.TEMPERATE && roof < 0.45) {
			roofStairs = "brick_stairs";
			roofBlock = "bricks";
		} else {
			String w = style == Style.SAVANNA ? "acacia" : (roof < 0.75 ? "dark_oak" : wood);
			roofStairs = w + "_stairs";
			roofBlock = w + "_planks";
		}
		banner = pick(BANNERS);
		window = pick(new String[] {"air", "air", "iron_bars", "glass_pane"});
		double s = rnd.nextDouble();
		shape = s < 0.45 ? Shape.SQUARE : s < 0.72 ? Shape.OCTAGON : Shape.ROUND;
		r = chance(0.6) ? 3 : 4;
		if (shape != Shape.ROUND && chance(0.12)) {
			r = 5;
		}
		stories = rnd.nextDouble() < 0.3 ? 2 : rnd.nextDouble() < 0.7 ? 3 : 4;
		storyHeight = chance(0.5) ? 4 : 5;
		deck = stories * storyHeight;
		Top[] tops = Top.values();
		top = tops[rnd.nextInt(tops.length)];
		overhang = top == Top.LOOKOUT || chance(0.55) ? 1 : 0;
		crown = r + overhang;
		battered = chance(0.5);
		courses = chance(0.5);
		buttresses = shape == Shape.SQUARE && chance(0.5);
		pinnacles = chance(0.5);
		weathering = style == Style.SAVANNA ? 0.08 : 0.12 + rnd.nextDouble() * 0.18;
		String[] shapeNames = {"cuadrada", "octogonal", "redonda"};
		String[] topNames = {"almenas", "hoguera de señales", "mirador con tejado", "garita de entramado"};
		plan.description = String.format("%s %dx%d, %d pisos, %s, %s y %s%s", shapeNames[shape.ordinal()], 2 * r + 1, 2 * r + 1,
				stories, topNames[top.ordinal()], m.name(), wood.replace('_', ' '), overhang > 0 ? ", con voladizo" : "")
				+ (buttresses ? ", contrafuertes" : "");
	}

	// ------------------------------------------------------------------ forma

	private boolean in(int x, int z, int rad) {
		if (rad < 0) {
			return false;
		}
		int ax = Math.abs(x);
		int az = Math.abs(z);
		return switch (shape) {
			case SQUARE -> ax <= rad && az <= rad;
			case OCTAGON -> ax <= rad && az <= rad && ax + az <= rad + rad / 2;
			case ROUND -> x * x + z * z <= rad * rad + rad;
		};
	}

	private boolean ring(int x, int z, int rad) {
		return in(x, z, rad) && !in(x, z, rad - 1);
	}

	/** Hacia fuera desde el centro (el eje que más pesa): 0 norte, 1 este, 2 sur, 3 oeste. */
	private static int outward(int x, int z) {
		if (Math.abs(x) >= Math.abs(z)) {
			return x >= 0 ? 1 : 3;
		}
		return z >= 0 ? 2 : 0;
	}

	private static int opposite(int dir) {
		return (dir + 2) % 4;
	}

	private String masonry() {
		return chance(weathering) ? pick(m.weather()) : m.wall();
	}

	/** El muro a esa altura: el primer bloque de desgaste (musgo) abunda abajo y el segundo (grietas) arriba. */
	private String masonry(int y) {
		String[] w = m.weather();
		double low = Math.max(0.0, 1.0 - y / 6.0);
		double high = Math.min(1.0, Math.max(0.0, (y - deck * 0.6) / (deck * 0.4 + 1.0)));
		if (chance(weathering * (0.5 + low * 1.6))) {
			return w[0];
		}
		if (w.length > 1 && chance(weathering * (0.3 + high * 1.4))) {
			return w[1];
		}
		return chance(0.015) ? m.accent() + (m.accent().endsWith("_log") ? "[axis=y]" : "") : m.wall();
	}

	private String log(String axis) {
		return wood + "_log[axis=" + axis + "]";
	}

	private boolean isCorner(int x, int z, int rad) {
		return shape == Shape.SQUARE && Math.abs(x) == rad && Math.abs(z) == rad;
	}

	// ------------------------------------------------------------------ construcción

	private void build() {
		choose();
		int yard = r + 4 + rnd.nextInt(2);
		boolean fenced = chance(0.55);
		ground(yard, fenced);
		base();
		shaft();
		floors();
		ladderAndDoor();
		windows();
		deckAndCrown();
		switch (top) {
			case PARAPET -> parapet(false);
			case BRAZIER -> parapet(true);
			case ROOF -> lookout(false);
			case LOOKOUT -> lookout(true);
		}
		banners();
		awning();
		boolean stable = chance(0.35) && stable();
		sills();
		if (buttresses) {
			buttresses();
		}
		if (overhang > 0) {
			machicolations();
		}
		if (pinnacles && (top == Top.PARAPET || top == Top.BRAZIER)) {
			pinnacles();
		}
		if (stories >= 3 && chance(0.45)) {
			balcony();
		}
		if (style != Style.SAVANNA && chance(0.6)) {
			vines();
		}
		for (int s = 0; s < stories; s++) {
			furnish(s);
		}
		if (fenced) {
			fence(yard);
		}
		if (chance(0.5)) {
			lampPosts(yard, fenced);
		}
		extras(yard);
		if (stable) {
			plan.description += ", cuadra";
		}
		guards();
	}

	/** El patio: camino hasta la puerta y algo de grava y tierra pisada. */
	private void ground(int yard, boolean fenced) {
		int reach = fenced ? yard : yard - 1;
		for (int z = -reach - 1; z <= -r - 1; z++) {
			plan.set(0, 0, z, "dirt_path", TowerPlan.GROUND);
			if (chance(0.35)) {
				plan.set(chance(0.5) ? 1 : -1, 0, z, "dirt_path", TowerPlan.GROUND);
			}
		}
		String[] wear = style == Style.SAVANNA ? new String[] {"coarse_dirt", "packed_mud", "coarse_dirt"}
				: new String[] {"coarse_dirt", "gravel", "dirt_path"};
		for (int x = -yard + 1; x < yard; x++) {
			for (int z = -yard + 1; z < yard; z++) {
				if (!in(x, z, r + 1) && !plan.has(x, 0, z) && chance(0.16)) {
					plan.set(x, 0, z, pick(wear), TowerPlan.GROUND);
				}
			}
		}
	}

	/** Zócalo y, a veces, talud. */
	private void base() {
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				if (in(x, z, r)) {
					plan.set(x, 0, z, m.plinth(), TowerPlan.FOUNDATION);
				} else if (battered && in(x, z, r + 1)) {
					plan.set(x, 0, z, m.plinth(), TowerPlan.FOUNDATION);
					boolean door = x == 0 && z < 0;
					if (!door) {
						plan.set(x, 1, z, m.plinthStairs() + "[facing=" + DIR_NAMES[opposite(outward(x, z))] + ",half=bottom]");
					}
				}
			}
		}
	}

	private void shaft() {
		for (int y = 1; y < deck; y++) {
			boolean course = courses && y % storyHeight == 0;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (ring(x, z, r)) {
						String block;
						if (y <= 2) {
							block = m.plinth();
						} else if (isCorner(x, z, r)) {
							block = y % 2 == 0 ? m.accent() : m.wall();
						} else if (course) {
							block = m.accent();
						} else {
							block = masonry(y);
						}
						if (m.accent().endsWith("_log") && block.equals(m.accent())) {
							block += "[axis=y]";
						}
						plan.set(x, y, z, block);
					} else if (in(x, z, r - 1)) {
						plan.air(x, y, z);
					}
				}
			}
		}
	}

	private void floors() {
		for (int s = 1; s < stories; s++) {
			int y = s * storyHeight;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (in(x, z, r - 1) && !(x == 0 && z == r - 1)) {
						plan.set(x, y, z, wood + "_planks");
					}
				}
			}
			// farol colgado del techo de cada piso
			plan.set(-1, y - 1, -1, "lantern[hanging=true]");
		}
		plan.set(-1, deck - 1, -1, "lantern[hanging=true]");
	}

	private void ladderAndDoor() {
		for (int y = 1; y < deck; y++) {
			plan.set(0, y, r - 1, "ladder[facing=north]");
		}
		plan.set(0, deck, r - 1, wood + "_trapdoor[facing=north,half=top,open=false]");
		plan.set(0, 1, -r, wood + "_door[facing=north,half=lower,hinge=left,open=false]");
		plan.set(0, 2, -r, wood + "_door[facing=north,half=upper,hinge=left,open=false]");
		plan.set(0, 3, -r, m.stairs() + "[facing=south,half=top]");
		plan.set(0, 0, -r - 1, m.plinth(), TowerPlan.FOUNDATION);
		plan.air(0, 1, -r - 1);
		plan.air(0, 2, -r - 1);
		plan.air(0, 1, -r + 1);
		plan.air(0, 2, -r + 1);
		// luz a los lados de la puerta
		for (int side = -1; side <= 1; side += 2) {
			if (plan.solid(side, 3, -r)) {
				plan.set(side, 3, -r - 1, "wall_torch[facing=north]");
			}
		}
	}

	private void windows() {
		for (int s = 0; s < stories; s++) {
			int y0 = s * storyHeight;
			int[][] spots = {{0, -r}, {r, 0}, {-r, 0}};
			for (int[] spot : spots) {
				if (s == 0 && spot[1] == -r) {
					continue;
				}
				plan.set(spot[0], y0 + 2, spot[1], window);
				if (storyHeight == 5) {
					plan.set(spot[0], y0 + 3, spot[1], window);
				}
			}
			// las cuadradas anchas, saeteras dobles en pisos alternos
			if (shape == Shape.SQUARE && r >= 4 && s % 2 == 1) {
				for (int side = -2; side <= 2; side += 4) {
					plan.set(side, y0 + 2, -r, window);
					plan.set(r, y0 + 2, side, window);
					plan.set(-r, y0 + 2, side, window);
				}
			}
		}
	}

	/** La plataforma de arriba, con ménsulas si vuela. */
	private void deckAndCrown() {
		for (int x = -crown; x <= crown; x++) {
			for (int z = -crown; z <= crown; z++) {
				if (!in(x, z, crown)) {
					continue;
				}
				if (x == 0 && z == r - 1) {
					continue; // la trampilla
				}
				plan.set(x, deck, z, in(x, z, r - 1) ? wood + "_planks" : m.wall());
				if (overhang > 0 && ring(x, z, crown)) {
					plan.set(x, deck - 1, z, m.stairs() + "[facing=" + DIR_NAMES[opposite(outward(x, z))] + ",half=top]");
				}
			}
		}
		if (courses || overhang > 0) {
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (ring(x, z, r)) {
						plan.set(x, deck - 1, z, m.accent().endsWith("_log") ? m.accent() + "[axis=y]" : m.accent());
					}
				}
			}
		}
	}

	private void parapet(boolean brazier) {
		int y = deck + 1;
		for (int x = -crown; x <= crown; x++) {
			for (int z = -crown; z <= crown; z++) {
				if (!ring(x, z, crown)) {
					continue;
				}
				if (brazier) {
					plan.set(x, y, z, m.wallBlock());
				} else {
					plan.set(x, y, z, masonry());
					if (((x + z) & 1) == 0) {
						plan.set(x, y + 1, z, masonry());
					}
				}
			}
		}
		if (brazier) {
			// hoguera de señales en un pedestal
			plan.set(0, y, 0, m.accent().endsWith("_log") ? m.accent() + "[axis=y]" : m.accent());
			plan.set(0, y + 1, 0, chance(0.25) ? "soul_campfire[lit=true]" : "campfire[lit=true]");
			for (int[] d : DIRS) {
				plan.set(d[0], y, d[1], m.stairs() + "[facing=" + DIR_NAMES[opposite(outward(d[0], d[1]))] + ",half=bottom]");
			}
		} else {
			// faroles en algunas almenas (las esquinas, o los cuatro puntos cardinales si es redonda) y un mástil con la bandera
			int[][] corners = shape == Shape.SQUARE
					? new int[][] {{crown, crown}, {-crown, crown}, {crown, -crown}, {-crown, -crown}}
					: new int[][] {{crown, 0}, {-crown, 0}, {0, crown}, {0, -crown}};
			for (int[] c : corners) {
				int ty = plan.solid(c[0], y + 1, c[1]) ? y + 2 : y + 1;
				if (plan.solid(c[0], ty - 1, c[1]) && chance(0.7)) {
					plan.set(c[0], ty, c[1], "lantern");
				}
			}
			int px = -(r - 1);
			int pz = -(r - 1) + (shape == Shape.SQUARE ? 0 : 1);
			if (in(px, pz, r - 1)) {
				for (int k = 0; k < 4; k++) {
					plan.set(px, y + k, pz, wood + "_fence");
				}
				plan.set(px, y + 4, pz, banner + "_banner[rotation=" + rnd.nextInt(16) + "]");
			}
		}
		if (snowy) {
			for (int x = -crown; x <= crown; x++) {
				for (int z = -crown; z <= crown; z++) {
					if (in(x, z, crown) && !plan.has(x, y, z) && plan.solid(x, deck, z) && !plan.get(x, deck, z).contains("trapdoor")) {
						plan.set(x, y, z, "snow[layers=1]");
					}
				}
			}
		}
	}

	/** Mirador con tejado: abierto con barandilla y campana, o garita cerrada de entramado. */
	private void lookout(boolean closed) {
		int y = deck + 1;
		int wallTop = deck + (closed ? 3 : 3);
		List<int[]> posts = new ArrayList<>();
		for (int x = -crown; x <= crown; x++) {
			for (int z = -crown; z <= crown; z++) {
				if (!ring(x, z, crown)) {
					continue;
				}
				boolean post = shape == Shape.SQUARE ? (Math.abs(x) == crown && Math.abs(z) == crown)
						: ((x == 0 || z == 0) || (Math.abs(x) == Math.abs(z)));
				if (post) {
					posts.add(new int[] {x, z});
					for (int k = y; k <= wallTop; k++) {
						plan.set(x, k, z, log("y"));
					}
				} else if (closed) {
					boolean opening = (x == 0 || z == 0);
					for (int k = y; k <= wallTop; k++) {
						if (k == y + 1 && opening) {
							plan.set(x, k, z, "glass_pane".equals(window) ? "glass_pane" : wood + "_fence");
						} else if (k == wallTop) {
							plan.set(x, k, z, log(Math.abs(x) == crown ? "z" : "x"));
						} else {
							plan.set(x, k, z, infill.contains("log") ? infill + "[axis=y]" : infill);
						}
					}
				} else {
					plan.set(x, y, z, wood + "_fence");
				}
			}
		}
		// la viga de arriba que lo ata todo y el tejado
		int eaves = wallTop + 1;
		for (int x = -crown; x <= crown; x++) {
			for (int z = -crown; z <= crown; z++) {
				if (ring(x, z, crown)) {
					plan.set(x, eaves, z, log(Math.abs(x) >= Math.abs(z) ? "z" : "x"));
				}
			}
		}
		for (int x = -crown; x <= crown; x++) {
			plan.set(x, eaves, 0, log("x"));
		}
		for (int k = 0; ; k++) {
			int rad = crown + 1 - k;
			int yy = eaves + k;
			if (rad <= 0) {
				plan.set(0, yy, 0, roofBlock);
				plan.set(0, yy + 1, 0, chance(0.5) ? "lightning_rod" : wood + "_fence");
				if (chance(0.5)) {
					plan.set(0, yy + 2, 0, banner + "_banner[rotation=" + rnd.nextInt(16) + "]");
				}
				break;
			}
			for (int x = -rad; x <= rad; x++) {
				for (int z = -rad; z <= rad; z++) {
					if (!ring(x, z, rad) || (k == 0 && ring(x, z, crown))) {
						continue;
					}
					if ((shape == Shape.SQUARE && Math.abs(x) == Math.abs(z)) || (shape != Shape.SQUARE && Math.abs(x) == Math.abs(z)
							&& x != 0)) {
						plan.set(x, yy, z, roofBlock);
					} else {
						plan.set(x, yy, z, roofStairs + "[facing=" + DIR_NAMES[opposite(outward(x, z))] + ",half=bottom]");
					}
				}
			}
		}
		if (closed) {
			// mesa de mapas y un barril en la garita
			plan.set(-(r - 1), y, -(r - 1) + (shape == Shape.SQUARE ? 0 : 1), "cartography_table");
			plan.set(r - 1, y, -(r - 1) + (shape == Shape.SQUARE ? 0 : 1), "barrel[facing=up]");
		} else {
			plan.set(0, eaves - 1, 0, "bell[attachment=ceiling,facing=north]");
		}
		plan.set(1, eaves - 1, 0, "lantern[hanging=true]");
	}

	/** Un tejadillo sobre la puerta. */
	private void awning() {
		if (!chance(0.55)) {
			return;
		}
		for (int x = -1; x <= 1; x++) {
			if (plan.solid(x, 4, -r) && !plan.has(x, 4, -r - 1)) {
				plan.set(x, 4, -r - 1, roofStairs + "[facing=south,half=bottom]");
			}
		}
	}

	/** Un balcón de madera a un lado, en un piso alto, con su salida y barandilla. */
	private void balcony() {
		int story = 1 + rnd.nextInt(stories - 1);
		int y0 = story * storyHeight;
		int side = chance(0.5) ? 1 : -1;
		int wx = side * r;
		int bx = side * (r + 1);
		for (int z = -1; z <= 1; z++) {
			if (plan.has(bx, y0, z) || plan.has(bx, y0 + 1, z)) {
				return;
			}
		}
		for (int z = -1; z <= 1; z++) {
			plan.set(bx, y0, z, wood + "_slab[type=top]");
			plan.set(bx, y0 - 1, z, wood + "_stairs[facing=" + (side > 0 ? "west" : "east") + ",half=top]");
			plan.set(bx + side, y0 + 1, z, wood + "_fence");
		}
		plan.set(bx, y0 + 1, -2, wood + "_fence");
		plan.set(bx, y0 + 1, 2, wood + "_fence");
		plan.set(bx + side, y0 + 1, -2, wood + "_fence");
		plan.set(bx + side, y0 + 1, 2, wood + "_fence");
		// la salida: el hueco de la saetera se hace puerta
		plan.air(wx, y0 + 1, 0);
		plan.air(wx, y0 + 2, 0);
		plan.air(wx - side, y0 + 1, 0);
		plan.air(wx - side, y0 + 2, 0);
		reserved.add((y0 + 1) * 10000 + (wx - side + 50) * 100 + 50);
	}

	private void banners() {
		int y = deck - 2;
		int placed = 0;
		for (int side : new int[] {-2, 2, -1, 1}) {
			if (placed >= 2) {
				break;
			}
			if (plan.solid(side, y, -r) && !plan.has(side, y, -r - 1) && !plan.has(side, y - 1, -r - 1)) {
				plan.set(side, y, -r - 1, banner + "_wall_banner[facing=north]");
				placed++;
			}
		}
	}

	private void vines() {
		int strips = 2 + rnd.nextInt(4);
		for (int i = 0; i < strips; i++) {
			int dir = rnd.nextInt(4);
			int along = rnd.nextInt(2 * r - 1) - (r - 1);
			int x = dir % 2 == 0 ? along : (dir == 1 ? r : -r);
			int z = dir % 2 == 0 ? (dir == 2 ? r : -r) : along;
			while (in(x + DIRS[dir][0], z + DIRS[dir][1], r)) {
				x += DIRS[dir][0];
				z += DIRS[dir][1];
			}
			int vx = x + DIRS[dir][0];
			int vz = z + DIRS[dir][1];
			if (dir == 0 && Math.abs(vx) <= 1) {
				continue; // la puerta
			}
			int topY = deck - 2 - rnd.nextInt(3);
			int length = 3 + rnd.nextInt(Math.max(1, topY - 2));
			for (int y = topY; y > topY - length && y > 0; y--) {
				if (!plan.solid(x, y, z) || plan.has(vx, y, vz)) {
					break;
				}
				plan.set(vx, y, vz, "vine[" + DIR_NAMES[opposite(dir)] + "=true]");
			}
		}
	}

	/** Alféizar de piedra bajo cada saetera (por fuera), donde el muro es recto. */
	private void sills() {
		for (int s = 0; s < stories; s++) {
			int y = s * storyHeight + 1;
			for (int d = 1; d < 4; d++) {
				int wx = DIRS[d][0] * r;
				int wz = DIRS[d][1] * r;
				int ox = wx + DIRS[d][0];
				int oz = wz + DIRS[d][1];
				String hole = plan.get(wx, y + 1, wz);
				boolean opening = hole == null || hole.equals("minecraft:air") || hole.contains("bars") || hole.contains("pane");
				if (opening && plan.solid(wx, y, wz) && !plan.has(ox, y, oz) && !plan.has(ox, y + 1, oz)) {
					plan.set(ox, y, oz, m.stairs() + "[facing=" + DIR_NAMES[opposite(d)] + ",half=top]");
				}
			}
		}
	}

	/** Contrafuertes en las esquinas de las cuadradas: dos machones que se estrechan hacia arriba. */
	private void buttresses() {
		int height = Math.min(deck - 2, storyHeight + 1 + rnd.nextInt(Math.max(1, storyHeight)));
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				int[][] spots = {{sx * (r + 1), sz * r}, {sx * r, sz * (r + 1)}};
				for (int[] p : spots) {
					int face = Math.abs(p[0]) > r ? (p[0] > 0 ? 1 : 3) : (p[1] > 0 ? 2 : 0);
					plan.set(p[0], 0, p[1], m.plinth(), TowerPlan.FOUNDATION);
					for (int y = 1; y < height; y++) {
						plan.set(p[0], y, p[1], y <= 2 ? m.plinth() : masonry(y));
					}
					plan.set(p[0], height, p[1], m.stairs() + "[facing=" + DIR_NAMES[opposite(face)] + ",half=bottom]");
				}
			}
		}
	}

	/** Matacanes: bajo cada ménsula del voladizo, un remate que cuelga (alternos). */
	private void machicolations() {
		for (int x = -crown; x <= crown; x++) {
			for (int z = -crown; z <= crown; z++) {
				if (ring(x, z, crown) && ((x + z) & 1) == 0 && !plan.has(x, deck - 2, z)) {
					plan.set(x, deck - 2, z, m.wallBlock());
				}
			}
		}
	}

	/** Pináculos: algunas almenas suben dos más con un remate. */
	private void pinnacles() {
		int y = deck + 1;
		int[][] spots = shape == Shape.SQUARE
				? new int[][] {{crown, crown}, {-crown, crown}, {crown, -crown}, {-crown, -crown}}
				: new int[][] {{crown, 0}, {-crown, 0}, {0, crown}, {0, -crown}};
		String cap = m.accent().endsWith("_log") ? m.wallBlock() : m.accent();
		for (int[] p : spots) {
			if (!plan.solid(p[0], y, p[1])) {
				continue;
			}
			plan.set(p[0], y + 1, p[1], masonry(y + 1));
			plan.set(p[0], y + 2, p[1], cap);
			plan.set(p[0], y + 3, p[1], m.wallBlock());
			if (plan.get(p[0], y + 2, p[1]) != null && plan.has(p[0], y + 4, p[1])) {
				plan.remove(p[0], y + 4, p[1]);
			}
		}
	}

	/** Una cuadra adosada a un lado (este u oeste): postes, tejadillo a un agua, heno, abrevadero y valla. */
	private boolean stable() {
		int side = chance(0.5) ? 1 : -1;
		int x0 = side * (r + 1 + (battered ? 1 : 0));
		int depth = 3;
		// pegada a la parte recta del muro (en las redondas y octogonales es más corta)
		int half = shape == Shape.SQUARE ? Math.min(2, r - 1) : (r >= 4 ? 2 : 1);
		for (int z = -half - 1; z <= half + 1; z++) {
			for (int k = 0; k < depth; k++) {
				int x = x0 + side * k;
				for (int y = 1; y <= 4; y++) {
					if (plan.has(x, y, z)) {
						return false;
					}
				}
			}
		}
		for (int z = -half; z <= half; z++) {
			for (int k = 0; k < depth; k++) {
				int x = x0 + side * k;
				plan.set(x, 0, z, chance(0.5) ? "coarse_dirt" : "packed_mud", TowerPlan.FOUNDATION);
				// el tejadillo baja desde el muro
				int roofY = k == 0 ? 4 : 3;
				String facing = side > 0 ? "west" : "east";
				plan.set(x, roofY, z, roofStairs + "[facing=" + facing + ",half=bottom]");
			}
			int outer = x0 + side * (depth - 1);
			if (Math.abs(z) == half) {
				for (int y = 1; y <= 2; y++) {
					plan.set(outer, y, z, log("y"));
				}
			} else {
				plan.set(outer, 1, z, wood + "_fence");
			}
		}
		// heno, abrevadero y un comedero
		plan.set(x0, 1, -half, "hay_block[axis=y]");
		plan.set(x0, 2, -half, "hay_block[axis=z]");
		plan.set(x0, 1, half, "water_cauldron[level=3]");
		plan.set(x0 + side, 1, half, "composter[level=0]");
		plan.set(x0 + side, 3 - 1, 0, "lantern[hanging=true]");
		return true;
	}

	/** Farolas a los lados del camino, entre la puerta y el portón. */
	private void lampPosts(int yard, boolean fenced) {
		int z = -(r + 3 + rnd.nextInt(Math.max(1, yard - r - 4)));
		for (int side = -1; side <= 1; side += 2) {
			int x = side * 2;
			if (plan.has(x, 1, z) || plan.has(x, 2, z) || in(x, z, crown + 1)) {
				continue;
			}
			plan.set(x, 1, z, wood + "_fence");
			plan.set(x, 2, z, wood + "_fence");
			plan.set(x, 3, z, "lantern");
		}
	}

	// ------------------------------------------------------------------ dentro

	/** Huecos pegados al muro (para muebles) y hacia qué lado está el muro. */
	private List<int[]> slots(int story) {
		List<int[]> out = new ArrayList<>();
		for (int x = -r + 1; x <= r - 1; x++) {
			for (int z = -r + 1; z <= r - 1; z++) {
				if (!in(x, z, r - 1)) {
					continue;
				}
				if (x == 0 && (z == r - 1 || z == r - 2)) {
					continue; // la escalera de mano y su sitio delante
				}
				if (story == 0 && x == 0 && (z == -r + 1 || z == -r + 2)) {
					continue; // la entrada
				}
				if (x == -1 && z == -1) {
					continue; // bajo el farol
				}
				if (reserved.contains((story * storyHeight + 1) * 10000 + (x + 50) * 100 + (z + 50))) {
					continue; // la salida al balcón
				}
				for (int d = 0; d < 4; d++) {
					if (!in(x + DIRS[d][0], z + DIRS[d][1], r - 1)) {
						out.add(new int[] {x, z, d});
						break;
					}
				}
			}
		}
		Collections.shuffle(out, rnd);
		return out;
	}

	private void furnish(int story) {
		int y = story * storyHeight + 1;
		List<int[]> slots = slots(story);
		List<String> items = new ArrayList<>();
		String kind;
		if (story == 0) {
			kind = "guard";
			items.add("chest:" + (chance(0.5) ? LOOT_GUARD : LOOT_ARMOR));
			items.add("crafting_table");
			items.add(chance(0.5) ? "furnace" : "smoker");
			items.add("barrel");
			items.add(pick(new String[] {"water_cauldron[level=3]", "grindstone", "anvil", "barrel"}));
		} else if (story == 1 || (story == stories - 1 && stories > 2 && chance(0.3))) {
			kind = "barracks";
			items.add("bed");
			if (chance(0.6)) {
				items.add("bed");
			}
			items.add("chest:" + LOOT_HOUSE);
			items.add("barrel");
			if (chance(0.7)) {
				carpet(y);
			}
		} else if (chance(0.5)) {
			kind = "armory";
			items.add("fletching_table");
			items.add("smithing_table");
			items.add("barrel");
			items.add("chest:" + LOOT_TOOLS);
			items.add("hay_block");
		} else {
			kind = "maps";
			items.add("cartography_table");
			items.add("lectern");
			items.add("bookshelf");
			items.add("bookshelf");
			items.add("barrel");
		}
		int budget = Math.min(items.size(), Math.max(2, slots.size() / 2));
		int placed = 0;
		for (String item : items) {
			if (placed >= budget) {
				break;
			}
			for (int i = 0; i < slots.size(); i++) {
				int[] s = slots.get(i);
				if (plan.solid(s[0], y, s[1])) {
					continue;
				}
				if (place(item, s, y)) {
					slots.remove(i);
					placed++;
					break;
				}
			}
		}
	}

	private boolean place(String item, int[] slot, int y) {
		int x = slot[0];
		int z = slot[1];
		int wall = slot[2];
		String front = DIR_NAMES[opposite(wall)];
		if (item.equals("bed")) {
			int fx = x - DIRS[wall][0];
			int fz = z - DIRS[wall][1];
			if (!in(fx, fz, r - 1) || plan.solid(fx, y, fz) || (fx == 0 && fz >= r - 2) || (fx == 0 && fz <= -r + 2)) {
				return false;
			}
			String color = pick(new String[] {"red", "white", "brown", "gray", "light_gray", banner});
			plan.set(x, y, z, color + "_bed[facing=" + DIR_NAMES[wall] + ",part=head]");
			plan.set(fx, y, fz, color + "_bed[facing=" + DIR_NAMES[wall] + ",part=foot]");
			return true;
		}
		if (item.startsWith("chest:")) {
			plan.set(x, y, z, "chest[facing=" + front + ",type=single]");
			plan.loot(x, y, z, item.substring(6));
			return true;
		}
		switch (item) {
			case "furnace", "smoker" -> plan.set(x, y, z, item + "[facing=" + front + ",lit=false]");
			case "grindstone" -> plan.set(x, y, z, "grindstone[face=floor,facing=" + front + "]");
			case "anvil" -> plan.set(x, y, z, "anvil[facing=" + DIR_NAMES[(wall + 1) % 4] + "]");
			case "lectern" -> plan.set(x, y, z, "lectern[facing=" + front + ",has_book=false]");
			case "barrel" -> {
				plan.set(x, y, z, "barrel[facing=up]");
				if (chance(0.4)) {
					plan.set(x, y + 1, z, "barrel[facing=up]");
				}
			}
			case "hay_block" -> plan.set(x, y, z, "hay_block[axis=y]");
			case "bookshelf" -> {
				plan.set(x, y, z, "bookshelf");
				plan.set(x, y + 1, z, "bookshelf");
			}
			default -> plan.set(x, y, z, item);
		}
		return true;
	}

	private void carpet(int y) {
		String color = pick(new String[] {"red", "brown", "gray", "green", "blue", banner});
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 0; z++) {
				if (in(x, z, r - 2) && !plan.solid(x, y, z)) {
					plan.set(x, y, z, color + "_carpet");
				}
			}
		}
	}

	// ------------------------------------------------------------------ fuera

	private void fence(int yard) {
		String kind = switch (style) {
			case BOREAL -> "palisade";
			case ROCKY -> "wall";
			default -> chance(0.7) ? "fence" : "palisade";
		};
		for (int x = -yard; x <= yard; x++) {
			for (int z = -yard; z <= yard; z++) {
				boolean edge = Math.abs(x) == yard || Math.abs(z) == yard;
				if (!edge) {
					continue;
				}
				if (z == -yard && Math.abs(x) <= (kind.equals("fence") ? 0 : 1)) {
					continue; // el portón
				}
				plan.set(x, 0, z, chance(0.5) ? "coarse_dirt" : "dirt", TowerPlan.FOUNDATION);
				switch (kind) {
					case "palisade" -> {
						int height = 2 + ((x * 7 + z * 13) & 1);
						for (int k = 1; k <= height; k++) {
							plan.set(x, k, z, (chance(0.25) ? "stripped_" : "") + log("y"));
						}
					}
					case "wall" -> plan.set(x, 1, z, m.wallBlock());
					default -> plan.set(x, 1, z, wood + "_fence");
				}
			}
		}
		if (kind.equals("fence")) {
			plan.set(0, 1, -yard, wood + "_fence_gate[facing=north,open=false,in_wall=false]");
		} else {
			for (int side = -2; side <= 2; side += 4) {
				for (int k = 1; k <= 3; k++) {
					plan.set(side, k, -yard, log("y"));
				}
				plan.set(side, 4, -yard, "lantern");
			}
		}
	}

	private void extras(int yard) {
		List<int[]> anchors = new ArrayList<>();
		int near = r + 2 + (battered || overhang > 0 ? 1 : 0);
		int far = yard - 2;
		for (int a = near; a <= far; a += 2) {
			for (int b = -far; b <= far; b += 3) {
				anchors.add(new int[] {a, b});
				anchors.add(new int[] {-a, b});
				anchors.add(new int[] {b, a});
			}
		}
		Collections.shuffle(anchors, rnd);
		String[] kinds = {"haystack", "woodpile", "campfire", "target", "dummy", "shed", "crates", "well"};
		List<String> wanted = new ArrayList<>(List.of(kinds));
		Collections.shuffle(wanted, rnd);
		int count = 2 + rnd.nextInt(3);
		int done = 0;
		for (String kind : wanted) {
			if (done >= count) {
				break;
			}
			for (int i = 0; i < anchors.size(); i++) {
				int[] a = anchors.get(i);
				if (fits(a[0], a[1], kind, yard)) {
					feature(kind, a[0], a[1]);
					anchors.remove(i);
					done++;
					break;
				}
			}
		}
	}

	private static int[] footprint(String kind) {
		return switch (kind) {
			case "haystack", "crates" -> new int[] {2, 2};
			case "woodpile" -> new int[] {3, 1};
			case "campfire", "well" -> new int[] {5, 5};
			case "shed" -> new int[] {3, 2};
			default -> new int[] {1, 1};
		};
	}

	private boolean fits(int ax, int az, String kind, int yard) {
		int[] f = footprint(kind);
		int x0 = kind.equals("campfire") || kind.equals("well") ? ax - 2 : ax;
		int z0 = kind.equals("campfire") || kind.equals("well") ? az - 2 : az;
		for (int x = x0 - 1; x <= x0 + f[0]; x++) {
			for (int z = z0 - 1; z <= z0 + f[1]; z++) {
				if (Math.abs(x) >= yard || Math.abs(z) >= yard || in(x, z, crown + 1) || (Math.abs(x) <= 1 && z < 0)) {
					return false;
				}
				for (int y = 1; y <= 4; y++) {
					if (plan.has(x, y, z)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private void feature(String kind, int ax, int az) {
		switch (kind) {
			case "haystack" -> {
				plan.set(ax, 1, az, "hay_block[axis=y]");
				plan.set(ax + 1, 1, az, "hay_block[axis=x]");
				plan.set(ax, 1, az + 1, "hay_block[axis=z]");
				plan.set(ax, 2, az, "hay_block[axis=y]");
			}
			case "woodpile" -> {
				for (int i = 0; i < 3; i++) {
					plan.set(ax + i, 1, az, log("z"));
					if (i < 2 || chance(0.5)) {
						plan.set(ax + i, 2, az, log("z"));
					}
					plan.set(ax + i, 3, az, wood + "_slab[type=bottom]");
				}
			}
			case "campfire" -> {
				plan.set(ax, 1, az, "campfire[lit=true]");
				plan.set(ax - 2, 1, az, wood + "_stairs[facing=west,half=bottom]");
				plan.set(ax + 2, 1, az, wood + "_stairs[facing=east,half=bottom]");
				plan.set(ax, 1, az + 2, wood + "_stairs[facing=south,half=bottom]");
				plan.guard(ax, 1, az - 2, 0.0F);
			}
			case "target" -> {
				plan.set(ax, 1, az, "hay_block[axis=y]");
				plan.set(ax, 2, az, "target");
			}
			case "dummy" -> {
				plan.set(ax, 1, az, wood + "_fence");
				plan.set(ax, 2, az, "hay_block[axis=y]");
				plan.set(ax, 3, az, "carved_pumpkin[facing=" + DIR_NAMES[outward(-ax, -az)] + "]");
			}
			case "shed" -> {
				for (int x = 0; x < 3; x++) {
					for (int z = 0; z < 2; z++) {
						boolean post = x != 1;
						if (post) {
							plan.set(ax + x, 1, az + z, wood + "_fence");
							plan.set(ax + x, 2, az + z, wood + "_fence");
						}
						plan.set(ax + x, 3, az + z, wood + "_slab[type=bottom]");
					}
				}
				plan.set(ax + 1, 1, az, "crafting_table");
				plan.set(ax + 1, 1, az + 1, "barrel[facing=up]");
				plan.loot(ax + 1, 1, az + 1, LOOT_TOOLS);
			}
			case "crates" -> {
				plan.set(ax, 1, az, "barrel[facing=up]");
				plan.set(ax + 1, 1, az, "barrel[facing=north]");
				plan.set(ax, 1, az + 1, "barrel[facing=east]");
				plan.set(ax, 2, az, "barrel[facing=up]");
			}
			case "well" -> {
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						if (x == 0 && z == 0) {
							plan.set(ax, 0, az, "water");
							plan.set(ax, -1, az, m.plinth(), TowerPlan.FOUNDATION);
						} else {
							plan.set(ax + x, 0, az + z, m.plinth(), TowerPlan.FOUNDATION);
							plan.set(ax + x, 1, az + z, m.wallBlock());
						}
					}
				}
				plan.set(ax - 1, 2, az, wood + "_fence");
				plan.set(ax + 1, 2, az, wood + "_fence");
				for (int x = -1; x <= 1; x++) {
					plan.set(ax + x, 3, az, wood + "_slab[type=bottom]");
				}
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ guardia

	private void guards() {
		// siempre uno arriba vigilando, mirando hacia fuera
		int[][] spots = {{1, -1}, {-1, 1}, {1, 1}, {-1, -1}, {0, -1}, {1, 0}};
		for (int[] s : spots) {
			if (standable(s[0], deck + 1, s[1])) {
				plan.guard(s[0], deck + 1, s[1], 180.0F);
				break;
			}
		}
		if (chance(0.5)) {
			for (int[] s : new int[][] {{1, 0}, {-1, 0}, {0, 0}, {1, 1}}) {
				if (standable(s[0], 1, s[1])) {
					plan.guard(s[0], 1, s[1], 180.0F);
					break;
				}
			}
		}
	}

	private boolean standable(int x, int y, int z) {
		String floor = plan.get(x, y - 1, z);
		return floor != null && !TowerChecks.passable(floor) && !floor.contains("trapdoor")
				&& TowerChecks.passable(plan.get(x, y, z)) && TowerChecks.passable(plan.get(x, y + 1, z));
	}
}

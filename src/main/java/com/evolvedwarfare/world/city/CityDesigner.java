package com.evolvedwarfare.world.city;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Diseña una ciudad amurallada a partir de su semilla (Java puro): cada ciudad sale distinta.
 *
 * <ul>
 * <li><b>Muralla</b> de 3 de grueso y 7 de alto con almenas y camino de ronda, torres en las esquinas (con una sala y un
 * arquero arriba), torres a lo largo de los lienzos largos y de 2 a 4 puertas con su par de torres; junto a cada puerta,
 * una escalera para subir a la muralla.</li>
 * <li><b>Calles</b>: una ronda pegada a la muralla, dos avenidas en cruz (de puerta a puerta) y calles secundarias que
 * parten las manzanas grandes. En el centro, la plaza con su fuente.</li>
 * <li><b>Manzanas</b> partidas en solares que dan a la calle: tiendas (una por oficio, las más cercanas a la plaza), un
 * cuartel junto a una puerta, casas de uno o dos pisos, huertos y pozos.</li>
 * <li><b>Tiendas</b>: la puerta da a la calle, el mostrador corta la planta baja de pared a pared (el mercader está
 * siempre detrás) y su cama está arriba (con escalera) o en un cuarto de atrás.</li>
 * </ul>
 */
public final class CityDesigner {
	public enum Style {
		MEDIEVAL, DESERT, SNOW
	}

	/** Alto de la muralla (el camino de ronda está encima). */
	static final int WALL_H = 7;
	/** Grueso de la muralla. */
	static final int WALL_T = 3;
	/** Ancho de la ronda (calle pegada a la muralla por dentro). */
	static final int RING = 3;
	/** Medio ancho de las avenidas. */
	static final int AVENUE = 2;
	static final int STREET = 3;
	static final int MAX_BLOCK = 26;

	private CityDesigner() {
	}

	// ------------------------------------------------------------------ materiales

	private record Roof(String stairs, String full, String slab) {
	}

	private static final class Palette {
		String[] wall;
		String foundation;
		String merlon;
		String[] street;
		String[] avenue;
		String[] plaza;
		String ground;
		String fill;
		String[] lowerWall;
		String[] upperWall;
		String[] frame;
		String[] floor;
		Roof[] roofs;
		String[] doors;
		String fence;
		String fenceGate;
		String window = "glass_pane";
		String[] beds;
		boolean flatRoofs;
		String flatTop;
		String flatRim;
		String counter;
		String stairsMat;
		String rail;
		String[] trapdoor;
		String bannerBase;
	}

	private static Palette palette(Style style) {
		Palette p = new Palette();
		switch (style) {
			case DESERT -> {
				p.wall = new String[] {"sandstone", "sandstone", "cut_sandstone", "smooth_sandstone"};
				p.foundation = "sandstone";
				p.merlon = "cut_sandstone";
				p.street = new String[] {"smooth_sandstone", "sandstone", "sand", "smooth_sandstone"};
				p.avenue = new String[] {"cut_sandstone", "smooth_sandstone", "smooth_sandstone"};
				p.plaza = new String[] {"smooth_sandstone", "cut_sandstone", "chiseled_sandstone", "orange_terracotta"};
				p.ground = "sand";
				p.fill = "sandstone";
				p.lowerWall = new String[] {"smooth_sandstone", "sandstone", "white_terracotta", "smooth_sandstone"};
				p.upperWall = new String[] {"smooth_sandstone", "sandstone", "white_terracotta"};
				p.frame = new String[] {"cut_sandstone", "chiseled_sandstone"};
				p.floor = new String[] {"jungle_planks", "acacia_planks", "birch_planks"};
				p.roofs = new Roof[] {new Roof("sandstone_stairs", "cut_sandstone", "smooth_sandstone_slab")};
				p.doors = new String[] {"jungle_door", "acacia_door", "birch_door"};
				p.fence = "jungle_fence";
				p.fenceGate = "jungle_fence_gate";
				p.beds = new String[] {"orange", "yellow", "red", "white"};
				p.flatRoofs = true;
				p.flatTop = "smooth_sandstone";
				p.flatRim = "cut_sandstone_slab";
				p.counter = "cut_sandstone";
				p.stairsMat = "jungle_stairs";
				p.rail = "jungle_fence";
				p.trapdoor = new String[] {"jungle_trapdoor", "acacia_trapdoor"};
				p.bannerBase = "orange";
			}
			case SNOW -> {
				p.wall = new String[] {"cobblestone", "stone_bricks", "cobblestone", "andesite", "mossy_cobblestone"};
				p.foundation = "cobblestone";
				p.merlon = "cobblestone";
				p.street = new String[] {"gravel", "cobblestone", "coarse_dirt", "gravel"};
				p.avenue = new String[] {"spruce_planks", "spruce_planks", "stripped_spruce_wood"};
				p.plaza = new String[] {"cobblestone", "stone_bricks", "andesite", "polished_andesite"};
				p.ground = "snow_block";
				p.fill = "dirt";
				p.lowerWall = new String[] {"cobblestone", "stone_bricks", "spruce_log"};
				p.upperWall = new String[] {"spruce_log", "spruce_planks", "stripped_spruce_log"};
				p.frame = new String[] {"stripped_spruce_log", "dark_oak_log", "spruce_log"};
				p.floor = new String[] {"spruce_planks", "dark_oak_planks"};
				p.roofs = new Roof[] {new Roof("dark_oak_stairs", "dark_oak_planks", "dark_oak_slab"),
						new Roof("spruce_stairs", "spruce_planks", "spruce_slab"),
						new Roof("deepslate_tile_stairs", "deepslate_tiles", "deepslate_tile_slab")};
				p.doors = new String[] {"spruce_door", "dark_oak_door"};
				p.fence = "spruce_fence";
				p.fenceGate = "spruce_fence_gate";
				p.beds = new String[] {"blue", "white", "light_gray", "cyan"};
				p.counter = "spruce_planks";
				p.stairsMat = "spruce_stairs";
				p.rail = "spruce_fence";
				p.trapdoor = new String[] {"spruce_trapdoor"};
				p.bannerBase = "blue";
			}
			default -> {
				p.wall = new String[] {"stone_bricks", "stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks"};
				p.foundation = "cobblestone";
				p.merlon = "stone_bricks";
				p.street = new String[] {"cobblestone", "gravel", "andesite", "cobblestone", "dirt_path"};
				p.avenue = new String[] {"stone_bricks", "polished_andesite", "cobblestone", "stone_bricks"};
				p.plaza = new String[] {"polished_andesite", "stone_bricks", "stone_bricks", "chiseled_stone_bricks"};
				p.ground = "grass_block";
				p.fill = "dirt";
				p.lowerWall = new String[] {"cobblestone", "stone_bricks", "oak_planks", "bricks"};
				p.upperWall = new String[] {"white_terracotta", "oak_planks", "birch_planks", "mud_bricks", "white_terracotta"};
				p.frame = new String[] {"spruce_log", "dark_oak_log", "oak_log", "stripped_spruce_log"};
				p.floor = new String[] {"spruce_planks", "oak_planks", "dark_oak_planks"};
				p.roofs = new Roof[] {new Roof("spruce_stairs", "spruce_planks", "spruce_slab"),
						new Roof("dark_oak_stairs", "dark_oak_planks", "dark_oak_slab"),
						new Roof("brick_stairs", "bricks", "brick_slab"),
						new Roof("deepslate_tile_stairs", "deepslate_tiles", "deepslate_tile_slab")};
				p.doors = new String[] {"spruce_door", "oak_door", "dark_oak_door"};
				p.fence = "spruce_fence";
				p.fenceGate = "spruce_fence_gate";
				p.beds = new String[] {"red", "blue", "white", "green", "yellow"};
				p.counter = "spruce_planks";
				p.stairsMat = "spruce_stairs";
				p.rail = "spruce_fence";
				p.trapdoor = new String[] {"spruce_trapdoor", "oak_trapdoor"};
				p.bannerBase = "red";
			}
		}
		return p;
	}

	/** Los oficios de las tiendas de cada estilo (todas las ciudades tienen tendero, armero, cantero y joyero). */
	public static List<String> tradesOf(Style style) {
		return switch (style) {
			case DESERT -> List.of("spice_merchant", "grocer", "armorer", "jeweler", "mason");
			case SNOW -> List.of("furrier", "grocer", "armorer", "mason", "jeweler");
			default -> List.of("grocer", "tailor", "armorer", "mason", "jeweler");
		};
	}

	// ------------------------------------------------------------------ geometría de los solares

	/** Hacia dónde da un solar a la calle. */
	enum Dir {
		NORTH(0, -1, 180.0F), SOUTH(0, 1, 0.0F), WEST(-1, 0, 90.0F), EAST(1, 0, -90.0F);

		final int dx;
		final int dz;
		final float yaw;

		Dir(int dx, int dz, float yaw) {
			this.dx = dx;
			this.dz = dz;
			this.yaw = yaw;
		}

		String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		Dir opposite() {
			return switch (this) {
				case NORTH -> SOUTH;
				case SOUTH -> NORTH;
				case WEST -> EAST;
				case EAST -> WEST;
			};
		}

		/** Lo que queda a la derecha mirando el edificio desde la calle (hacia donde crece u). */
		Dir right() {
			return switch (this) {
				case NORTH -> EAST;
				case SOUTH -> WEST;
				case WEST -> NORTH;
				case EAST -> SOUTH;
			};
		}

		String axis() {
			return dx != 0 ? "x" : "z";
		}
	}

	/** Un rectángulo [x0..x1] x [z0..z1]. */
	record Rect(int x0, int z0, int x1, int z1) {
		int w() {
			return x1 - x0 + 1;
		}

		int d() {
			return z1 - z0 + 1;
		}

		boolean overlaps(Rect o) {
			return x0 <= o.x1 && o.x0 <= x1 && z0 <= o.z1 && o.z0 <= z1;
		}
	}

	/** Un solar: su rectángulo y hacia dónde da la calle. width va a lo largo de la calle; depth, hacia dentro. */
	record Lot(Rect rect, Dir facing) {
		int width() {
			return facing.dx != 0 ? rect.d() : rect.w();
		}

		int depth() {
			return facing.dx != 0 ? rect.w() : rect.d();
		}

		double distance() {
			double cx = (rect.x0 + rect.x1) / 2.0;
			double cz = (rect.z0 + rect.z1) / 2.0;
			return Math.sqrt(cx * cx + cz * cz);
		}
	}

	/** Coordenadas de un edificio: u a lo largo de la fachada, v hacia dentro (0 = la fachada), y arriba. */
	static final class Frame {
		final Rect r;
		final Dir out;

		Frame(Rect r, Dir out) {
			this.r = r;
			this.out = out;
		}

		int x(int u, int v) {
			return switch (out) {
				case NORTH -> r.x0 + u;
				case SOUTH -> r.x1 - u;
				case WEST -> r.x0 + v;
				case EAST -> r.x1 - v;
			};
		}

		int z(int u, int v) {
			return switch (out) {
				case NORTH -> r.z0 + v;
				case SOUTH -> r.z1 - v;
				case WEST -> r.z1 - u;
				case EAST -> r.z0 + u;
			};
		}

		String outward() {
			return out.id();
		}

		String inward() {
			return out.opposite().id();
		}

		String right() {
			return out.right().id();
		}

		String left() {
			return out.right().opposite().id();
		}
	}

	// ------------------------------------------------------------------ el diseño

	private static final class Builder {
		final CityPlan plan = new CityPlan();
		final Random rnd;
		final Palette p;
		final Style style;
		final int hx;
		final int hz;
		final int plazaR;
		final List<Dir> gates = new ArrayList<>();
		/** Qué hay en cada columna dentro de la muralla (para no pisar calles con edificios). */
		final char[][] map;
		static final char FREE = ' ';
		static final char ROAD = 'r';
		static final char PLAZA = 'p';
		static final char WALL = 'w';
		static final char BUILT = 'b';
		final StringBuilder desc = new StringBuilder();

		Builder(long seed, Style style) {
			this.rnd = new Random(seed);
			this.style = style;
			this.p = palette(style);
			this.hx = 36 + 4 * rnd.nextInt(4);
			this.hz = 36 + 4 * rnd.nextInt(4);
			this.plazaR = 7 + rnd.nextInt(3);
			plan.halfX = hx;
			plan.halfZ = hz;
			map = new char[2 * hx + 1][2 * hz + 1];
			for (char[] row : map) {
				java.util.Arrays.fill(row, FREE);
			}
		}

		String pick(String[] options) {
			return options[rnd.nextInt(options.length)];
		}

		char at(int x, int z) {
			if (Math.abs(x) > hx || Math.abs(z) > hz) {
				return WALL;
			}
			return map[x + hx][z + hz];
		}

		void mark(int x, int z, char c) {
			if (Math.abs(x) <= hx && Math.abs(z) <= hz) {
				map[x + hx][z + hz] = c;
			}
		}

		void set(int x, int y, int z, String state) {
			plan.set(x, y, z, state);
		}

		void found(int x, int y, int z, String state) {
			plan.set(x, y, z, state, CityPlan.FOUNDATION);
		}

		void air(int x, int y, int z) {
			plan.air(x, y, z);
		}

		CityPlan build() {
			chooseGates();
			ground();
			walls();
			streets();
			plaza();
			List<Lot> lots = lots();
			buildings(lots);
			gateRoads();
			plan.description = desc.toString();
			return plan;
		}

		// -------------------------------------------------------------- muralla

		void chooseGates() {
			boolean northSouth = rnd.nextBoolean();
			if (northSouth) {
				gates.add(Dir.NORTH);
				gates.add(Dir.SOUTH);
			} else {
				gates.add(Dir.WEST);
				gates.add(Dir.EAST);
			}
			for (Dir d : northSouth ? new Dir[] {Dir.WEST, Dir.EAST} : new Dir[] {Dir.NORTH, Dir.SOUTH}) {
				if (rnd.nextInt(2) == 0) {
					gates.add(d);
				}
			}
			desc.append(style).append(' ').append(2 * hx + 1).append('x').append(2 * hz + 1).append(", ").append(gates.size())
					.append(" puertas");
		}

		/** El suelo de dentro: todo lo que no sea calle ni edificio, patio (hierba, arena o nieve). */
		void ground() {
			for (int x = -hx; x <= hx; x++) {
				for (int z = -hz; z <= hz; z++) {
					found(x, 0, z, p.ground);
				}
			}
		}

		/** Posición de la muralla: lado, t a lo largo (de -h a h) y d desde fuera (0) hacia dentro. */
		int[] wallPos(Dir side, int t, int d) {
			return switch (side) {
				case NORTH -> new int[] {t, -hz + d};
				case SOUTH -> new int[] {t, hz - d};
				case WEST -> new int[] {-hx + d, t};
				case EAST -> new int[] {hx - d, t};
			};
		}

		int halfLen(Dir side) {
			return side.dx != 0 ? hz : hx;
		}

		boolean isGate(Dir side) {
			return gates.contains(side);
		}

		void walls() {
			for (Dir side : Dir.values()) {
				int h = halfLen(side);
				for (int t = -h; t <= h; t++) {
					boolean gate = isGate(side) && Math.abs(t) <= 2;
					for (int d = 0; d < WALL_T; d++) {
						int[] c = wallPos(side, t, d);
						mark(c[0], c[1], WALL);
						found(c[0], 0, c[1], p.foundation);
						for (int y = 1; y <= WALL_H; y++) {
							if (gate && (y <= 4 || (y == 5 && Math.abs(t) <= 1))) {
								air(c[0], y, c[1]);
							} else {
								set(c[0], y, c[1], pick(p.wall));
							}
						}
						if (gate) {
							set(c[0], 0, c[1], pick(p.avenue));
						}
					}
					// almenas en el borde de fuera
					int[] o = wallPos(side, t, 0);
					if (Math.floorMod(t, 2) == 0) {
						set(o[0], WALL_H + 1, o[1], p.merlon);
					}
					// pretil bajo por dentro
					int[] in = wallPos(side, t, WALL_T - 1);
					if (Math.floorMod(t, 4) == 0 && !(isGate(side) && Math.abs(t) <= 9)) {
						set(in[0], WALL_H + 1, in[1], p.rail);
					}
				}
				if (isGate(side)) {
					gatehouse(side);
					wallStairs(side);
				}
				sideTowers(side);
			}
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					cornerTower(sx * hx, sz * hz, sx, sz);
				}
			}
		}

		/** Un torreón macizo de 5x5 que sobresale 2 por fuera, con almenas. */
		void solidTower(Dir side, int tc, int height) {
			for (int t = tc - 2; t <= tc + 2; t++) {
				for (int d = -2; d <= 2; d++) {
					int[] c = wallPos(side, t, d);
					found(c[0], 0, c[1], p.foundation);
					for (int y = 1; y <= height; y++) {
						set(c[0], y, c[1], pick(p.wall));
					}
					boolean edge = Math.abs(t - tc) == 2 || Math.abs(d) == 2;
					if (edge && Math.floorMod(t + d, 2) == 0) {
						set(c[0], height + 1, c[1], p.merlon);
					}
				}
			}
		}

		void gatehouse(Dir side) {
			solidTower(side, -5, WALL_H + 4);
			solidTower(side, 5, WALL_H + 4);
			// rastrillo subido (barrotes bajo el arco, por fuera)
			for (int t = -1; t <= 1; t++) {
				int[] c = wallPos(side, t, 0);
				set(c[0], 5, c[1], "iron_bars");
			}
			// guardias a los lados de la puerta, por dentro, mirando afuera
			for (int t = -2; t <= 2; t += 4) {
				int[] c = wallPos(side, t, WALL_T + 1);
				plan.guard(new CityPlan.Guard("warrior", c[0], 1, c[1], side.yaw));
			}
		}

		/** Escalera para subir a la muralla junto a la puerta, pegada a la cara de dentro (en la ronda). */
		void wallStairs(Dir side) {
			int dir = rnd.nextBoolean() ? 1 : -1;
			// a lo largo de t: de t = 4 a t = 10 (o al revés), subiendo de 1 en 1
			Dir along;
			if (side.dx != 0) {
				along = dir > 0 ? Dir.SOUTH : Dir.NORTH;
			} else {
				along = dir > 0 ? Dir.EAST : Dir.WEST;
			}
			for (int i = 0; i < WALL_H; i++) {
				int t = dir * (4 + i);
				int[] c = wallPos(side, t, WALL_T);
				for (int y = 1; y <= i; y++) {
					set(c[0], y, c[1], pick(p.wall));
				}
				set(c[0], i + 1, c[1], stairs(wallStairsMat(), along.id(), false));
				for (int y = i + 2; y <= i + 3; y++) {
					air(c[0], y, c[1]);
				}
			}
		}

		String wallStairsMat() {
			return switch (style) {
				case DESERT -> "sandstone_stairs";
				case SNOW -> "cobblestone_stairs";
				default -> "stone_brick_stairs";
			};
		}

		/** Torres a lo largo de los lienzos (cada ~20 bloques, sin tocar las puertas ni las esquinas). */
		void sideTowers(Dir side) {
			int h = halfLen(side);
			int from = isGate(side) ? 12 : 6;
			int to = h - 9;
			if (to - from < 6) {
				return;
			}
			int count = (to - from) / 18 + 1;
			for (int s = -1; s <= 1; s += 2) {
				for (int i = 0; i < count; i++) {
					int tc = s * (from + (to - from) * (2 * i + 1) / (2 * count));
					solidTower(side, tc, WALL_H + 3);
				}
			}
		}

		/** Torre de esquina de 7x7: maciza hasta el camino de ronda, con una sala encima y un arquero en lo alto. */
		void cornerTower(int cx, int cz, int sx, int sz) {
			int top = WALL_H + 6;
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					int x = cx + dx;
					int z = cz + dz;
					found(x, 0, z, p.foundation);
					boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
					for (int y = 1; y <= top; y++) {
						if (!edge && y > WALL_H && y < top) {
							air(x, y, z);
						} else {
							set(x, y, z, pick(p.wall));
						}
					}
					if (edge && Math.floorMod(dx + dz, 2) == 0) {
						set(x, top + 1, z, p.merlon);
					}
				}
			}
			// puertas al camino de ronda (hacia los dos lienzos)
			for (int k = -1; k <= 1; k++) {
				for (int y = WALL_H + 1; y <= WALL_H + 2; y++) {
					air(cx - sx * 3, y, cz - sz + k);
					air(cx - sx + k, y, cz - sz * 3);
				}
			}
			// troneras
			for (int k = -1; k <= 1; k += 2) {
				air(cx + sx * 3, WALL_H + 2, cz + k);
				air(cx + k, WALL_H + 2, cz + sz * 3);
			}
			// escala a la azotea
			int lx = cx + sx * 2;
			int lz = cz;
			String facing = sx > 0 ? "west" : "east";
			for (int y = WALL_H + 1; y <= top; y++) {
				set(lx, y, lz, "ladder[facing=" + facing + "]");
			}
			set(cx - sx, WALL_H + 1, cz - sz, "lantern");
			plan.guard(new CityPlan.Guard("archer", cx, top + 1, cz, sx > 0 ? (sz > 0 ? -45.0F : -135.0F) : (sz > 0 ? 45.0F : 135.0F)));
		}

		// -------------------------------------------------------------- calles

		void road(int x, int z, String[] options) {
			if (Math.abs(x) > hx - WALL_T || Math.abs(z) > hz - WALL_T) {
				return;
			}
			if (at(x, z) == PLAZA) {
				return;
			}
			found(x, 0, z, pick(options));
			mark(x, z, ROAD);
		}

		final List<Rect> blocks = new ArrayList<>();

		void streets() {
			int ix = hx - WALL_T;
			int iz = hz - WALL_T;
			// la ronda
			for (int x = -ix; x <= ix; x++) {
				for (int z = -iz; z <= iz; z++) {
					if (Math.abs(x) > ix - RING || Math.abs(z) > iz - RING) {
						road(x, z, p.street);
					}
				}
			}
			// las avenidas en cruz
			for (int x = -ix; x <= ix; x++) {
				for (int z = -AVENUE; z <= AVENUE; z++) {
					road(x, z, p.avenue);
				}
			}
			for (int z = -iz; z <= iz; z++) {
				for (int x = -AVENUE; x <= AVENUE; x++) {
					road(x, z, p.avenue);
				}
			}
			// farolas a lo largo de las avenidas
			for (int k = plazaR + 4; k <= Math.max(ix, iz) - RING - 2; k += 9) {
				for (int s = -1; s <= 1; s += 2) {
					if (k <= ix - RING - 2) {
						lamp(s * k, AVENUE);
					}
					if (k <= iz - RING - 2) {
						lamp(-AVENUE, s * k);
					}
				}
			}
			// manzanas: los cuatro cuartos entre las avenidas y la ronda, partidos si son grandes
			int bx = ix - RING;
			int bz = iz - RING;
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					int x0 = sx > 0 ? AVENUE + 1 : -bx;
					int x1 = sx > 0 ? bx : -AVENUE - 1;
					int z0 = sz > 0 ? AVENUE + 1 : -bz;
					int z1 = sz > 0 ? bz : -AVENUE - 1;
					split(new Rect(x0, z0, x1, z1));
				}
			}
		}

		/** Una farola (poste de valla con farol) en el borde de la avenida, si ahí no hay nada. */
		void lamp(int x, int z) {
			if (at(x, z) != FREE && at(x, z) != ROAD) {
				return;
			}
			found(x, 0, z, p.foundation);
			set(x, 1, z, p.fence);
			set(x, 2, z, p.fence);
			set(x, 3, z, "lantern");
			mark(x, z, BUILT);
		}

		/** Parte una manzana con calles de 3 hasta que ningún lado pase de {@link #MAX_BLOCK}. */
		void split(Rect r) {
			if (r.w() > MAX_BLOCK && r.w() >= r.d()) {
				int cut = r.x0 + r.w() * (35 + rnd.nextInt(31)) / 100;
				for (int x = cut; x < cut + STREET; x++) {
					for (int z = r.z0; z <= r.z1; z++) {
						road(x, z, p.street);
					}
				}
				split(new Rect(r.x0, r.z0, cut - 1, r.z1));
				split(new Rect(cut + STREET, r.z0, r.x1, r.z1));
				return;
			}
			if (r.d() > MAX_BLOCK) {
				int cut = r.z0 + r.d() * (35 + rnd.nextInt(31)) / 100;
				for (int z = cut; z < cut + STREET; z++) {
					for (int x = r.x0; x <= r.x1; x++) {
						road(x, z, p.street);
					}
				}
				split(new Rect(r.x0, r.z0, r.x1, cut - 1));
				split(new Rect(r.x0, cut + STREET, r.x1, r.z1));
				return;
			}
			if (r.w() >= 5 && r.d() >= 5) {
				blocks.add(r);
			}
		}

		// -------------------------------------------------------------- plaza

		void plaza() {
			int r = plazaR;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					found(x, 0, z, pick(p.plaza));
					mark(x, z, PLAZA);
				}
			}
			// fuente: pila de 7x7 con el agua un escalón abajo y una columna con farol
			String rim = switch (style) {
				case DESERT -> "cut_sandstone";
				case SNOW -> "stone_bricks";
				default -> "stone_bricks";
			};
			String rimTop = switch (style) {
				case DESERT -> "sandstone_wall";
				case SNOW -> "cobblestone_wall";
				default -> "stone_brick_wall";
			};
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
					if (edge) {
						found(x, 0, z, rim);
						set(x, 1, z, rim);
					} else {
						found(x, -1, z, rim);
						set(x, 0, z, "water");
					}
				}
			}
			for (int y = 0; y <= 3; y++) {
				set(0, y, 0, rim);
			}
			set(0, 4, 0, rimTop);
			set(0, 5, 0, "lantern");
			// farolas en las esquinas de la plaza
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					int x = sx * (r - 1);
					int z = sz * (r - 1);
					set(x, 1, z, p.fence);
					set(x, 2, z, p.fence);
					set(x, 3, z, "lantern");
				}
			}
			plan.guard(new CityPlan.Guard("warrior", r - 2, 1, 0, -90.0F));
			plan.guard(new CityPlan.Guard("crossbowman", -(r - 2), 1, 0, 90.0F));
		}

		// -------------------------------------------------------------- solares

		boolean free(Rect r) {
			for (int x = r.x0; x <= r.x1; x++) {
				for (int z = r.z0; z <= r.z1; z++) {
					if (at(x, z) != FREE) {
						return false;
					}
				}
			}
			return true;
		}

		List<Lot> lots() {
			List<Lot> out = new ArrayList<>();
			for (Rect b : blocks) {
				boolean alongX = b.w() >= b.d();
				int shortLen = alongX ? b.d() : b.w();
				List<Rect> rows = new ArrayList<>();
				List<Dir> facings = new ArrayList<>();
				if (shortLen >= 14) {
					int half = shortLen / 2;
					if (alongX) {
						rows.add(new Rect(b.x0, b.z0, b.x1, b.z0 + half - 1));
						facings.add(Dir.NORTH);
						rows.add(new Rect(b.x0, b.z0 + half, b.x1, b.z1));
						facings.add(Dir.SOUTH);
					} else {
						rows.add(new Rect(b.x0, b.z0, b.x0 + half - 1, b.z1));
						facings.add(Dir.WEST);
						rows.add(new Rect(b.x0 + half, b.z0, b.x1, b.z1));
						facings.add(Dir.EAST);
					}
				} else {
					rows.add(b);
					if (alongX) {
						facings.add((b.z0 + b.z1) / 2 > 0 ? Dir.NORTH : Dir.SOUTH);
					} else {
						facings.add((b.x0 + b.x1) / 2 > 0 ? Dir.WEST : Dir.EAST);
					}
				}
				for (int i = 0; i < rows.size(); i++) {
					Rect row = rows.get(i);
					Dir facing = facings.get(i);
					int len = alongX ? row.w() : row.d();
					int pos = 0;
					while (len - pos >= 5) {
						int w = 7 + rnd.nextInt(5);
						if (len - pos - w < 5) {
							w = len - pos;
						}
						if (w > 13) {
							w = w / 2;
						}
						Rect lot = alongX ? new Rect(row.x0 + pos, row.z0, row.x0 + pos + w - 1, row.z1)
								: new Rect(row.x0, row.z0 + pos, row.x1, row.z0 + pos + w - 1);
						pos += w;
						if (free(lot)) {
							out.add(new Lot(lot, facing));
						}
					}
				}
			}
			return out;
		}

		// -------------------------------------------------------------- edificios

		void buildings(List<Lot> lots) {
			lots.sort(Comparator.comparingDouble(Lot::distance));
			List<String> trades = new ArrayList<>(tradesOf(style));
			// las tiendas, en los solares más cercanos a la plaza que tengan sitio
			for (int i = 0; i < lots.size() && !trades.isEmpty(); i++) {
				Lot lot = lots.get(i);
				if (lot.width() >= 8 && lot.depth() >= 8) {
					shop(lot, trades.remove(0));
					lots.remove(i);
					i--;
				}
			}
			// el cuartel, en el solar grande más cercano a una puerta
			Lot barracks = null;
			double best = Double.MAX_VALUE;
			for (Lot lot : lots) {
				if (lot.width() >= 10 && lot.depth() >= 7) {
					for (Dir g : gates) {
						double gx = g.dx * hx;
						double gz = g.dz * hz;
						double cx = (lot.rect.x0 + lot.rect.x1) / 2.0;
						double cz = (lot.rect.z0 + lot.rect.z1) / 2.0;
						double dist = Math.hypot(cx - gx, cz - gz);
						if (dist < best) {
							best = dist;
							barracks = lot;
						}
					}
				}
			}
			if (barracks != null) {
				lots.remove(barracks);
				barracks(barracks);
			}
			int houses = 0;
			int gardens = 0;
			for (Lot lot : lots) {
				int roll = rnd.nextInt(100);
				if (roll < 12 && lot.depth() >= 5) {
					garden(lot);
					gardens++;
				} else if (roll < 17) {
					well(lot);
				} else {
					house(lot);
					houses++;
				}
			}
			desc.append(", ").append(tradesOf(style).size() - trades.size()).append(" tiendas, ").append(houses).append(" casas, ")
					.append(gardens).append(" huertos");
		}

		/** Pared exterior de un edificio: esquinas con poste, el resto con relleno (el de abajo y el de arriba). */
		void shell(Frame f, int bw, int bd, int floors, String lower, String upper, String frame, String floor) {
			int top = floors * 4;
			for (int u = 0; u < bw; u++) {
				for (int v = 0; v < bd; v++) {
					int x = f.x(u, v);
					int z = f.z(u, v);
					mark(x, z, BUILT);
					boolean edge = u == 0 || v == 0 || u == bw - 1 || v == bd - 1;
					boolean corner = (u == 0 || u == bw - 1) && (v == 0 || v == bd - 1);
					found(x, 0, z, edge ? p.foundation : floor);
					for (int y = 1; y <= top; y++) {
						if (y % 4 == 0) {
							// suelo de arriba (o techo) y viga alrededor
							String inner = y == top && p.flatRoofs ? p.flatTop : floor;
							set(x, y, z, edge ? (style == Style.DESERT ? frame : beam(frame, f, u, v, bw, bd)) : inner);
						} else if (edge) {
							if (corner && style != Style.DESERT) {
								set(x, y, z, log(frame, "y"));
							} else {
								String infill = y < 4 ? lower : upper;
								set(x, y, z, infill.endsWith("_log") ? log(infill, wallAxis(f, u, v, bw)) : infill);
							}
						} else {
							air(x, y, z);
						}
					}
				}
			}
		}

		String beam(String frame, Frame f, int u, int v, int bw, int bd) {
			boolean corner = (u == 0 || u == bw - 1) && (v == 0 || v == bd - 1);
			return corner ? log(frame, "y") : log(frame, wallAxis(f, u, v, bw));
		}

		/** Eje de un tronco tumbado a lo largo de la pared. */
		String wallAxis(Frame f, int u, int v, int bw) {
			boolean frontBack = v == 0 || (u != 0 && u != bw - 1);
			Dir along = frontBack ? f.out.right() : f.out;
			return along.axis();
		}

		String log(String block, String axis) {
			if (block.endsWith("_log") || block.endsWith("_wood")) {
				return block + "[axis=" + axis + "]";
			}
			return block;
		}

		/** Ventanas: cristales cada tres bloques en las paredes (no en las esquinas ni junto a la puerta). */
		void windows(Frame f, int bw, int bd, int floors, int doorU) {
			for (int level = 0; level < floors; level++) {
				int y = level * 4 + 2;
				for (int u = 2; u < bw - 2; u += 2) {
					if (level > 0 || Math.abs(u - doorU) > 1) {
						set(f.x(u, 0), y, f.z(u, 0), p.window);
					}
					set(f.x(u, bd - 1), y, f.z(u, bd - 1), p.window);
				}
				for (int v = 2; v < bd - 2; v += 3) {
					set(f.x(0, v), y, f.z(0, v), p.window);
					set(f.x(bw - 1, v), y, f.z(bw - 1, v), p.window);
				}
			}
		}

		void door(Frame f, int u, String door) {
			int x = f.x(u, 0);
			int z = f.z(u, 0);
			set(x, 1, z, door + "[facing=" + f.inward() + ",half=lower,hinge=left,open=false]");
			set(x, 2, z, door + "[facing=" + f.inward() + ",half=upper,hinge=left,open=false]");
		}

		void bed(Frame f, int footU, int footV, int headU, int headV, int y, String color) {
			Dir dir;
			if (headV > footV) {
				dir = f.out.opposite();
			} else if (headV < footV) {
				dir = f.out;
			} else if (headU > footU) {
				dir = f.out.right();
			} else {
				dir = f.out.right().opposite();
			}
			set(f.x(footU, footV), y, f.z(footU, footV), color + "_bed[facing=" + dir.id() + ",part=foot]");
			set(f.x(headU, headV), y, f.z(headU, headV), color + "_bed[facing=" + dir.id() + ",part=head]");
		}

		void torch(Frame f, int u, int v, int y, String facing) {
			set(f.x(u, v), y, f.z(u, v), "wall_torch[facing=" + facing + "]");
		}

		/** Tejado: a dos aguas (cumbrera a lo largo del lado largo) o plano con pretil (desierto). */
		void roof(Frame f, int bw, int bd, int baseY) {
			int[] xs = {f.x(0, 0), f.x(bw - 1, bd - 1)};
			int[] zs = {f.z(0, 0), f.z(bw - 1, bd - 1)};
			int x0 = Math.min(xs[0], xs[1]);
			int x1 = Math.max(xs[0], xs[1]);
			int z0 = Math.min(zs[0], zs[1]);
			int z1 = Math.max(zs[0], zs[1]);
			if (p.flatRoofs) {
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
						if (edge) {
							set(x, baseY, z, p.flatRim);
						}
					}
				}
				return;
			}
			Roof roof = p.roofs[rnd.nextInt(p.roofs.length)];
			boolean ridgeX = (x1 - x0) >= (z1 - z0);
			String gable = pick(p.upperWall);
			if (ridgeX) {
				int a = z0 - 1;
				int b = z1 + 1;
				for (int k = 0; a <= b; k++, a++, b--) {
					int y = baseY + k;
					for (int x = x0 - 1; x <= x1 + 1; x++) {
						if (a == b) {
							set(x, y, a, roof.slab);
						} else {
							set(x, y, a, stairs(roof.stairs, "south", false));
							set(x, y, b, stairs(roof.stairs, "north", false));
						}
					}
					// hastiales
					for (int z = a + 1; z <= b - 1; z++) {
						if (z >= z0 && z <= z1) {
							set(x0, y, z, gable);
							set(x1, y, z, gable);
						}
					}
				}
			} else {
				int a = x0 - 1;
				int b = x1 + 1;
				for (int k = 0; a <= b; k++, a++, b--) {
					int y = baseY + k;
					for (int z = z0 - 1; z <= z1 + 1; z++) {
						if (a == b) {
							set(a, y, z, roof.slab);
						} else {
							set(a, y, z, stairs(roof.stairs, "east", false));
							set(b, y, z, stairs(roof.stairs, "west", false));
						}
					}
					for (int x = a + 1; x <= b - 1; x++) {
						if (x >= x0 && x <= x1) {
							set(x, y, z0, gable);
							set(x, y, z1, gable);
						}
					}
				}
			}
		}

		String stairs(String block, String facing, boolean top) {
			return block + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",shape=straight]";
		}

		/** Medidas del edificio dentro del solar: deja un callejón a un lado y patio detrás si es muy hondo. */
		int[] size(Lot lot, int maxDepth) {
			int bw = Math.max(5, lot.width() - 1);
			int bd = Math.min(lot.depth(), maxDepth);
			return new int[] {bw, bd};
		}

		// -------------------------------------------------------------- casa

		void house(Lot lot) {
			int[] s = size(lot, 9 + rnd.nextInt(3));
			int bw = s[0];
			int bd = s[1];
			if (bw < 5 || bd < 5) {
				return;
			}
			Frame f = new Frame(lot.rect, lot.facing);
			int floors = (bw >= 6 && bd >= 6 && rnd.nextInt(100) < 50) ? 2 : 1;
			String lower = pick(p.lowerWall);
			String upper = floors > 1 ? pick(p.upperWall) : lower;
			String frame = pick(p.frame);
			String floor = pick(p.floor);
			shell(f, bw, bd, floors, lower, upper, frame, floor);
			int doorU = bw / 2;
			door(f, doorU, pick(p.doors));
			windows(f, bw, bd, floors, doorU);
			torch(f, doorU + 1 < bw - 1 ? doorU + 1 : doorU - 1, 1, 2, f.inward());
			String bedColor = pick(p.beds);
			int bedY = floors > 1 ? 5 : 1;
			// cama contra la pared de atrás, a la derecha
			bed(f, bw - 2, bd - 3, bw - 2, bd - 2, bedY, bedColor);
			if (bw >= 7 && rnd.nextBoolean()) {
				bed(f, bw - 3, bd - 3, bw - 3, bd - 2, bedY, pick(p.beds));
			}
			// muebles abajo
			set(f.x(1, bd - 2), 1, f.z(1, bd - 2), "crafting_table");
			set(f.x(1, bd - 3), 1, f.z(1, bd - 3), (style == Style.DESERT ? "smoker" : "furnace") + "[facing=" + f.right() + "]");
			String chest = "chest[facing=" + f.left() + "]";
			set(f.x(bw - 2, 1), 1, f.z(bw - 2, 1), chest);
			plan.loot(f.x(bw - 2, 1), 1, f.z(bw - 2, 1), lootTable());
			if (floors > 1) {
				// escala a la planta de arriba (junto a la pared izquierda)
				for (int y = 1; y <= 4; y++) {
					set(f.x(1, 2), y, f.z(1, 2), "ladder[facing=" + f.right() + "]");
				}
				set(f.x(1, bd - 2), 5, f.z(1, bd - 2), "barrel[facing=up]");
				torch(f, 1, bd - 2, 6, f.right());
			}
			roof(f, bw, bd, floors * 4 + 1);
			if (rnd.nextInt(100) < 45) {
				plan.resident(f.x(doorU, 2), 1, f.z(doorU, 2));
			}
		}

		String lootTable() {
			return switch (style) {
				case DESERT -> "minecraft:chests/village/village_desert_house";
				case SNOW -> "minecraft:chests/village/village_snowy_house";
				default -> "minecraft:chests/village/village_plains_house";
			};
		}

		// -------------------------------------------------------------- tienda

		void shop(Lot lot, String trade) {
			int bw = Math.min(lot.width() - 1, 11);
			int bd = Math.min(lot.depth(), 12);
			Frame f = new Frame(lot.rect, lot.facing);
			boolean backRoom = bd >= 10 && rnd.nextBoolean();
			int floors = backRoom ? 1 : 2;
			String lower = pick(p.lowerWall);
			String upper = floors > 1 ? pick(p.upperWall) : lower;
			String frame = pick(p.frame);
			String floor = pick(p.floor);
			shell(f, bw, bd, floors, lower, upper, frame, floor);
			int doorU = bw / 2;
			door(f, doorU, pick(p.doors));
			windows(f, bw, bd, floors, doorU);
			// estandarte del oficio junto a la puerta, por fuera
			int bannerU = doorU - 2 >= 1 ? doorU - 2 : doorU + 2;
			set(f.x(bannerU, -1), 3, f.z(bannerU, -1), bannerColor(trade) + "_wall_banner[facing=" + f.outward() + "]");
			// el mostrador: de pared a pared en v = 3
			int cv = 3;
			for (int u = 1; u <= bw - 2; u++) {
				set(f.x(u, cv), 1, f.z(u, cv), u % 3 == 0 ? "barrel[facing=" + f.outward() + "]" : p.counter);
			}
			set(f.x(1, cv), 2, f.z(1, cv), "lantern");
			if (bw - 2 > 2) {
				set(f.x(bw - 2, cv), 2, f.z(bw - 2, cv), "flower_pot");
			}
			torch(f, 1, 1, 2, f.right());
			torch(f, bw - 2, 1, 2, f.left());
			int standU = doorU;
			int standV = cv + 1;
			int bedU;
			int bedV;
			int bedY;
			if (backRoom) {
				// zona del mercader v = 4..5, tabique en v = 6 con paso junto a la pared derecha, cuarto detrás
				int wallV = cv + 3;
				for (int u = 1; u <= bw - 2; u++) {
					for (int y = 1; y <= 3; y++) {
						if (u == bw - 2 && y <= 2) {
							air(f.x(u, wallV), y, f.z(u, wallV));
						} else {
							set(f.x(u, wallV), y, f.z(u, wallV), floor);
						}
					}
				}
				bedU = 1;
				bedV = bd - 2;
				bedY = 1;
				bed(f, 1, bd - 3, 1, bd - 2, 1, pick(p.beds));
				set(f.x(bw - 2, bd - 2), 1, f.z(bw - 2, bd - 2), "chest[facing=" + f.outward() + "]");
				torch(f, 2, bd - 2, 2, f.outward());
				decor(f, trade, new int[][] {{1, cv + 1}, {1, cv + 2}});
				roof(f, bw, bd, 5);
			} else {
				// escalera a lo largo de la pared de atrás, subiendo hacia la izquierda: escalones en y = 1..4
				int sv = bd - 2;
				for (int i = 0; i < 4; i++) {
					int u = bw - 2 - i;
					for (int y = 1; y <= i; y++) {
						set(f.x(u, sv), y, f.z(u, sv), floor);
					}
					set(f.x(u, sv), i + 1, f.z(u, sv), stairs(p.stairsMat, f.left(), false));
					// sitio para la cabeza: dos libres encima de cada escalón y el hueco en el suelo de arriba (y = 4) sobre
					// todos los escalones de debajo, también el primero (al pasar de uno al siguiente, el cuerpo aún está
					// medio encima del anterior y la cabeza chocaría con el techo)
					for (int y = i + 2; y <= Math.max(i + 3, 4); y++) {
						air(f.x(u, sv), y, f.z(u, sv));
					}
				}
				// barandilla junto al hueco de la escalera, arriba
				for (int i = 0; i <= 2; i++) {
					int u = bw - 2 - i;
					set(f.x(u, sv - 1), 5, f.z(u, sv - 1), p.rail);
				}
				bedU = 1;
				bedV = 1;
				bedY = 5;
				bed(f, 1, 2, 1, 1, 5, pick(p.beds));
				set(f.x(bw - 2, 1), 5, f.z(bw - 2, 1), "chest[facing=" + f.inward() + "]");
				torch(f, 2, 1, 6, f.inward());
				torch(f, bw - 2, bd - 3, 6, f.left());
				int[][] spots = bd - 2 > cv + 1 + 1 ? new int[][] {{1, cv + 1}, {1, cv + 2}} : new int[][] {{1, cv + 1}};
				decor(f, trade, spots);
				roof(f, bw, bd, 9);
			}
			// el catálogo, encima del mostrador delante del mercader, mirando a los clientes
			int tradeIndex = List.of("grocer", "armorer", "mason", "jeweler", "tailor", "spice_merchant", "furrier").indexOf(trade);
			set(f.x(standU, cv), 2, f.z(standU, cv), "evolvedwarfare:trader_catalog[facing=" + f.outward() + ",trade=" + Math.max(0, tradeIndex)
					+ "]");
			int sx = f.x(standU, standV);
			int sz = f.z(standU, standV);
			plan.trader(new CityPlan.Trader(trade, sx, 1, sz, f.out.yaw, f.x(bedU, bedV), bedY, f.z(bedU, bedV)));
		}

		String bannerColor(String trade) {
			return switch (trade) {
				case "grocer" -> "yellow";
				case "armorer" -> "gray";
				case "mason" -> "light_gray";
				case "jeweler" -> "purple";
				case "tailor" -> "blue";
				case "spice_merchant" -> "orange";
				case "furrier" -> "white";
				default -> p.bannerBase;
			};
		}

		/** Lo que tiene cada oficio detrás del mostrador (contra la pared izquierda, sin cortar el paso). */
		void decor(Frame f, String trade, int[][] spots) {
			String[] items = switch (trade) {
				case "grocer" -> new String[] {"hay_block", "composter", "barrel[facing=up]", "pumpkin"};
				case "armorer" -> new String[] {"anvil[facing=" + f.right() + "]", "smithing_table", "grindstone[face=floor,facing=" + f.right() + "]"};
				case "mason" -> new String[] {"stonecutter[facing=" + f.right() + "]", "chiseled_stone_bricks", "polished_andesite"};
				case "jeweler" -> new String[] {"bookshelf", "decorated_pot", "lectern[facing=" + f.right() + "]"};
				case "tailor" -> new String[] {"loom[facing=" + f.right() + "]", "white_wool", "red_wool", "blue_wool"};
				case "spice_merchant" -> new String[] {"barrel[facing=up]", "composter", "decorated_pot", "hay_block"};
				case "furrier" -> new String[] {"white_wool", "loom[facing=" + f.right() + "]", "brown_wool", "barrel[facing=up]"};
				default -> new String[] {"barrel[facing=up]"};
			};
			for (int i = 0; i < spots.length; i++) {
				int u = spots[i][0];
				int v = spots[i][1];
				set(f.x(u, v), 1, f.z(u, v), items[i % items.length]);
			}
		}

		// -------------------------------------------------------------- cuartel, huerto y pozo

		void barracks(Lot lot) {
			int bw = Math.min(lot.width() - 1, 13);
			int bd = Math.min(lot.depth(), 9);
			Frame f = new Frame(lot.rect, lot.facing);
			shell(f, bw, bd, 1, p.wall[0], p.wall[0], pick(p.frame), pick(p.floor));
			int doorU = bw / 2;
			door(f, doorU, pick(p.doors));
			windows(f, bw, bd, 1, doorU);
			for (int u = 1; u + 1 <= bw - 2; u += 2) {
				bed(f, u, bd - 3, u, bd - 2, 1, p.beds[0]);
			}
			set(f.x(1, 1), 1, f.z(1, 1), "chest[facing=" + f.right() + "]");
			plan.loot(f.x(1, 1), 1, f.z(1, 1), "minecraft:chests/village/village_weaponsmith");
			set(f.x(bw - 2, 1), 1, f.z(bw - 2, 1), "smithing_table");
			torch(f, doorU + 1, 1, 2, f.inward());
			roof(f, bw, bd, 5);
			plan.guard(new CityPlan.Guard("warrior", f.x(doorU, 2), 1, f.z(doorU, 2), f.out.yaw));
			plan.guard(new CityPlan.Guard("crossbowman", f.x(doorU, -1), 1, f.z(doorU, -1), f.out.yaw));
			desc.append(", cuartel");
		}

		void garden(Lot lot) {
			Frame f = new Frame(lot.rect, lot.facing);
			int bw = lot.width() - 1;
			int bd = Math.min(lot.depth(), 9);
			String[] crops = {"wheat", "carrots", "potatoes", "beetroots"};
			String crop = pick(crops);
			for (int u = 0; u < bw; u++) {
				for (int v = 0; v < bd; v++) {
					int x = f.x(u, v);
					int z = f.z(u, v);
					mark(x, z, BUILT);
					boolean edge = u == 0 || v == 0 || u == bw - 1 || v == bd - 1;
					if (edge) {
						set(x, 1, z, u == bw / 2 && v == 0 ? p.fenceGate + "[facing=" + f.outward() + ",open=false]" : p.fence);
					} else if (u == bw / 2 && style != Style.SNOW) {
						found(x, 0, z, "water");
					} else {
						found(x, 0, z, "farmland[moisture=7]");
						int age = crop.equals("beetroots") ? rnd.nextInt(4) : rnd.nextInt(8);
						set(x, 1, z, crop + "[age=" + age + "]");
					}
				}
			}
		}

		void well(Lot lot) {
			Frame f = new Frame(lot.rect, lot.facing);
			int cu = (lot.width() - 1) / 2;
			int cv = Math.min(lot.depth(), 6) / 2;
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					int x = f.x(cu + du, cv + dv);
					int z = f.z(cu + du, cv + dv);
					mark(x, z, BUILT);
					if (du == 0 && dv == 0) {
						found(x, -1, z, p.foundation);
						set(x, 0, z, "water");
					} else {
						found(x, 0, z, p.foundation);
						set(x, 1, z, p.foundation.equals("sandstone") ? "sandstone_wall" : "cobblestone_wall");
						if (Math.abs(du) == 1 && Math.abs(dv) == 1) {
							set(x, 2, z, p.fence);
							set(x, 3, z, p.roofs[0].slab);
						}
					}
				}
			}
			set(f.x(cu, cv - 1), 3, f.z(cu, cv - 1), p.roofs[0].slab);
			set(f.x(cu, cv + 1), 3, f.z(cu, cv + 1), p.roofs[0].slab);
			set(f.x(cu - 1, cv), 3, f.z(cu - 1, cv), p.roofs[0].slab);
			set(f.x(cu + 1, cv), 3, f.z(cu + 1, cv), p.roofs[0].slab);
			set(f.x(cu, cv), 3, f.z(cu, cv), p.roofs[0].slab);
		}

		/** Caminos de tierra que salen de cada puerta (solo sobre suelo natural). */
		void gateRoads() {
			for (Dir g : gates) {
				int h = g.dx != 0 ? hx : hz;
				for (int k = 1; k <= 10; k++) {
					for (int t = -AVENUE; t <= AVENUE; t++) {
						int x = g.dx != 0 ? g.dx * (h + k) : t;
						int z = g.dz != 0 ? g.dz * (h + k) : t;
						plan.set(x, 0, z, style == Style.DESERT ? "smooth_sandstone" : (Math.abs(t) == AVENUE ? "dirt_path" : "gravel"),
								CityPlan.GROUND);
					}
				}
			}
		}
	}

	/** La ciudad de esa semilla y estilo (siempre la misma). */
	public static CityPlan design(long seed, Style style) {
		return new Builder(seed, style).build();
	}
}

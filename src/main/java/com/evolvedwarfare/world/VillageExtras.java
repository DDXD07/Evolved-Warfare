package com.evolvedwarfare.world;

import com.evolvedwarfare.mixin.PoolStructurePieceAccessor;
import com.evolvedwarfare.mixin.SinglePoolElementAccessor;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import org.jetbrains.annotations.Nullable;

/**
 * Calcula, al generarse una aldea vanilla, su empalizada, sus puertas, los caminos de acceso y las
 * farolas de las calles. El resultado se guarda en una {@link VillageExtrasPiece} que lo coloca chunk a chunk.
 *
 * <ul>
 *   <li><b>Empalizada</b>: rodea todas las piezas de la aldea a {@value #WALL_MARGIN} bloques. Se hace un
 *       "cierre" (dilatar y erosionar {@value #CLOSING_RADIUS}) para que no se meta en cada hueco entre casas.</li>
 *   <li><b>Puertas</b>: en cada dirección (N, S, E, O), donde una calle llega más cerca de la empalizada sin
 *       atravesar casas. Llevan postes altos con faroles y un camino empedrado hasta la calle.</li>
 *   <li><b>Farolas</b>: en el borde de las calles, separadas al menos {@value #LAMP_SPACING} bloques, nunca
 *       delante de una puerta ni en un cruce.</li>
 * </ul>
 *
 * <p>El mismo algoritmo está en {@code tools/ew_village.py} para hacer vistas previas.</p>
 */
public final class VillageExtras {
	public static final int CLOSING_RADIUS = 10;
	public static final int WALL_MARGIN = 4;
	public static final int LAMP_SPACING = 8;
	public static final int WALL_LANTERN_SPACING = 8;
	private static final int GATE_CANDIDATES = 60;
	private static final int MIN_GATE_DISTANCE = 15;
	private static final float SQRT2 = (float) Math.sqrt(2.0);

	private VillageExtras() {
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private record Cell(int x, int z) {
	}

	/** El terreno (de la generación, no de lo ya puesto): la altura del suelo y si hay agua encima. */
	public interface Terrain {
		int ground(int x, int z);

		boolean dry(int x, int z);
	}

	@Nullable
	public static VillageExtrasPiece create(VillageStyle style, List<StructurePiece> pieces, StructureTemplateManager templates) {
		return create(style, pieces, templates, null);
	}

	@Nullable
	public static VillageExtrasPiece create(VillageStyle style, List<StructurePiece> pieces, StructureTemplateManager templates, @Nullable Terrain terrain) {
		if (pieces.isEmpty()) {
			return null;
		}
		LongOpenHashSet path = new LongOpenHashSet();
		LongOpenHashSet noLamp = new LongOpenHashSet();
		List<Cell> pathCells = new ArrayList<>();
		List<BlockBox> houseBoxes = new ArrayList<>();

		// ---------------------------------------------------------------- calles (desde sus plantillas)
		for (StructurePiece piece : pieces) {
			StructureTemplate template = streetTemplate(piece, templates);
			if (template == null) {
				houseBoxes.add(piece.getBoundingBox());
				continue;
			}
			PoolStructurePiece pool = (PoolStructurePiece) piece;
			StructurePlacementData placement = new StructurePlacementData()
					.setRotation(((PoolStructurePieceAccessor) pool).evolvedwarfare$getRotation());
			for (StructureTemplate.StructureBlockInfo info : template.getInfosForBlock(pool.getPos(), placement, style.streetBlock())) {
				if (path.add(key(info.pos().getX(), info.pos().getZ()))) {
					pathCells.add(new Cell(info.pos().getX(), info.pos().getZ()));
				}
			}
			for (StructureTemplate.StructureBlockInfo info : template.getInfosForBlock(pool.getPos(), placement, Blocks.JIGSAW)) {
				NbtCompound nbt = info.nbt();
				if (nbt == null) {
					continue;
				}
				if (nbt.getString("pool").contains("houses") || nbt.getString("name").equals("minecraft:street")) {
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							noLamp.add(key(info.pos().getX() + dx, info.pos().getZ() + dz));
						}
					}
				}
			}
		}

		// ---------------------------------------------------------------- farolas
		List<Cell> edges = new ArrayList<>();
		for (Cell c : pathCells) {
			if (!path.contains(key(c.x + 1, c.z)) || !path.contains(key(c.x - 1, c.z))
					|| !path.contains(key(c.x, c.z + 1)) || !path.contains(key(c.x, c.z - 1))) {
				edges.add(c);
			}
		}
		edges.sort(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z));
		List<Cell> lamps = spaced(edges, LAMP_SPACING, c -> !noLamp.contains(key(c.x, c.z)));

		// ---------------------------------------------------------------- contorno de la empalizada
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		int y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
		for (StructurePiece piece : pieces) {
			BlockBox b = piece.getBoundingBox();
			x0 = Math.min(x0, b.getMinX());
			z0 = Math.min(z0, b.getMinZ());
			x1 = Math.max(x1, b.getMaxX());
			z1 = Math.max(z1, b.getMaxZ());
			y0 = Math.min(y0, b.getMinY());
			y1 = Math.max(y1, b.getMaxY());
		}
		int pad = CLOSING_RADIUS + WALL_MARGIN + 3;
		int gx0 = x0 - pad;
		int gz0 = z0 - pad;
		int w = (x1 - x0 + 1) + 2 * pad;
		int h = (z1 - z0 + 1) + 2 * pad;
		boolean[] occupied = new boolean[w * h];
		for (StructurePiece piece : pieces) {
			BlockBox b = piece.getBoundingBox();
			for (int z = b.getMinZ(); z <= b.getMaxZ(); z++) {
				for (int x = b.getMinX(); x <= b.getMaxX(); x++) {
					occupied[(z - gz0) * w + (x - gx0)] = true;
				}
			}
		}
		float[] fromVillage = chamfer(w, h, occupied);
		boolean[] outsideDilated = new boolean[w * h];
		for (int i = 0; i < w * h; i++) {
			outsideDilated[i] = fromVillage[i] > CLOSING_RADIUS + WALL_MARGIN;
		}
		float[] fromOutside = chamfer(w, h, outsideDilated);
		boolean[] region = new boolean[w * h];
		for (int i = 0; i < w * h; i++) {
			region[i] = fromOutside[i] > CLOSING_RADIUS;
		}
		boolean[] outside = floodOutside(w, h, region);
		List<Cell> wall = new ArrayList<>();
		for (int z = 1; z < h - 1; z++) {
			for (int x = 1; x < w - 1; x++) {
				if (!region[z * w + x]) {
					continue;
				}
				boolean border = false;
				for (int dz = -1; dz <= 1 && !border; dz++) {
					for (int dx = -1; dx <= 1; dx++) {
						if (outside[(z + dz) * w + x + dx]) {
							border = true;
							break;
						}
					}
				}
				if (border) {
					wall.add(new Cell(x + gx0, z + gz0));
				}
			}
		}
		if (wall.isEmpty()) {
			return null;
		}
		LongOpenHashSet wallSet = new LongOpenHashSet();
		for (Cell c : wall) {
			wallSet.add(key(c.x, c.z));
		}

		// ---------------------------------------------------------------- puertas y caminos de acceso
		List<Cell> gates = new ArrayList<>();
		LongOpenHashSet gateCells = new LongOpenHashSet();
		LongOpenHashSet postCells = new LongOpenHashSet();
		LongOpenHashSet road = new LongOpenHashSet();
		int[][] directions = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
		for (int[] d : directions) {
			List<Cell> candidates = new ArrayList<>(pathCells);
			candidates.sort(Comparator.<Cell>comparingInt(c -> -(c.x * d[0] + c.z * d[1]))
					.thenComparingInt(Cell::x).thenComparingInt(Cell::z));
			int tries = Math.min(GATE_CANDIDATES, candidates.size());
			for (int i = 0; i < tries; i++) {
				Cell p = candidates.get(i);
				Cell gate = nearest(wall, p);
				List<Cell> segment = line(p, gate);
				boolean blocked = false;
				for (int k = 1; k < segment.size() && !blocked; k++) {
					blocked = inAny(houseBoxes, segment.get(k));
				}
				if (blocked) {
					continue;
				}
				boolean tooClose = false;
				for (Cell g : gates) {
					tooClose |= dist2(g, gate) < MIN_GATE_DISTANCE * MIN_GATE_DISTANCE;
				}
				if (tooClose) {
					break;
				}
				gates.add(gate);
				for (Cell c : wall) {
					int dd = dist2(c, gate);
					if (dd <= 2) {
						gateCells.add(key(c.x, c.z));
					} else if (dd <= 6) {
						postCells.add(key(c.x, c.z));
					}
				}
				// camino de 3 de ancho desde la calle hasta 3 bloques más allá de la puerta
				int vx = gate.x - p.x;
				int vz = gate.z - p.z;
				double n = Math.max(1.0, Math.sqrt((double) vx * vx + (double) vz * vz));
				Cell far = new Cell((int) Math.round(gate.x + vx / n * 3), (int) Math.round(gate.z + vz / n * 3));
				boolean alongX = Math.abs(vx) >= Math.abs(vz);
				for (Cell c : line(p, far)) {
					for (int s = -1; s <= 1; s++) {
						Cell cc = alongX ? new Cell(c.x, c.z + s) : new Cell(c.x + s, c.z);
						long k = key(cc.x, cc.z);
						if (!path.contains(k) && !inAny(houseBoxes, cc) && (!wallSet.contains(k) || gateCells.contains(k))) {
							road.add(k);
						}
					}
				}
				break;
			}
		}

		// ---------------------------------------------------------------- faroles sobre la empalizada
		List<Cell> plainWall = new ArrayList<>();
		for (Cell c : wall) {
			long k = key(c.x, c.z);
			if (!gateCells.contains(k)) {
				plainWall.add(c);
			}
		}
		List<Cell> sortedWall = new ArrayList<>(plainWall);
		sortedWall.sort(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z));
		List<Cell> lanterns = spaced(sortedWall, WALL_LANTERN_SPACING, c -> !postCells.contains(key(c.x, c.z)));

		// ---------------------------------------------------------------- los edificios nuevos (mercado, taberna, granero, atalaya)
		int[] buildings = new int[0];
		if (terrain != null) {
			BlockBox center = pieces.get(0).getBoundingBox();
			buildings = planBuildings(style, terrain, gx0, gz0, w, h, region, houseBoxes, path, pathCells, road,
					(center.getMinX() + center.getMaxX()) / 2, (center.getMinZ() + center.getMaxZ()) / 2);
		}

		// ---------------------------------------------------------------- caja y pieza
		int bx0 = Integer.MAX_VALUE, bz0 = Integer.MAX_VALUE, bx1 = Integer.MIN_VALUE, bz1 = Integer.MIN_VALUE;
		for (Cell c : wall) {
			bx0 = Math.min(bx0, c.x);
			bz0 = Math.min(bz0, c.z);
			bx1 = Math.max(bx1, c.x);
			bz1 = Math.max(bz1, c.z);
		}
		for (long k : road) {
			int x = (int) (k >> 32);
			int z = (int) k;
			bx0 = Math.min(bx0, x);
			bz0 = Math.min(bz0, z);
			bx1 = Math.max(bx1, x);
			bz1 = Math.max(bz1, z);
		}
		int top = y1 + 16, bottom = y0 - 16;
		for (int i = 0; i + 4 < buildings.length; i += 5) {
			top = Math.max(top, buildings[i + 4] + 20);
			bottom = Math.min(bottom, buildings[i + 4] - 10);
		}
		BlockBox box = new BlockBox(bx0, bottom, bz0, bx1, top, bz1);
		return new VillageExtrasPiece(style, box, pack(plainWall, c -> !postCells.contains(key(c.x, c.z))),
				packKeys(postCells), packKeys(road), pack(lamps, c -> true), pack(lanterns, c -> true), buildings, pack(pathCells, c -> true));
	}

	// ==================================================================== los edificios

	/** Un sitio posible para un edificio: la esquina, el giro y lo bueno que es (menos es mejor). */
	private record Spot(int x, int z, int rot, double score) {
	}

	/**
	 * Busca sitio dentro de la empalizada (a 3 bloques de ella como poco) para la plaza del mercado y la taberna (lo más
	 * cerca del centro), el granero y una o dos atalayas (cerca de la empalizada), sin pisar casas ni calles, con la entrada
	 * a una calle (menos las atalayas) y en terreno seco y casi llano. Devuelve los elementos de 5 en 5.
	 */
	private static int[] planBuildings(VillageStyle style, Terrain terrain, int gx0, int gz0, int w, int h, boolean[] region, List<BlockBox> houses,
			LongOpenHashSet path, List<Cell> pathCells, LongOpenHashSet road, int cx, int cz) {
		boolean[] notRegion = new boolean[w * h];
		for (int i = 0; i < w * h; i++) {
			notRegion[i] = !region[i];
		}
		float[] inDist = chamfer(w, h, notRegion);
		boolean[] blocked = new boolean[w * h];
		for (int i = 0; i < w * h; i++) {
			blocked[i] = inDist[i] < 3;
		}
		for (BlockBox b : houses) {
			fill(blocked, gx0, gz0, w, h, b.getMinX() - 1, b.getMinZ() - 1, b.getMaxX() + 1, b.getMaxZ() + 1);
		}
		for (LongOpenHashSet set : List.of(path, road)) {
			for (long k : set) {
				int x = (int) (k >> 32), z = (int) k;
				fill(blocked, gx0, gz0, w, h, x, z, x, z);
			}
		}
		IntArrayList out = new IntArrayList();
		int[] order = {VillageBuildings.MARKET, VillageBuildings.TAVERN, VillageBuildings.GRANARY, VillageBuildings.TOWER, VillageBuildings.TOWER};
		List<int[]> towers = new ArrayList<>();
		for (int type : order) {
			if (type == VillageBuildings.TOWER && !towers.isEmpty() && w * h < 9000) {
				break;
			}
			int[] got = place(type, terrain, gx0, gz0, w, h, blocked, inDist, path, cx, cz, towers);
			if (got == null && type == VillageBuildings.MARKET) {
				got = place(VillageBuildings.MARKET_SMALL, terrain, gx0, gz0, w, h, blocked, inDist, path, cx, cz, towers);
			}
			if (got == null) {
				continue;
			}
			out.addElements(out.size(), got);
			int t = got[0], fw = (got[3] & 1) == 0 ? VillageBuildings.width(t) : VillageBuildings.depth(t);
			int fd = (got[3] & 1) == 0 ? VillageBuildings.depth(t) : VillageBuildings.width(t);
			fill(blocked, gx0, gz0, w, h, got[1] - 2, got[2] - 2, got[1] + fw + 1, got[2] + fd + 1);
			if (t == VillageBuildings.TOWER) {
				towers.add(new int[] {got[1], got[2]});
			}
		}
		planDecor(style, terrain, gx0, gz0, w, h, blocked, path, pathCells, out);
		return out.toIntArray();
	}

	/** Separación mínima entre decoraciones (bloques). */
	private static final int DECOR_SPACING = 6;

	/**
	 * Las decoraciones de las calles (barriles, heno, leña, flores, bancos, espantapájaros, cajas, vasijas, hogueras): en
	 * los huecos libres pegados a una calle, con el frente a ella, separadas entre sí y en terreno llano.
	 */
	private static void planDecor(VillageStyle style, Terrain terrain, int gx0, int gz0, int w, int h, boolean[] blocked, LongOpenHashSet path,
			List<Cell> pathCells, IntArrayList out) {
		int[] kinds = VillageBuildings.decorations(style);
		int max = Math.min(40, pathCells.size() / 12);
		List<Cell> chosen = new ArrayList<>();
		List<Cell> cells = new ArrayList<>(pathCells);
		cells.sort(Comparator.comparingInt((Cell c) -> Math.floorMod(c.x * 734287 + c.z * 912931, 1009)).thenComparingInt(Cell::x).thenComparingInt(Cell::z));
		// hacia la calle: la decoración está en (x + d, z + d) y su frente mira a la calle
		int[][] dirs = {{0, 1, 0}, {-1, 0, 1}, {0, -1, 2}, {1, 0, 3}};
		for (Cell street : cells) {
			if (chosen.size() >= max) {
				break;
			}
			for (int[] d : dirs) {
				int x = street.x + d[0], z = street.z + d[1];
				int rot = d[2];
				int gi = (z - gz0) * w + (x - gx0);
				if (x - gx0 < 0 || z - gz0 < 0 || x - gx0 >= w || z - gz0 >= h || blocked[gi] || path.contains(key(x, z))) {
					continue;
				}
				boolean far = true;
				for (Cell o : chosen) {
					far &= dist2(o, new Cell(x, z)) >= DECOR_SPACING * DECOR_SPACING;
				}
				if (!far) {
					continue;
				}
				int type = kinds[Math.floorMod(x * 31 + z * 17, kinds.length)];
				int bw = VillageBuildings.width(type), bd = VillageBuildings.depth(type);
				// la esquina del rectángulo girado para que (x, z) sea su (0, 0) local
				int x0 = switch (rot) {
					case 1 -> x - (bd - 1);
					case 2 -> x - (bw - 1);
					default -> x;
				};
				int z0 = switch (rot) {
					case 2 -> z - (bd - 1);
					case 3 -> z - (bw - 1);
					default -> z;
				};
				int fw = (rot & 1) == 0 ? bw : bd, fd = (rot & 1) == 0 ? bd : bw;
				boolean free = true;
				int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
				for (int zz = z0; zz < z0 + fd && free; zz++) {
					for (int xx = x0; xx < x0 + fw; xx++) {
						int i = (zz - gz0) * w + (xx - gx0);
						if (xx - gx0 < 0 || zz - gz0 < 0 || xx - gx0 >= w || zz - gz0 >= h || blocked[i] || path.contains(key(xx, zz))) {
							free = false;
							break;
						}
					}
				}
				if (!free) {
					continue;
				}
				for (int[] p : new int[][] {{x0, z0}, {x0 + fw - 1, z0 + fd - 1}}) {
					int y = terrain.ground(p[0], p[1]);
					lo = Math.min(lo, y);
					hi = Math.max(hi, y);
				}
				if (hi - lo > 1 || !terrain.dry(x, z)) {
					continue;
				}
				out.add(type);
				out.add(x0);
				out.add(z0);
				out.add(rot);
				out.add(terrain.ground(x, z));
				fill(blocked, gx0, gz0, w, h, x0 - 1, z0 - 1, x0 + fw, z0 + fd);
				chosen.add(new Cell(x, z));
				break;
			}
		}
	}

	private static void fill(boolean[] grid, int gx0, int gz0, int w, int h, int x0, int z0, int x1, int z1) {
		for (int z = Math.max(0, z0 - gz0); z <= Math.min(h - 1, z1 - gz0); z++) {
			for (int x = Math.max(0, x0 - gx0); x <= Math.min(w - 1, x1 - gx0); x++) {
				grid[z * w + x] = true;
			}
		}
	}

	@Nullable
	private static int[] place(int type, Terrain terrain, int gx0, int gz0, int w, int h, boolean[] blocked, float[] inDist, LongOpenHashSet path,
			int cx, int cz, List<int[]> towers) {
		// sumas acumuladas de lo ocupado, para mirar rectángulos de golpe
		int[] sum = new int[(w + 1) * (h + 1)];
		for (int z = 0; z < h; z++) {
			for (int x = 0; x < w; x++) {
				sum[(z + 1) * (w + 1) + x + 1] = (blocked[z * w + x] ? 1 : 0) + sum[z * (w + 1) + x + 1] + sum[(z + 1) * (w + 1) + x] - sum[z * (w + 1) + x];
			}
		}
		int bw = VillageBuildings.width(type), bd = VillageBuildings.depth(type);
		boolean tower = type == VillageBuildings.TOWER;
		List<Spot> spots = new ArrayList<>();
		for (int rot = 0; rot < 4; rot++) {
			int fw = (rot & 1) == 0 ? bw : bd, fd = (rot & 1) == 0 ? bd : bw;
			for (int z = 0; z + fd <= h; z++) {
				for (int x = 0; x + fw <= w; x++) {
					int n = sum[(z + fd) * (w + 1) + x + fw] - sum[z * (w + 1) + x + fw] - sum[(z + fd) * (w + 1) + x] + sum[z * (w + 1) + x];
					if (n > 0) {
						continue;
					}
					int wx = x + gx0, wz = z + gz0;
					double score;
					if (tower) {
						float d = inDist[(z + fd / 2) * w + x + fw / 2];
						if (d > 8) {
							continue;
						}
						boolean near = false;
						for (int[] t : towers) {
							near |= (t[0] - wx) * (t[0] - wx) + (t[1] - wz) * (t[1] - wz) < 40 * 40;
						}
						if (near) {
							continue;
						}
						score = d + ((wx * 31 + wz * 17) & 7) * 0.1;
					} else {
						if (frontStreet(path, rot, wx, wz, fw, fd) < 2) {
							continue;
						}
						double dx = wx + fw / 2.0 - cx, dz = wz + fd / 2.0 - cz;
						score = dx * dx + dz * dz;
						if (type == VillageBuildings.GRANARY) {
							score = Math.abs(Math.sqrt(score) - 30);
						}
					}
					spots.add(new Spot(wx, wz, rot, score));
				}
			}
		}
		spots.sort(Comparator.comparingDouble(Spot::score));
		int tries = 0;
		for (Spot sp : spots) {
			if (++tries > 40) {
				break;
			}
			int fw = (sp.rot & 1) == 0 ? bw : bd, fd = (sp.rot & 1) == 0 ? bd : bw;
			int[][] probes = {{sp.x, sp.z}, {sp.x + fw - 1, sp.z}, {sp.x, sp.z + fd - 1}, {sp.x + fw - 1, sp.z + fd - 1}, {sp.x + fw / 2, sp.z + fd / 2}};
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			boolean dry = true;
			for (int[] p : probes) {
				int y = terrain.ground(p[0], p[1]);
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
				dry &= terrain.dry(p[0], p[1]);
			}
			if (dry && hi - lo <= 3) {
				return new int[] {type, sp.x, sp.z, sp.rot, terrain.ground(sp.x + fw / 2, sp.z + fd / 2)};
			}
		}
		return null;
	}

	/** Cuántas casillas de calle hay delante de la entrada (a 1 o 2 bloques). */
	private static int frontStreet(LongOpenHashSet path, int rot, int x0, int z0, int fw, int fd) {
		int n = 0;
		for (int k = 1; k <= 2; k++) {
			switch (rot & 3) {
				case 0 -> {
					for (int x = x0; x < x0 + fw; x++) {
						n += path.contains(key(x, z0 - k)) ? 1 : 0;
					}
				}
				case 1 -> {
					for (int z = z0; z < z0 + fd; z++) {
						n += path.contains(key(x0 + fw - 1 + k, z)) ? 1 : 0;
					}
				}
				case 2 -> {
					for (int x = x0; x < x0 + fw; x++) {
						n += path.contains(key(x, z0 + fd - 1 + k)) ? 1 : 0;
					}
				}
				default -> {
					for (int z = z0; z < z0 + fd; z++) {
						n += path.contains(key(x0 - k, z)) ? 1 : 0;
					}
				}
			}
		}
		return n;
	}

	// ==================================================================== utilidades

	/** Plantilla de la pieza si es una calle de aldea; null si es cualquier otra cosa. */
	@Nullable
	private static StructureTemplate streetTemplate(StructurePiece piece, StructureTemplateManager templates) {
		if (!(piece instanceof PoolStructurePiece pool)) {
			return null;
		}
		StructurePoolElement element = pool.getPoolElement();
		if (!(element instanceof SinglePoolElement single)) {
			return null;
		}
		Either<Identifier, StructureTemplate> location = ((SinglePoolElementAccessor) single).evolvedwarfare$getLocation();
		Optional<Identifier> id = location.left();
		if (id.isEmpty() || !id.get().getPath().contains("/streets/")) {
			return null;
		}
		return templates.getTemplateOrBlank(id.get());
	}

	private interface CellFilter {
		boolean test(Cell cell);
	}

	/** Elige celdas en orden, separadas al menos 'spacing' bloques entre sí. */
	private static List<Cell> spaced(List<Cell> cells, int spacing, CellFilter filter) {
		List<Cell> chosen = new ArrayList<>();
		int s2 = spacing * spacing;
		for (Cell c : cells) {
			if (!filter.test(c)) {
				continue;
			}
			boolean ok = true;
			for (Cell o : chosen) {
				if (dist2(c, o) < s2) {
					ok = false;
					break;
				}
			}
			if (ok) {
				chosen.add(c);
			}
		}
		return chosen;
	}

	private static int dist2(Cell a, Cell b) {
		int dx = a.x - b.x;
		int dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	private static Cell nearest(List<Cell> cells, Cell p) {
		Cell best = cells.get(0);
		int bestD = Integer.MAX_VALUE;
		for (Cell c : cells) {
			int d = dist2(c, p);
			if (d < bestD) {
				bestD = d;
				best = c;
			}
		}
		return best;
	}

	private static List<Cell> line(Cell a, Cell b) {
		int n = Math.max(Math.abs(b.x - a.x), Math.abs(b.z - a.z));
		List<Cell> out = new ArrayList<>(n + 1);
		if (n == 0) {
			out.add(a);
			return out;
		}
		for (int k = 0; k <= n; k++) {
			out.add(new Cell((int) Math.round(a.x + (b.x - a.x) * (double) k / n),
					(int) Math.round(a.z + (b.z - a.z) * (double) k / n)));
		}
		return out;
	}

	private static boolean inAny(List<BlockBox> boxes, Cell c) {
		for (BlockBox b : boxes) {
			if (c.x >= b.getMinX() && c.x <= b.getMaxX() && c.z >= b.getMinZ() && c.z <= b.getMaxZ()) {
				return true;
			}
		}
		return false;
	}

	/** Distancia aproximada (chamfer 1-√2) desde las celdas marcadas. */
	private static float[] chamfer(int w, int h, boolean[] seeds) {
		float[] d = new float[w * h];
		for (int i = 0; i < d.length; i++) {
			d[i] = seeds[i] ? 0.0F : 1.0E9F;
		}
		for (int z = 0; z < h; z++) {
			for (int x = 0; x < w; x++) {
				int i = z * w + x;
				float v = d[i];
				if (x > 0) v = Math.min(v, d[i - 1] + 1);
				if (z > 0) {
					v = Math.min(v, d[i - w] + 1);
					if (x > 0) v = Math.min(v, d[i - w - 1] + SQRT2);
					if (x < w - 1) v = Math.min(v, d[i - w + 1] + SQRT2);
				}
				d[i] = v;
			}
		}
		for (int z = h - 1; z >= 0; z--) {
			for (int x = w - 1; x >= 0; x--) {
				int i = z * w + x;
				float v = d[i];
				if (x < w - 1) v = Math.min(v, d[i + 1] + 1);
				if (z < h - 1) {
					v = Math.min(v, d[i + w] + 1);
					if (x < w - 1) v = Math.min(v, d[i + w + 1] + SQRT2);
					if (x > 0) v = Math.min(v, d[i + w - 1] + SQRT2);
				}
				d[i] = v;
			}
		}
		return d;
	}

	/** Celdas fuera de la región alcanzables desde el borde de la cuadrícula. */
	private static boolean[] floodOutside(int w, int h, boolean[] region) {
		boolean[] outside = new boolean[w * h];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int x = 0; x < w; x++) {
			seed(outside, region, queue, x);
			seed(outside, region, queue, (h - 1) * w + x);
		}
		for (int z = 0; z < h; z++) {
			seed(outside, region, queue, z * w);
			seed(outside, region, queue, z * w + w - 1);
		}
		while (!queue.isEmpty()) {
			int i = queue.poll();
			int x = i % w;
			int z = i / w;
			if (x > 0) seed(outside, region, queue, i - 1);
			if (x < w - 1) seed(outside, region, queue, i + 1);
			if (z > 0) seed(outside, region, queue, i - w);
			if (z < h - 1) seed(outside, region, queue, i + w);
		}
		return outside;
	}

	private static void seed(boolean[] outside, boolean[] region, ArrayDeque<Integer> queue, int i) {
		if (!region[i] && !outside[i]) {
			outside[i] = true;
			queue.add(i);
		}
	}

	private static int[] pack(List<Cell> cells, CellFilter filter) {
		IntArrayList out = new IntArrayList();
		for (Cell c : cells) {
			if (filter.test(c)) {
				out.add(c.x);
				out.add(c.z);
			}
		}
		return out.toIntArray();
	}

	private static int[] packKeys(LongOpenHashSet keys) {
		long[] sorted = keys.toLongArray();
		java.util.Arrays.sort(sorted);
		int[] out = new int[sorted.length * 2];
		for (int i = 0; i < sorted.length; i++) {
			out[2 * i] = (int) (sorted[i] >> 32);
			out[2 * i + 1] = (int) sorted[i];
		}
		return out;
	}
}

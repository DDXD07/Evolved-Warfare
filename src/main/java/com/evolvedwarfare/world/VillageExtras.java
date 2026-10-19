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

	@Nullable
	public static VillageExtrasPiece create(VillageStyle style, List<StructurePiece> pieces, StructureTemplateManager templates) {
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
		BlockBox box = new BlockBox(bx0, y0 - 16, bz0, bx1, y1 + 16, bz1);
		return new VillageExtrasPiece(style, box, pack(plainWall, c -> !postCells.contains(key(c.x, c.z))),
				packKeys(postCells), packKeys(road), pack(lamps, c -> true), pack(lanterns, c -> true));
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

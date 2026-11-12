package com.evolvedwarfare.train;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Construir en la plataforma del tren, como en ARK (fase 52): la plataforma es una rejilla de 2 x 4 casillas de 1,5 bloques
 * y dos plantas de 2,5. Las paredes (lisas, con puerta, con ventana) y las barandillas van en los bordes de las casillas;
 * los techos, los tejados inclinados y las escaleras, en las casillas. Todo se hace de paja, madera, piedra o metal (cada
 * una aguanta más golpes que la anterior). Lo de arriba necesita apoyo: un techo, una pared (o un techo al lado); una
 * pared de la segunda planta, un techo debajo. Si se quita el apoyo, se cae (y suelta su pieza).
 * <p>
 * Coordenadas de la plataforma (en bloques): x a lo ancho, z hacia delante, y desde la vía.
 */
public final class Building {
	public static final double CELL = 1.5, DECK = 1.0, LEVEL = 2.5;
	public static final int NX = 2, NZ = 4, LEVELS = 2;
	public static final double X0 = -NX * CELL / 2.0, Z0 = -NZ * CELL / 2.0;

	private Building() {
	}

	public enum Material {
		STRAW("straw", 30), WOOD("wood", 80), STONE("stone", 200), METAL("metal", 500);

		public final String id;
		public final int hp;

		Material(String id, int hp) {
			this.id = id;
			this.hp = hp;
		}
	}

	public enum Kind {
		WALL("wall", true), DOORWAY("doorway", true), WINDOW("window", true), CEILING("ceiling", false), ROOF("roof", false), STAIRS("stairs", false),
		RAILING("railing", true);

		public final String id;
		/** Va en un borde (si no, en una casilla). */
		public final boolean edge;

		Kind(String id, boolean edge) {
			this.id = id;
			this.edge = edge;
		}

		/** ¿Aguanta un techo encima? */
		public boolean wall() {
			return this == WALL || this == DOORWAY || this == WINDOW;
		}
	}

	/** Dónde va una pieza: planta, casilla o borde (a, b) y giro (en los bordes, el eje: 0 a lo ancho, 1 a lo largo). */
	public record Slot(Kind kind, int level, int a, int b, int rot) {
		String key() {
			return kind.edge ? "e" + level + rot + a + "_" + b : kind == Kind.STAIRS ? "s" + level + a + "_" + b : "h" + level + a + "_" + b;
		}
	}

	/** Una pieza puesta. */
	public static final class Placed {
		public final Kind kind;
		public final Material material;
		public final int level, a, b, rot;
		public float hp;
		public boolean open;

		public Placed(Kind kind, Material material, int level, int a, int b, int rot) {
			this.kind = kind;
			this.material = material;
			this.level = level;
			this.a = a;
			this.b = b;
			this.rot = rot;
			this.hp = material.hp * (kind == Kind.RAILING ? 0.5F : 1.0F);
		}

		public Slot slot() {
			return new Slot(kind, level, a, b, rot);
		}

		public float maxHp() {
			return material.hp * (kind == Kind.RAILING ? 0.5F : 1.0F);
		}

		public NbtCompound write() {
			NbtCompound n = new NbtCompound();
			n.putByte("K", (byte) kind.ordinal());
			n.putByte("M", (byte) material.ordinal());
			n.putByte("L", (byte) level);
			n.putByte("A", (byte) a);
			n.putByte("B", (byte) b);
			n.putByte("R", (byte) rot);
			n.putFloat("Hp", hp);
			n.putBoolean("Open", open);
			return n;
		}

		@Nullable
		public static Placed read(NbtCompound n) {
			int k = n.getByte("K"), m = n.getByte("M");
			if (k < 0 || k >= Kind.values().length || m < 0 || m >= Material.values().length) {
				return null;
			}
			Placed p = new Placed(Kind.values()[k], Material.values()[m], n.getByte("L"), n.getByte("A"), n.getByte("B"), n.getByte("R"));
			p.hp = n.contains("Hp") ? n.getFloat("Hp") : p.maxHp();
			p.open = n.getBoolean("Open");
			return p;
		}
	}

	// ------------------------------------------------------------------ lo que cuesta

	/** Un ingrediente: qué y cuántos (y qué dibujar). */
	public record Cost(Ingredient ingredient, int count, Item icon) {
	}

	public static List<Cost> cost(Kind kind, Material material) {
		List<Cost> out = new ArrayList<>();
		double f = kind == Kind.RAILING ? 0.5 : 1.0;
		Ingredient planks = Ingredient.fromTag(ItemTags.PLANKS), stone = Ingredient.fromTag(ItemTags.STONE_CRAFTING_MATERIALS);
		switch (material) {
			case STRAW -> {
				out.add(new Cost(Ingredient.ofItems(Items.WHEAT), (int) Math.ceil(6 * f), Items.WHEAT));
				out.add(new Cost(Ingredient.ofItems(Items.STICK), (int) Math.ceil(2 * f), Items.STICK));
			}
			case WOOD -> {
				out.add(new Cost(planks, (int) Math.ceil(6 * f), Items.OAK_PLANKS));
				out.add(new Cost(Ingredient.ofItems(Items.STICK), (int) Math.ceil(2 * f), Items.STICK));
			}
			case STONE -> {
				out.add(new Cost(stone, (int) Math.ceil(6 * f), Items.COBBLESTONE));
				out.add(new Cost(planks, (int) Math.ceil(2 * f), Items.OAK_PLANKS));
			}
			case METAL -> {
				out.add(new Cost(Ingredient.ofItems(Items.IRON_INGOT), (int) Math.ceil(4 * f), Items.IRON_INGOT));
				out.add(new Cost(stone, (int) Math.ceil(2 * f), Items.COBBLESTONE));
			}
		}
		if (kind == Kind.WINDOW) {
			out.add(new Cost(Ingredient.ofItems(Items.GLASS_PANE), 2, Items.GLASS_PANE));
		}
		if (kind == Kind.DOORWAY) {
			out.add(new Cost(material == Material.METAL ? Ingredient.ofItems(Items.IRON_INGOT) : planks, 2,
					material == Material.METAL ? Items.IRON_INGOT : Items.OAK_PLANKS));
		}
		return out;
	}

	/** ¿Cuántos tiene de ese ingrediente? */
	public static int count(net.minecraft.entity.player.PlayerInventory inv, Ingredient ing) {
		int n = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty() && ing.test(s)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** ¿Cuántas piezas podría fabricar con lo que lleva? */
	public static int affordable(net.minecraft.entity.player.PlayerInventory inv, Kind kind, Material material) {
		return affordable(inv, cost(kind, material));
	}

	/** ¿Cuántas veces le llega para eso? */
	public static int affordable(net.minecraft.entity.player.PlayerInventory inv, List<Cost> costs) {
		int best = Integer.MAX_VALUE;
		for (Cost c : costs) {
			best = Math.min(best, count(inv, c.ingredient()) / c.count());
		}
		return best == Integer.MAX_VALUE ? 0 : best;
	}

	/** Gasta los ingredientes de una pieza (si los tiene todos). */
	public static boolean pay(net.minecraft.entity.player.PlayerInventory inv, Kind kind, Material material) {
		return pay(inv, cost(kind, material));
	}

	/** Gasta esos ingredientes (si los tiene todos). */
	public static boolean pay(net.minecraft.entity.player.PlayerInventory inv, List<Cost> costs) {
		if (affordable(inv, costs) < 1) {
			return false;
		}
		for (Cost c : costs) {
			int left = c.count();
			for (int i = 0; i < inv.size() && left > 0; i++) {
				ItemStack s = inv.getStack(i);
				if (!s.isEmpty() && c.ingredient().test(s)) {
					int take = Math.min(left, s.getCount());
					s.decrement(take);
					left -= take;
				}
			}
		}
		inv.markDirty();
		return true;
	}

	// ------------------------------------------------------------------ dónde se pone

	/** La base (el suelo) de una planta. */
	public static double floor(int level) {
		return DECK + level * LEVEL;
	}

	/** El giro de una pieza de casilla según hacia dónde mira el jugador (en coordenadas de la plataforma). */
	public static int rotFor(Vec3d look) {
		if (Math.abs(look.z) >= Math.abs(look.x)) {
			return look.z >= 0 ? 0 : 2;
		}
		return look.x >= 0 ? 1 : 3;
	}

	/** Dónde iría una pieza si se apunta a hit (en coordenadas de la plataforma) mirando hacia look; null si fuera. */
	@Nullable
	public static Slot slotFor(Kind kind, Vec3d hit, Vec3d look) {
		Vec3d q = hit.subtract(look.multiply(0.05));
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		if (flat.lengthSquared() > 1e-6) {
			flat = flat.normalize();
		}
		if (kind.edge) {
			int level = MathHelper.clamp(MathHelper.floor((q.y - DECK + 0.2) / LEVEL), 0, LEVELS - 1);
			double cx = (q.x - X0) / CELL, cz = (q.z - Z0) / CELL;
			if (cx < -0.4 || cx > NX + 0.4 || cz < -0.4 || cz > NZ + 0.4) {
				return null;
			}
			int col = MathHelper.clamp(MathHelper.floor(cx), 0, NX - 1), row = MathHelper.clamp(MathHelper.floor(cz), 0, NZ - 1);
			double dz = Math.abs(cz - Math.round(cz)), dx = Math.abs(cx - Math.round(cx));
			if (dz <= dx) {
				return new Slot(kind, level, col, MathHelper.clamp((int) Math.round(cz), 0, NZ), 0);
			}
			return new Slot(kind, level, MathHelper.clamp((int) Math.round(cx), 0, NX), row, 1);
		}
		int level;
		Vec3d c;
		if (kind == Kind.STAIRS) {
			level = MathHelper.floor((q.y - DECK + 0.2) / LEVEL);
			c = q;
		} else {
			level = Math.max(0, MathHelper.floor((q.y - DECK - 1.0) / LEVEL));
			c = hit.add(flat.multiply(0.4));
		}
		double cx = (c.x - X0) / CELL, cz = (c.z - Z0) / CELL;
		if (level < 0 || level >= LEVELS || cx < 0 || cx >= NX || cz < 0 || cz >= NZ) {
			return null;
		}
		return new Slot(kind, level, MathHelper.floor(cx), MathHelper.floor(cz), rotFor(flat));
	}

	/** Las casillas a los lados de un borde. */
	static List<int[]> edgeCells(int axis, int a, int b) {
		List<int[]> out = new ArrayList<>();
		if (axis == 0) {
			if (b - 1 >= 0) {
				out.add(new int[] {a, b - 1});
			}
			if (b < NZ) {
				out.add(new int[] {a, b});
			}
		} else {
			if (a - 1 >= 0) {
				out.add(new int[] {a - 1, b});
			}
			if (a < NX) {
				out.add(new int[] {a, b});
			}
		}
		return out;
	}

	/** Los cuatro bordes de una casilla: {eje, a, b}. */
	static int[][] cellEdges(int a, int b) {
		return new int[][] {{0, a, b}, {0, a, b + 1}, {1, a, b}, {1, a + 1, b}};
	}

	static boolean inBounds(Slot s) {
		if (s.level() < 0 || s.level() >= LEVELS) {
			return false;
		}
		if (s.kind().edge) {
			return s.rot() == 0 ? s.a() >= 0 && s.a() < NX && s.b() >= 0 && s.b() <= NZ : s.a() >= 0 && s.a() <= NX && s.b() >= 0 && s.b() < NZ;
		}
		return s.a() >= 0 && s.a() < NX && s.b() >= 0 && s.b() < NZ && s.rot() >= 0 && s.rot() < 4;
	}

	@Nullable
	static Placed at(List<Placed> pieces, String key) {
		for (Placed p : pieces) {
			if (p.slot().key().equals(key)) {
				return p;
			}
		}
		return null;
	}

	private static boolean ceilingAt(List<Placed> pieces, int level, int a, int b) {
		Placed p = at(pieces, "h" + level + a + "_" + b);
		return p != null && p.kind == Kind.CEILING;
	}

	private static boolean wallAt(List<Placed> pieces, int level, int axis, int a, int b) {
		Placed p = at(pieces, "e" + level + axis + a + "_" + b);
		return p != null && p.kind.wall();
	}

	/** ¿Tiene apoyo una pieza en ese sitio (con lo que hay)? */
	static boolean supported(List<Placed> pieces, Slot s) {
		int l = s.level();
		if (s.kind().edge) {
			if (l == 0) {
				return true;
			}
			for (int[] c : edgeCells(s.rot(), s.a(), s.b())) {
				if (ceilingAt(pieces, l - 1, c[0], c[1])) {
					return true;
				}
			}
			return false;
		}
		if (s.kind() == Kind.STAIRS) {
			return l == 0 || ceilingAt(pieces, l - 1, s.a(), s.b());
		}
		// techos y tejados: una pared debajo (en uno de sus bordes) o un techo al lado
		for (int[] e : cellEdges(s.a(), s.b())) {
			if (wallAt(pieces, l, e[0], e[1], e[2])) {
				return true;
			}
		}
		int[][] near = {{s.a() - 1, s.b()}, {s.a() + 1, s.b()}, {s.a(), s.b() - 1}, {s.a(), s.b() + 1}};
		for (int[] n : near) {
			if (ceilingAt(pieces, l, n[0], n[1])) {
				return true;
			}
		}
		return false;
	}

	/** ¿Se puede poner ahí? (dentro de la plataforma, sitio libre y con apoyo). */
	public static boolean canPlace(List<Placed> pieces, Slot s) {
		if (!inBounds(s) || at(pieces, s.key()) != null) {
			return false;
		}
		if (s.kind() == Kind.CEILING || s.kind() == Kind.ROOF) {
			if (at(pieces, "s" + s.level() + s.a() + "_" + s.b()) != null) {
				return false;
			}
		} else if (s.kind() == Kind.STAIRS) {
			if (at(pieces, "h" + s.level() + s.a() + "_" + s.b()) != null) {
				return false;
			}
		}
		return supported(pieces, s);
	}

	/**
	 * Lo que se queda sin apoyo (se va quitando por plantas, de abajo arriba: las paredes de abajo siempre se aguantan; los
	 * techos, si tocan una pared o, de techo en techo, uno que la toque).
	 */
	public static List<Placed> unsupported(List<Placed> pieces) {
		Set<Placed> ok = new HashSet<>();
		List<Placed> kept = new ArrayList<>();
		for (int l = 0; l < LEVELS; l++) {
			// las paredes, barandillas y escaleras de esta planta
			for (Placed p : pieces) {
				if (p.level == l && (p.kind.edge || p.kind == Kind.STAIRS) && supported(kept, p.slot())) {
					ok.add(p);
					kept.add(p);
				}
			}
			// los techos: los que tocan una pared y, de ahí, los de al lado
			boolean grew = true;
			while (grew) {
				grew = false;
				for (Placed p : pieces) {
					if (p.level == l && p.kind == Kind.CEILING && !ok.contains(p) && supported(kept, p.slot())) {
						ok.add(p);
						kept.add(p);
						grew = true;
					}
				}
			}
			for (Placed p : pieces) {
				if (p.level == l && p.kind == Kind.ROOF && supported(kept, p.slot())) {
					ok.add(p);
					kept.add(p);
				}
			}
		}
		List<Placed> out = new ArrayList<>();
		for (Placed p : pieces) {
			if (!ok.contains(p)) {
				out.add(p);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ cómo es (para dibujarla, chocar con ella y apuntarla)

	/** Las texturas de una pieza: el material, el remate, el cristal, la puerta y el tejado. */
	public enum Tex {
		MAIN, TRIM, GLASS, DOOR, ROOF, NONE
	}

	/** Un trozo de pieza: una caja con su textura (y si es macizo). */
	public record Part(Box box, Tex tex, boolean solid) {
	}

	/** Una caja de un borde: s a lo largo del borde (0..CELL), n a un lado y otro de él, y la altura. */
	private static Box edgeBox(int axis, int a, int b, double s0, double s1, double n0, double n1, double y0, double y1) {
		if (axis == 0) {
			double zl = Z0 + b * CELL, xs = X0 + a * CELL;
			return new Box(xs + s0, y0, zl + n0, xs + s1, y1, zl + n1);
		}
		double xl = X0 + a * CELL, zs = Z0 + b * CELL;
		return new Box(xl + n0, y0, zs + s0, xl + n1, y1, zs + s1);
	}

	/** Una caja de una casilla, girada: p a lo ancho, q hacia donde sube (0..CELL). */
	private static Box cellBox(int a, int b, int rot, double p0, double p1, double q0, double q1, double y0, double y1) {
		double xa = X0 + a * CELL, zb = Z0 + b * CELL;
		double[] c0 = cellPoint(rot, p0, q0), c1 = cellPoint(rot, p1, q1);
		return new Box(xa + Math.min(c0[0], c1[0]), y0, zb + Math.min(c0[1], c1[1]), xa + Math.max(c0[0], c1[0]), y1, zb + Math.max(c0[1], c1[1]));
	}

	private static double[] cellPoint(int rot, double p, double q) {
		return switch (rot & 3) {
			case 0 -> new double[] {p, q};
			case 1 -> new double[] {q, CELL - p};
			case 2 -> new double[] {CELL - p, CELL - q};
			default -> new double[] {CELL - q, p};
		};
	}

	private static final java.util.Map<Integer, List<Part>> PARTS = new java.util.concurrent.ConcurrentHashMap<>();

	/** Los trozos de una pieza en ese sitio (open: si es una puerta, abierta). Se calculan una vez y se guardan. */
	public static List<Part> parts(Kind kind, int level, int a, int b, int rot, boolean open) {
		int key = ((((kind.ordinal() * 4 + (level & 3)) * 16 + (a & 15)) * 16 + (b & 15)) * 4 + (rot & 3)) * 2 + (open ? 1 : 0);
		return PARTS.computeIfAbsent(key, k -> List.copyOf(build(kind, level, a, b, rot, open)));
	}

	private static List<Part> build(Kind kind, int level, int a, int b, int rot, boolean open) {
		List<Part> out = new ArrayList<>();
		double y = floor(level), top = y + LEVEL, c = CELL;
		switch (kind) {
			case WALL -> {
				out.add(new Part(edgeBox(rot, a, b, 0, c, -0.1, 0.1, y, top), Tex.MAIN, true));
				out.add(new Part(edgeBox(rot, a, b, 0.01, c - 0.01, -0.12, 0.12, y, y + 0.15), Tex.TRIM, false));
				out.add(new Part(edgeBox(rot, a, b, 0.01, c - 0.01, -0.12, 0.12, top - 0.15, top), Tex.TRIM, false));
			}
			case DOORWAY -> {
				out.add(new Part(edgeBox(rot, a, b, 0, 0.3, -0.1, 0.1, y, top), Tex.MAIN, true));
				out.add(new Part(edgeBox(rot, a, b, c - 0.3, c, -0.1, 0.1, y, top), Tex.MAIN, true));
				out.add(new Part(edgeBox(rot, a, b, 0.3, c - 0.3, -0.1, 0.1, y + 2.05, top), Tex.MAIN, true));
				out.add(new Part(edgeBox(rot, a, b, 0.28, c - 0.28, -0.12, 0.12, y + 2.0, y + 2.1), Tex.TRIM, false));
				if (open) {
					out.add(new Part(edgeBox(rot, a, b, 0.3, 0.38, 0.1, 0.1 + c - 0.64, y, y + 2.0), Tex.DOOR, false));
				} else {
					out.add(new Part(edgeBox(rot, a, b, 0.32, c - 0.32, -0.04, 0.04, y, y + 2.0), Tex.DOOR, true));
					out.add(new Part(edgeBox(rot, a, b, c - 0.48, c - 0.42, -0.09, 0.09, y + 0.95, y + 1.05), Tex.TRIM, false));
				}
			}
			case WINDOW -> {
				out.add(new Part(edgeBox(rot, a, b, 0, c, -0.1, 0.1, y, y + 0.9), Tex.MAIN, true));
				out.add(new Part(edgeBox(rot, a, b, 0, c, -0.1, 0.1, y + 1.8, top), Tex.MAIN, true));
				out.add(new Part(edgeBox(rot, a, b, 0, 0.3, -0.1, 0.1, y + 0.9, y + 1.8), Tex.MAIN, true));
				out.add(new Part(edgeBox(rot, a, b, c - 0.3, c, -0.1, 0.1, y + 0.9, y + 1.8), Tex.MAIN, true));
				out.add(new Part(edgeBox(rot, a, b, 0.3, c - 0.3, -0.03, 0.03, y + 0.9, y + 1.8), Tex.GLASS, true));
				out.add(new Part(edgeBox(rot, a, b, 0.25, c - 0.25, -0.16, 0.16, y + 0.84, y + 0.92), Tex.TRIM, false));
				out.add(new Part(edgeBox(rot, a, b, 0.01, c - 0.01, -0.12, 0.12, top - 0.15, top), Tex.TRIM, false));
			}
			case RAILING -> {
				out.add(new Part(edgeBox(rot, a, b, 0, 0.15, -0.075, 0.075, y, y + 1.0), Tex.TRIM, false));
				out.add(new Part(edgeBox(rot, a, b, c - 0.15, c, -0.075, 0.075, y, y + 1.0), Tex.TRIM, false));
				out.add(new Part(edgeBox(rot, a, b, 0.15, c - 0.15, -0.05, 0.05, y + 0.88, y + 1.0), Tex.MAIN, false));
				out.add(new Part(edgeBox(rot, a, b, 0.15, c - 0.15, -0.05, 0.05, y + 0.45, y + 0.55), Tex.MAIN, false));
				out.add(new Part(edgeBox(rot, a, b, 0.6, 0.7, -0.04, 0.04, y + 0.1, y + 0.88), Tex.MAIN, false));
				// para no saltarla: choca hasta 1,5 de alto
				out.add(new Part(edgeBox(rot, a, b, 0, c, -0.075, 0.075, y, y + 1.5), Tex.NONE, true));
			}
			case CEILING -> {
				out.add(new Part(cellBox(a, b, 0, 0, c, 0, c, top - 0.2, top), Tex.MAIN, true));
				out.add(new Part(cellBox(a, b, rot, 0.12, 0.27, 0.01, c - 0.01, top - 0.32, top - 0.2), Tex.TRIM, false));
				out.add(new Part(cellBox(a, b, rot, c - 0.27, c - 0.12, 0.01, c - 0.01, top - 0.32, top - 0.2), Tex.TRIM, false));
			}
			case ROOF -> {
				double base = top;
				for (int i = 0; i < 6; i++) {
					double y0 = Math.max(base, base + i / 6.0 - 0.12), y1 = base + (i + 1) / 6.0;
					out.add(new Part(cellBox(a, b, rot, 0, c, i * 0.25, (i + 1) * 0.25, y0, y1), Tex.ROOF, true));
				}
				out.add(new Part(cellBox(a, b, rot, -0.02, c + 0.02, 0.0, 0.12, base, base + 0.08), Tex.TRIM, false));
			}
			case STAIRS -> {
				for (int i = 0; i < 5; i++) {
					out.add(new Part(cellBox(a, b, rot, 0.08, c - 0.08, i * 0.3, (i + 1) * 0.3, y, y + (i + 1) * 0.5), Tex.MAIN, true));
					out.add(new Part(cellBox(a, b, rot, 0.06, c - 0.06, i * 0.3 + 0.2, (i + 1) * 0.3, y + (i + 1) * 0.5 - 0.06, y + (i + 1) * 0.5 + 0.02),
							Tex.TRIM, false));
				}
			}
		}
		return out;
	}

	public static List<Part> parts(Placed p) {
		return parts(p.kind, p.level, p.a, p.b, p.rot, p.open);
	}

	/** La caja que envuelve una pieza (para apuntarla). */
	public static Box bounds(Placed p) {
		Box out = null;
		for (Part part : parts(p)) {
			if (part.tex() != Tex.NONE) {
				out = out == null ? part.box() : out.union(part.box());
			}
		}
		return out;
	}
}

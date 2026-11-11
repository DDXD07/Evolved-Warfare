package com.evolvedwarfare.train;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Las vías grandes del tren (fase 52): de dos bloques de ancho, en trozos que ocupan varios bloques. Cada trozo tiene su
 * esquina de origen (en el borde de un bloque), hacia dónde mira y, en curvas y desvíos, si gira a la izquierda. En sus
 * coordenadas (u a la derecha, v hacia delante, h hacia arriba) el centro de la vía empieza en (0, 0, 0):
 * <ul>
 * <li>recta: 2 de largo;</li>
 * <li>cuesta: 4 de largo, sube 1;</li>
 * <li>curva: un cuarto de círculo de radio 4, de (0, 0) a (4, 4), acabando a la derecha (o a la izquierda);</li>
 * <li>desvío: una recta de 4 y la curva, que salen del mismo sitio; se cambia con clic derecho.</li>
 * </ul>
 * Una posición en la vía es un trozo (su bloque principal), cuál de sus tramos, cuánto se lleva andado en él (t) y hacia
 * qué lado del tramo es "adelante" para el tren (dir).
 */
public final class Track {
	/** La altura de lo alto de los raíles sobre el suelo del bloque. */
	public static final double RAIL_TOP = 0.1875;
	/** La distancia de cada raíl al centro de la vía. */
	public static final double HALF_GAUGE = 0.84;
	public static final double RADIUS = 4.0;
	public static final double ARC = RADIUS * Math.PI / 2.0;
	public static final double SLOPE_LENGTH = Math.sqrt(17.0);
	/** La parte del bloque principal (el que está en u 0..1, v 0..1). */
	public static final int MAIN = 1;

	public enum Shape implements StringIdentifiable {
		STRAIGHT("straight"), SLOPE("slope"), CURVE("curve"), SWITCH("switch");

		private final String name;

		Shape(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return name;
		}
	}

	/** Una posición sobre la vía. */
	public record Pos(BlockPos piece, int route, double t, int dir) {
	}

	/** El resultado de andar: dónde se ha llegado y si se ha tenido que parar (fin de la vía). */
	public record Walk(Pos pos, boolean blocked) {
	}

	/** Al salir de un trozo: el trozo de al lado, su tramo, por qué punta se entra (t) y si se entra por el principio. */
	record Link(Piece piece, int route, double t, boolean atStart) {
	}

	private Track() {
	}

	// ------------------------------------------------------------------ los tramos de cada forma

	public static int routes(Shape s) {
		return s == Shape.SWITCH ? 2 : 1;
	}

	public static double length(Shape s, int route) {
		return switch (s) {
			case STRAIGHT -> 2.0;
			case SLOPE -> SLOPE_LENGTH;
			case CURVE -> ARC;
			case SWITCH -> route == 0 ? 4.0 : ARC;
		};
	}

	static boolean arc(Shape s, int route) {
		return s == Shape.CURVE || s == Shape.SWITCH && route == 1;
	}

	/** El punto del tramo en coordenadas del trozo: {u, v, h}. */
	public static double[] local(Shape s, int route, double t) {
		t = MathHelper.clamp(t, 0.0, length(s, route));
		if (arc(s, route)) {
			double a = t / RADIUS;
			return new double[] {RADIUS - RADIUS * Math.cos(a), RADIUS * Math.sin(a), 0.0};
		}
		if (s == Shape.SLOPE) {
			double f = t / SLOPE_LENGTH;
			return new double[] {0.0, 4.0 * f, f};
		}
		return new double[] {0.0, t, 0.0};
	}

	/** La dirección del tramo (hacia donde crece t) en coordenadas del trozo. */
	public static double[] localDir(Shape s, int route, double t) {
		t = MathHelper.clamp(t, 0.0, length(s, route));
		if (arc(s, route)) {
			double a = t / RADIUS;
			return new double[] {Math.sin(a), Math.cos(a), 0.0};
		}
		if (s == Shape.SLOPE) {
			return new double[] {0.0, 4.0 / SLOPE_LENGTH, 1.0 / SLOPE_LENGTH};
		}
		return new double[] {0.0, 1.0, 0.0};
	}

	/** Los bloques que ocupa una forma (sin reflejar): {bu, bv}, el bloque de u bu..bu+1 y v bv..bv+1. */
	public static List<int[]> footprint(Shape s) {
		List<int[]> out = new ArrayList<>();
		switch (s) {
			case STRAIGHT -> rect(out, 2);
			case SLOPE -> rect(out, 4);
			case CURVE -> band(out);
			case SWITCH -> {
				band(out);
				for (int bv = 0; bv < 4; bv++) {
					for (int bu = -1; bu <= 0; bu++) {
						add(out, bu, bv);
					}
				}
			}
		}
		return out;
	}

	private static void rect(List<int[]> out, int len) {
		for (int bv = 0; bv < len; bv++) {
			add(out, -1, bv);
			add(out, 0, bv);
		}
	}

	/** Los bloques que toca la curva (de radio 3 a 5 alrededor de (4, 0)). */
	private static void band(List<int[]> out) {
		for (int bv = 0; bv <= 4; bv++) {
			for (int bu = -1; bu <= 3; bu++) {
				double nx = MathHelper.clamp(RADIUS, bu, bu + 1), nz = MathHelper.clamp(0.0, bv, bv + 1);
				double min = Math.hypot(nx - RADIUS, nz);
				double max = 0;
				for (int cu = bu; cu <= bu + 1; cu++) {
					for (int cv = bv; cv <= bv + 1; cv++) {
						max = Math.max(max, Math.hypot(cu - RADIUS, cv));
					}
				}
				if (min < RADIUS + 1.0 - 1e-6 && max > RADIUS - 1.0 + 1e-6) {
					add(out, bu, bv);
				}
			}
		}
	}

	private static void add(List<int[]> out, int bu, int bv) {
		for (int[] b : out) {
			if (b[0] == bu && b[1] == bv) {
				return;
			}
		}
		out.add(new int[] {bu, bv});
	}

	public static int part(int bu, int bv) {
		return bu + 1 + bv * 6;
	}

	// ------------------------------------------------------------------ un trozo puesto en el mundo

	/**
	 * Un trozo de vía en el mundo: su bloque principal, la forma, hacia dónde mira, si gira a la izquierda, si el desvío
	 * está echado (hacia la curva), su esquina de origen y la altura del suelo de sus bloques.
	 */
	public record Piece(BlockPos main, Shape shape, Direction facing, boolean left, boolean thrown, double ox, double oz, int y) {
		public int mirror() {
			return left ? -1 : 1;
		}

		public int activeRoute() {
			return shape == Shape.SWITCH && thrown ? 1 : 0;
		}

		/** De coordenadas del trozo al mundo (h = 0 es lo alto de los raíles). */
		public Vec3d world(double u, double v, double h) {
			Direction r = facing.rotateYClockwise();
			double uu = u * mirror();
			return new Vec3d(ox + uu * r.getOffsetX() + v * facing.getOffsetX(), y + RAIL_TOP + h, oz + uu * r.getOffsetZ() + v * facing.getOffsetZ());
		}

		public Vec3d point(int route, double t) {
			double[] l = local(shape, route, t);
			return world(l[0], l[1], l[2]);
		}

		public Vec3d dir(int route, double t) {
			double[] d = localDir(shape, route, t);
			Direction r = facing.rotateYClockwise();
			double uu = d[0] * mirror();
			return new Vec3d(uu * r.getOffsetX() + d[1] * facing.getOffsetX(), d[2], uu * r.getOffsetZ() + d[1] * facing.getOffsetZ()).normalize();
		}

		/** Los bloques que ocupa. */
		public List<BlockPos> blocks() {
			List<BlockPos> out = new ArrayList<>();
			for (int[] b : footprint(shape)) {
				out.add(blockOf(b[0], b[1]));
			}
			return out;
		}

		public BlockPos blockOf(int bu, int bv) {
			Direction r = facing.rotateYClockwise();
			double du = mirror() * (bu + 0.5), dv = bv + 0.5;
			return BlockPos.ofFloored(ox + du * r.getOffsetX() + dv * facing.getOffsetX(), y, oz + du * r.getOffsetZ() + dv * facing.getOffsetZ());
		}

		public double length(int route) {
			return Track.length(shape, route);
		}
	}

	@Nullable
	public static Piece piece(BlockView world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		return s.getBlock() instanceof TrackBlock ? piece(pos, s) : null;
	}

	public static Piece piece(BlockPos pos, BlockState s) {
		Shape shape = s.get(TrackBlock.SHAPE);
		Direction f = s.get(TrackBlock.FACING);
		boolean left = s.get(TrackBlock.LEFT);
		int part = s.get(TrackBlock.PART);
		int bu = part % 6 - 1, bv = part / 6;
		int m = left ? -1 : 1;
		Direction r = f.rotateYClockwise();
		double du = m * (bu + 0.5), dv = bv + 0.5;
		double ox = Math.round(pos.getX() + 0.5 - du * r.getOffsetX() - dv * f.getOffsetX());
		double oz = Math.round(pos.getZ() + 0.5 - du * r.getOffsetZ() - dv * f.getOffsetZ());
		Piece tmp = new Piece(pos, shape, f, left, s.get(TrackBlock.THROWN), ox, oz, pos.getY());
		return new Piece(tmp.blockOf(0, 0), shape, f, left, s.get(TrackBlock.THROWN), ox, oz, pos.getY());
	}

	// ------------------------------------------------------------------ andar

	/** Lo horizontal de una dirección, de largo 1. */
	static Vec3d flat(Vec3d d) {
		double l = Math.sqrt(d.x * d.x + d.z * d.z);
		return l < 1e-9 ? Vec3d.ZERO : new Vec3d(d.x / l, 0, d.z / l);
	}

	/** El trozo que sigue al salir de self por el punto end (hacia out); null si no hay (o no está cargado). */
	@Nullable
	static Link next(World world, Piece self, Vec3d end, Vec3d out) {
		BlockPos probe = BlockPos.ofFloored(end.x + out.x * 0.5, end.y - RAIL_TOP + 0.01, end.z + out.z * 0.5);
		for (BlockPos c : new BlockPos[] {probe, probe.down()}) {
			if (!world.isChunkLoaded(c)) {
				return null;
			}
			Piece p = piece(world, c);
			if (p == null || p.main().equals(self.main())) {
				continue;
			}
			for (int r = 0; r < routes(p.shape()); r++) {
				double len = p.length(r);
				if (p.point(r, 0).squaredDistanceTo(end) < 0.01) {
					// por la punta común de un desvío se va por donde esté echado
					if (p.shape() == Shape.SWITCH && r != p.activeRoute()) {
						continue;
					}
					return new Link(p, r, 0.0, true);
				}
				if (p.point(r, len).squaredDistanceTo(end) < 0.01) {
					return new Link(p, r, len, false);
				}
			}
		}
		return null;
	}

	/** ¿Esa punta de ese tramo sigue en otro trozo? */
	public static boolean connected(World world, Piece p, int route, boolean atEnd) {
		double len = p.length(route);
		Vec3d e = p.point(route, atEnd ? len : 0);
		Vec3d out = flat(p.dir(route, atEnd ? len : 0)).multiply(atEnd ? 1 : -1);
		return next(world, p, e, out) != null;
	}

	/** Anda d bloques por la vía (hacia atrás del tren si d es negativo). */
	public static Walk walk(World world, Pos pos, double d) {
		Piece p = piece(world, pos.piece());
		if (p == null) {
			return new Walk(pos, true);
		}
		int route = Math.min(pos.route(), routes(p.shape()) - 1);
		int dir = pos.dir();
		double t = pos.t() + d * dir;
		for (int guard = 0; guard < 256; guard++) {
			double len = p.length(route);
			boolean over = t > len + 1e-9, under = t < -1e-9;
			if (!over && !under) {
				return new Walk(new Pos(p.main(), route, MathHelper.clamp(t, 0, len), dir), false);
			}
			double rest = over ? t - len : -t;
			Vec3d e = p.point(route, over ? len : 0);
			Vec3d out = flat(p.dir(route, over ? len : 0)).multiply(over ? 1 : -1);
			Link n = next(world, p, e, out);
			if (n == null) {
				return new Walk(new Pos(p.main(), route, over ? len : 0, dir), true);
			}
			int moving = over ? 1 : -1;
			dir = dir * moving * (n.atStart() ? 1 : -1);
			p = n.piece();
			route = n.route();
			t = n.atStart() ? rest : p.length(route) - rest;
		}
		return new Walk(new Pos(p.main(), route, 0, dir), true);
	}

	/** El punto de la vía en esa posición. */
	public static Vec3d point(World world, Pos pos) {
		Piece p = piece(world, pos.piece());
		return p == null ? Vec3d.ofBottomCenter(pos.piece()).add(0, RAIL_TOP, 0) : p.point(pos.route(), pos.t());
	}

	/** La dirección del tren (hacia delante) en esa posición. */
	public static Vec3d forward(World world, Pos pos) {
		Piece p = piece(world, pos.piece());
		return p == null ? new Vec3d(0, 0, 1) : p.dir(pos.route(), pos.t()).multiply(pos.dir());
	}

	/** Empieza en el trozo de ese bloque, a la mitad de su tramo activo, mirando lo más parecido a facing. */
	@Nullable
	public static Pos start(World world, BlockPos anyPart, Direction facing) {
		Piece p = piece(world, anyPart);
		if (p == null) {
			return null;
		}
		int route = p.activeRoute();
		double t = p.length(route) / 2.0;
		Vec3d d = p.dir(route, t);
		int dir = d.x * facing.getOffsetX() + d.z * facing.getOffsetZ() >= 0 ? 1 : -1;
		return new Pos(p.main(), route, t, dir);
	}

	/** El ángulo (yaw de Minecraft) de una dirección. */
	public static float yaw(Vec3d dir) {
		return (float) (MathHelper.atan2(-dir.x, dir.z) * MathHelper.DEGREES_PER_RADIAN);
	}
}

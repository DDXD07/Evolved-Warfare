package com.evolvedwarfare.train;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Las vías (las de siempre: normales, propulsoras, detectoras, activadoras): por dónde va el tren. Una posición en la vía
 * es un bloque de vía, el lado por el que se entra (from) y cuánto se lleva recorrido dentro (t, de 0 a lo que mida el
 * tramo: 1 en recta o cuesta, π/4 en curva). Se puede andar hacia delante o hacia atrás tanto como se quiera.
 */
public final class Track {
	public static final double RAIL_Y = 0.0625;
	public static final double CURVE = Math.PI / 4.0;

	/** Una posición sobre la vía. */
	public record Pos(BlockPos rail, Direction from, double t) {
	}

	/** El resultado de andar: dónde se ha llegado y si se ha tenido que parar (fin de la vía). */
	public record Walk(Pos pos, boolean blocked) {
	}

	private Track() {
	}

	@Nullable
	public static RailShape shape(World world, BlockPos pos) {
		BlockState s = world.getBlockState(pos);
		if (s.getBlock() instanceof AbstractRailBlock rail) {
			return s.get(rail.getShapeProperty());
		}
		return null;
	}

	/** Las dos salidas de un tramo (horizontales). */
	public static Direction[] ends(RailShape shape) {
		return switch (shape) {
			case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new Direction[] {Direction.NORTH, Direction.SOUTH};
			case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new Direction[] {Direction.EAST, Direction.WEST};
			case SOUTH_EAST -> new Direction[] {Direction.SOUTH, Direction.EAST};
			case SOUTH_WEST -> new Direction[] {Direction.SOUTH, Direction.WEST};
			case NORTH_WEST -> new Direction[] {Direction.NORTH, Direction.WEST};
			case NORTH_EAST -> new Direction[] {Direction.NORTH, Direction.EAST};
		};
	}

	/** ¿Esa salida está arriba (en una cuesta)? */
	static boolean high(RailShape shape, Direction d) {
		return switch (shape) {
			case ASCENDING_NORTH -> d == Direction.NORTH;
			case ASCENDING_SOUTH -> d == Direction.SOUTH;
			case ASCENDING_EAST -> d == Direction.EAST;
			case ASCENDING_WEST -> d == Direction.WEST;
			default -> false;
		};
	}

	static boolean connects(RailShape shape, Direction d) {
		Direction[] e = ends(shape);
		return e[0] == d || e[1] == d;
	}

	static Direction other(RailShape shape, Direction d) {
		Direction[] e = ends(shape);
		return e[0] == d ? e[1] : e[0];
	}

	public static double length(RailShape shape) {
		Direction[] e = ends(shape);
		return e[0] == e[1].getOpposite() ? 1.0 : CURVE;
	}

	/** El punto del borde del bloque por esa salida. */
	private static Vec3d edge(BlockPos p, RailShape shape, Direction d) {
		return new Vec3d(p.getX() + 0.5 + d.getOffsetX() * 0.5, p.getY() + RAIL_Y + (high(shape, d) ? 1.0 : 0.0), p.getZ() + 0.5 + d.getOffsetZ() * 0.5);
	}

	/** El punto de la vía (y su dirección de avance, hacia la salida que no es from). */
	public static Vec3d[] point(World world, Pos pos) {
		RailShape shape = shape(world, pos.rail());
		if (shape == null) {
			Vec3d c = Vec3d.ofBottomCenter(pos.rail()).add(0, RAIL_Y, 0);
			return new Vec3d[] {c, new Vec3d(-pos.from().getOffsetX(), 0, -pos.from().getOffsetZ())};
		}
		Direction a = pos.from(), b = other(shape, a);
		Vec3d ea = edge(pos.rail(), shape, a), eb = edge(pos.rail(), shape, b);
		double len = length(shape);
		double f = MathHelper.clamp(pos.t() / len, 0.0, 1.0);
		if (len == 1.0) {
			Vec3d dir = eb.subtract(ea).normalize();
			return new Vec3d[] {ea.lerp(eb, f), dir};
		}
		// curva: un cuarto de círculo de radio 1/2 alrededor de la esquina entre las dos salidas
		Vec3d corner = new Vec3d(pos.rail().getX() + 0.5 + (a.getOffsetX() + b.getOffsetX()) * 0.5, ea.y,
				pos.rail().getZ() + 0.5 + (a.getOffsetZ() + b.getOffsetZ()) * 0.5);
		Vec3d ra = ea.subtract(corner), rb = eb.subtract(corner);
		double ang = f * Math.PI / 2.0;
		Vec3d p = corner.add(ra.multiply(Math.cos(ang)).add(rb.multiply(Math.sin(ang))));
		Vec3d dir = ra.multiply(-Math.sin(ang)).add(rb.multiply(Math.cos(ang))).normalize();
		return new Vec3d[] {p, dir};
	}

	/** El tramo que sigue al salir de p por d (puede estar un bloque más arriba o más abajo). */
	@Nullable
	private static BlockPos next(World world, BlockPos p, RailShape shape, Direction d) {
		BlockPos n = p.offset(d);
		if (high(shape, d)) {
			n = n.up();
		}
		for (BlockPos c : new BlockPos[] {n, n.down(), n.up()}) {
			if (!world.isChunkLoaded(c)) {
				return null;
			}
			RailShape s = shape(world, c);
			if (s != null && connects(s, d.getOpposite())) {
				return c;
			}
		}
		return null;
	}

	/** Anda s bloques por la vía (hacia atrás si s es negativo). */
	public static Walk walk(World world, Pos pos, double s) {
		BlockPos rail = pos.rail();
		Direction from = pos.from();
		double t = pos.t() + s;
		for (int guard = 0; guard < 512; guard++) {
			RailShape shape = shape(world, rail);
			if (shape == null) {
				return new Walk(new Pos(rail, from, MathHelper.clamp(t, 0, 1)), true);
			}
			double len = length(shape);
			if (t > len) {
				Direction out = other(shape, from);
				BlockPos n = next(world, rail, shape, out);
				if (n == null) {
					return new Walk(new Pos(rail, from, len), true);
				}
				t -= len;
				rail = n;
				from = out.getOpposite();
			} else if (t < 0) {
				BlockPos n = next(world, rail, shape, from);
				if (n == null) {
					return new Walk(new Pos(rail, from, 0), true);
				}
				RailShape ns = shape(world, n);
				Direction entry = from.getOpposite();
				rail = n;
				from = other(ns, entry);
				t += length(ns);
			} else {
				return new Walk(new Pos(rail, from, t), false);
			}
		}
		return new Walk(new Pos(rail, from, MathHelper.clamp(t, 0, 1)), true);
	}

	/** Empieza en el bloque de vía p, a mitad del tramo, mirando lo más parecido a facing. */
	@Nullable
	public static Pos start(World world, BlockPos p, Direction facing) {
		RailShape shape = shape(world, p);
		if (shape == null) {
			return null;
		}
		Direction[] e = ends(shape);
		// se entra por la salida contraria a donde se mira
		Direction from = e[0] == facing ? e[1] : e[1] == facing ? e[0] : e[0].getOpposite() == facing ? e[0] : e[1];
		return new Pos(p.toImmutable(), from, length(shape) / 2.0);
	}

	/** El ángulo (yaw de Minecraft) de una dirección. */
	public static float yaw(Vec3d dir) {
		return (float) (MathHelper.atan2(-dir.x, dir.z) * MathHelper.DEGREES_PER_RADIAN);
	}
}

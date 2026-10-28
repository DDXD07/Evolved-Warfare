package com.evolvedwarfare.siege;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Tiro de las máquinas, igual en el servidor y en el cliente (para la vista previa): cada tic el proyectil avanza su
 * velocidad, la velocidad se multiplica por {@value #DRAG} y baja la gravedad (como cualquier proyectil lanzado).
 */
public final class SiegeBallistics {
	public static final double DRAG = 0.99;
	private static final int MAX_TICKS = 400;
	/** Hasta dónde mira el que apunta buscando el blanco. */
	public static final double LOOK_RANGE = 180.0;

	private SiegeBallistics() {
	}

	/** Un disparo calculado: hacia dónde, con qué elevación, y si llega al blanco o se queda corto. */
	public record Aim(Vec3d muzzle, float yaw, float elevation, Vec3d velocity, Vec3d target, boolean reachable) {
	}

	public static Vec3d velocity(float yaw, float elevation, double speed) {
		float y = yaw * MathHelper.RADIANS_PER_DEGREE;
		float e = elevation * MathHelper.RADIANS_PER_DEGREE;
		double horizontal = Math.cos(e) * speed;
		return new Vec3d(-Math.sin(y) * horizontal, Math.sin(e) * speed, Math.cos(y) * horizontal);
	}

	/** Altura (respecto a la salida) a la que pasa el proyectil por esa distancia horizontal; NaN si no llega. */
	public static double heightAt(double speed, double gravity, float elevation, double distance) {
		double e = elevation * MathHelper.RADIANS_PER_DEGREE;
		double vx = Math.cos(e) * speed;
		double vy = Math.sin(e) * speed;
		double x = 0.0;
		double y = 0.0;
		for (int t = 0; t < MAX_TICKS; t++) {
			double nx = x + vx;
			double ny = y + vy;
			if (nx >= distance) {
				return nx == x ? y : y + (ny - y) * (distance - x) / (nx - x);
			}
			if (ny < -256.0 || vx < 1.0E-3) {
				return Double.NaN;
			}
			x = nx;
			y = ny;
			vx *= DRAG;
			vy = vy * DRAG - gravity;
		}
		return Double.NaN;
	}

	/**
	 * La elevación que hace caer el tiro en ese punto (distancia horizontal y diferencia de altura), o null si no
	 * llega. Con 'lob', la parábola alta (pasa por encima de los muros) si existe.
	 */
	@Nullable
	public static Float solve(SiegeKind kind, double distance, double dy, boolean lob) {
		float step = 1.0F;
		List<Float> roots = new ArrayList<>();
		float prev = kind.minElevation;
		double fPrev = heightAt(kind.shotSpeed, kind.shotGravity, prev, distance) - dy;
		for (float e = kind.minElevation + step; e <= kind.maxElevation + 1.0E-3F; e += step) {
			double f = heightAt(kind.shotSpeed, kind.shotGravity, e, distance) - dy;
			if (!Double.isNaN(f) && !Double.isNaN(fPrev) && (f == 0.0 || Math.signum(f) != Math.signum(fPrev))) {
				roots.add(refine(kind, distance, dy, prev, e));
			}
			prev = e;
			fPrev = f;
		}
		if (roots.isEmpty()) {
			return null;
		}
		return lob ? roots.get(roots.size() - 1) : roots.get(0);
	}

	private static float refine(SiegeKind kind, double distance, double dy, float lo, float hi) {
		double fLo = heightAt(kind.shotSpeed, kind.shotGravity, lo, distance) - dy;
		for (int i = 0; i < 14; i++) {
			float mid = (lo + hi) * 0.5F;
			double fMid = heightAt(kind.shotSpeed, kind.shotGravity, mid, distance) - dy;
			if (Double.isNaN(fMid) || Math.signum(fMid) == Math.signum(fLo)) {
				lo = mid;
				fLo = fMid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) * 0.5F;
	}

	/** La elevación con la que llega más lejos (para cuando el blanco está fuera de alcance). */
	public static float farthestElevation(SiegeKind kind) {
		float best = kind.minElevation;
		double bestRange = -1.0;
		for (float e = Math.max(kind.minElevation, 0.0F); e <= kind.maxElevation; e += 1.0F) {
			double range = range(kind, e);
			if (range > bestRange) {
				bestRange = range;
				best = e;
			}
		}
		return best;
	}

	/** Alcance en llano (hasta volver a la altura de salida). */
	public static double range(SiegeKind kind, float elevation) {
		double e = elevation * MathHelper.RADIANS_PER_DEGREE;
		double vx = Math.cos(e) * kind.shotSpeed;
		double vy = Math.sin(e) * kind.shotSpeed;
		double x = 0.0;
		double y = 0.0;
		for (int t = 0; t < MAX_TICKS; t++) {
			x += vx;
			y += vy;
			vx *= DRAG;
			vy = vy * DRAG - kind.shotGravity;
			if (y < 0.0 && t > 0) {
				return x;
			}
		}
		return x;
	}

	/** Yaw (grados de Minecraft) desde 'from' hacia 'to'. */
	public static float yawTo(Vec3d from, Vec3d to) {
		return (float) (MathHelper.atan2(to.z - from.z, to.x - from.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
	}

	/** Recorta el yaw al arco que puede girar la máquina. */
	public static float clampYaw(SiegeKind kind, float engineYaw, float yaw) {
		float diff = MathHelper.wrapDegrees(yaw - engineYaw);
		return engineYaw + MathHelper.clamp(diff, -kind.yawArc, kind.yawArc);
	}

	/** Disparo hacia un punto concreto (la dotación). */
	public static Aim aimAt(SiegeKind kind, Vec3d muzzle, float engineYaw, Vec3d target) {
		float yaw = clampYaw(kind, engineYaw, yawTo(muzzle, target));
		double distance = Math.sqrt(MathHelper.squaredHypot(target.x - muzzle.x, target.z - muzzle.z));
		Float elevation = solve(kind, distance, target.y - muzzle.y, kind.lob);
		boolean reachable = elevation != null;
		float e = reachable ? elevation : farthestElevation(kind);
		return new Aim(muzzle, yaw, e, velocity(yaw, e, kind.shotSpeed), target, reachable);
	}

	/**
	 * Disparo del jugador que la maneja: apunta a lo que está mirando (el primer bloque en esa dirección). Si mira al
	 * cielo, la balista tira recto y las demás lo más lejos que pueden.
	 */
	public static Aim aimFor(SiegeKind kind, World world, Vec3d muzzle, float engineYaw, Entity shooter) {
		Vec3d eye = shooter.getEyePos();
		Vec3d look = shooter.getRotationVec(1.0F);
		Vec3d end = eye.add(look.multiply(LOOK_RANGE));
		BlockHitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.ANY, shooter));
		if (hit.getType() != HitResult.Type.MISS) {
			return aimAt(kind, muzzle, engineYaw, hit.getPos());
		}
		float yaw = clampYaw(kind, engineYaw, shooter.getYaw());
		float e = kind == SiegeKind.BALLISTA
				? MathHelper.clamp(-shooter.getPitch(), kind.minElevation, kind.maxElevation)
				: farthestElevation(kind);
		return new Aim(muzzle, yaw, e, velocity(yaw, e, kind.shotSpeed), end, false);
	}

	/** Puntos del recorrido (para la vista previa); se para al chocar con un bloque. */
	public static List<Vec3d> path(World world, Vec3d start, Vec3d velocity, double gravity, @Nullable Entity except) {
		List<Vec3d> points = new ArrayList<>();
		Vec3d pos = start;
		Vec3d v = velocity;
		points.add(pos);
		for (int t = 0; t < MAX_TICKS; t++) {
			Vec3d next = pos.add(v);
			BlockHitResult hit = world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE, except != null ? ShapeContext.of(except) : ShapeContext.absent()));
			if (hit.getType() != HitResult.Type.MISS) {
				points.add(hit.getPos());
				return points;
			}
			pos = next;
			points.add(pos);
			v = new Vec3d(v.x * DRAG, v.y * DRAG - gravity, v.z * DRAG);
			if (pos.y < world.getBottomY()) {
				break;
			}
		}
		return points;
	}
}

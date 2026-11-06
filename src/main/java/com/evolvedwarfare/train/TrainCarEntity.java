package com.evolvedwarfare.train;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

/**
 * Un coche del tren (la locomotora o una plataforma): lo que tiene de macizo (su plataforma y lo construido encima) en sus
 * coordenadas (x a lo ancho, y hacia arriba desde su base, z hacia delante), con lo que chocan y sobre lo que andan
 * jugadores y criaturas, y lo que lleva encima, que se mueve (y gira) con él para que nada se caiga.
 *
 * Para chocar se usa el giro de verdad (también en las curvas): cada pieza (de un bloque como mucho) se convierte en la
 * caja del mundo que la envuelve.
 */
public abstract class TrainCarEntity extends Entity {
	/** Lo que han girado las ruedas (para dibujarlas, en el cliente). */
	public float wheelAngle, lastWheelAngle;

	/** El radio de las ruedas (bloques). */
	protected double wheelRadius() {
		return 0.5;
	}

	/** Dónde estaba en el tic anterior (para llevar lo de encima). */
	protected Vec3d carryPos;
	protected float carryYaw;
	private List<Box> worldBoxes = List.of();
	private Vec3d boxesPos;
	private float boxesYaw = Float.NaN;
	private int boxesVersion = -1;

	protected TrainCarEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	/** Las cajas macizas en coordenadas del coche. */
	protected abstract List<Box> localBoxes();

	/** Cambia cuando cambian las cajas (para no recalcularlas cada vez). */
	protected abstract int boxesVersion();

	/** Medio ancho, medio largo y alto (para su caja de colisión y de apuntar). */
	protected abstract double halfWidth();

	protected abstract double halfLength();

	protected abstract double height();

	public static float snap(float yaw) {
		return Math.round(yaw / 90.0F) * 90.0F;
	}

	/** De coordenadas del coche a coordenadas del mundo (con el giro que se diga). */
	public static Vec3d toWorld(Vec3d origin, float yaw, double x, double y, double z) {
		double r = yaw * MathHelper.RADIANS_PER_DEGREE;
		double c = Math.cos(r), s = Math.sin(r);
		return new Vec3d(origin.x + x * c - z * s, origin.y + y, origin.z + x * s + z * c);
	}

	/** Del mundo a coordenadas del coche. */
	public static Vec3d toLocal(Vec3d origin, float yaw, Vec3d world) {
		double r = yaw * MathHelper.RADIANS_PER_DEGREE;
		double c = Math.cos(r), s = Math.sin(r);
		double dx = world.x - origin.x, dz = world.z - origin.z;
		return new Vec3d(dx * c + dz * s, world.y - origin.y, -dx * s + dz * c);
	}

	public Vec3d toWorld(double x, double y, double z) {
		return toWorld(getPos(), getYaw(), x, y, z);
	}

	public Vec3d toLocal(Vec3d world) {
		return toLocal(getPos(), getYaw(), world);
	}

	/** Parte una caja grande en piezas de un bloque como mucho (para que al girarla no crezca). */
	public static List<Box> split(Box b) {
		List<Box> out = new ArrayList<>();
		int nx = Math.max(1, (int) Math.ceil(b.getLengthX() - 1e-6)), nz = Math.max(1, (int) Math.ceil(b.getLengthZ() - 1e-6));
		double sx = b.getLengthX() / nx, sz = b.getLengthZ() / nz;
		for (int i = 0; i < nx; i++) {
			for (int k = 0; k < nz; k++) {
				out.add(new Box(b.minX + i * sx, b.minY, b.minZ + k * sz, b.minX + (i + 1) * sx, b.maxY, b.minZ + (k + 1) * sz));
			}
		}
		return out;
	}

	/** La caja del mundo que envuelve una caja del coche girada. */
	public static Box rotated(Vec3d origin, float yaw, Box b) {
		double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (double x : new double[] {b.minX, b.maxX}) {
			for (double z : new double[] {b.minZ, b.maxZ}) {
				Vec3d w = toWorld(origin, yaw, x, 0, z);
				minX = Math.min(minX, w.x);
				maxX = Math.max(maxX, w.x);
				minZ = Math.min(minZ, w.z);
				maxZ = Math.max(maxZ, w.z);
			}
		}
		// en las curvas la caja que envuelve crece un poco: se encoge lo que ha crecido para que no estorbe de más
		double grow = (maxX - minX + maxZ - minZ - b.getLengthX() - b.getLengthZ()) * 0.25;
		return new Box(minX + grow * 0.5, origin.y + b.minY, minZ + grow * 0.5, maxX - grow * 0.5, origin.y + b.maxY, maxZ - grow * 0.5);
	}

	/** Las cajas macizas en el mundo (con el giro de verdad). */
	public List<Box> worldBoxes() {
		float yaw = getYaw();
		if (!getPos().equals(boxesPos) || yaw != boxesYaw || boxesVersion != boxesVersion()) {
			List<Box> out = new ArrayList<>();
			for (Box b : localBoxes()) {
				out.add(rotated(getPos(), yaw, b));
			}
			worldBoxes = out;
			boxesPos = getPos();
			boxesYaw = yaw;
			boxesVersion = boxesVersion();
		}
		return worldBoxes;
	}

	/** Lo que se puede apuntar en coordenadas del coche (por defecto, lo macizo). */
	protected List<Box> localTargets() {
		return localBoxes();
	}

	/**
	 * Dónde da un rayo (del ojo al alcance) en el coche: el punto más cercano de lo que se puede apuntar, o null. Así se
	 * apunta al tren solo donde hay tren, y lo de alrededor (y detrás) se puede picar y usar.
	 */
	@org.jetbrains.annotations.Nullable
	public Vec3d raycast(Vec3d from, Vec3d to) {
		Vec3d lf = toLocal(from), lt = toLocal(to);
		Vec3d best = null;
		double bestD = Double.MAX_VALUE;
		for (Box b : localTargets()) {
			Box e = b.expand(0.02);
			if (e.contains(lf)) {
				return from;
			}
			var hit = e.raycast(lf, lt);
			if (hit.isPresent()) {
				double d = hit.get().squaredDistanceTo(lf);
				if (d < bestD) {
					bestD = d;
					best = hit.get();
				}
			}
		}
		return best == null ? null : toWorld(best.x, best.y, best.z);
	}

	/** Lo que choca dentro de esa caja (para la física de jugadores y criaturas). */
	public void collisions(Box query, List<VoxelShape> out) {
		for (Box b : worldBoxes()) {
			if (b.intersects(query)) {
				out.add(VoxelShapes.cuboid(b));
			}
		}
	}

	/** ¿Está ese algo de pie sobre (o dentro de) este coche? */
	public boolean carries(Entity e) {
		if (e == this || e.hasVehicle() || e instanceof TrainCarEntity || e.noClip) {
			return false;
		}
		Box feet = e.getBoundingBox().offset(0, -0.12, 0);
		for (Box b : worldBoxes()) {
			if (b.intersects(feet)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected Box calculateBoundingBox() {
		Box local = new Box(-halfWidth(), 0, -halfLength(), halfWidth(), height(), halfLength());
		Vec3d p = getPos();
		double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (double x : new double[] {local.minX, local.maxX}) {
			for (double z : new double[] {local.minZ, local.maxZ}) {
				Vec3d w = toWorld(p, getYaw(), x, 0, z);
				minX = Math.min(minX, w.x);
				maxX = Math.max(maxX, w.x);
				minZ = Math.min(minZ, w.z);
				maxZ = Math.max(maxZ, w.z);
			}
		}
		return new Box(minX, p.y, minZ, maxX, p.y + height(), maxZ);
	}

	/** Lo pone en su sitio (en la vía) y se lleva lo de encima. */
	public void moveCar(Vec3d pos, float yaw, float pitch) {
		setPosition(pos);
		setYaw(yaw);
		setPitch(pitch);
		setBoundingBox(calculateBoundingBox());
	}

	@Override
	public void tick() {
		super.tick();
		World world = getWorld();
		if (carryPos == null) {
			carryPos = getPos();
			carryYaw = getYaw();
			return;
		}
		Vec3d delta = getPos().subtract(carryPos);
		float dyaw = MathHelper.wrapDegrees(getYaw() - carryYaw);
		if (world.isClient) {
			lastWheelAngle = wheelAngle;
			double r = getYaw() * MathHelper.RADIANS_PER_DEGREE;
			double along = delta.x * -Math.sin(r) + delta.z * Math.cos(r);
			wheelAngle += (float) (along / wheelRadius());
		}
		if (delta.lengthSquared() > 1e-8 || Math.abs(dyaw) > 1e-4) {
			Box area = getBoundingBox().expand(1.0, 1.0, 1.0).stretch(delta.negate());
			for (Entity e : world.getOtherEntities(this, area)) {
				boolean mine = world.isClient ? e == clientPlayer() : !(e instanceof PlayerEntity);
				if (!mine || !carriesBefore(e, delta)) {
					continue;
				}
				// se mueve con el coche: lo que se ha desplazado y lo que ha girado alrededor de su centro
				Vec3d rel = e.getPos().subtract(carryPos);
				double r = dyaw * MathHelper.RADIANS_PER_DEGREE;
				double c = Math.cos(r), s = Math.sin(r);
				Vec3d turned = new Vec3d(rel.x * c - rel.z * s, rel.y, rel.x * s + rel.z * c);
				Vec3d to = getPos().add(turned);
				e.setPosition(to.x, to.y, to.z);
				e.setYaw(e.getYaw() + dyaw);
				e.setHeadYaw(e.getHeadYaw() + dyaw);
				e.setBodyYaw(e.getBodyYaw() + dyaw);
				e.fallDistance = 0.0F;
				e.setOnGround(true);
			}
		}
		carryPos = getPos();
		carryYaw = getYaw();
	}

	/** ¿Estaba encima antes de este movimiento? (las cajas aún donde estaban). */
	private boolean carriesBefore(Entity e, Vec3d delta) {
		if (e == this || e.hasVehicle() || e instanceof TrainCarEntity || e.noClip) {
			return false;
		}
		Box feet = e.getBoundingBox().offset(0, -0.15, 0);
		for (Box b : worldBoxes()) {
			if (b.offset(delta.negate()).expand(0.05, 0.0, 0.05).intersects(feet) || b.intersects(feet)) {
				return true;
			}
		}
		return false;
	}

	private static Entity clientPlayer() {
		return ClientAccess.player();
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isImmuneToExplosion(net.minecraft.world.explosion.Explosion explosion) {
		return true;
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 256.0 * 256.0;
	}

	/** Para no tocar la clase del cliente desde el servidor. */
	public static final class ClientAccess {
		static java.util.function.Supplier<Entity> player = () -> null;

		private ClientAccess() {
		}

		public static void set(java.util.function.Supplier<Entity> supplier) {
			player = supplier;
		}

		static Entity player() {
			return player.get();
		}
	}
}

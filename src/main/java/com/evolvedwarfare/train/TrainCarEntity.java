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
 * Para chocar se usa el giro redondeado a 90° (las vías van rectas casi siempre; en las curvas se nota poco).
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
		return toWorld(getPos(), snap(getYaw()), x, y, z);
	}

	public Vec3d toLocal(Vec3d world) {
		return toLocal(getPos(), snap(getYaw()), world);
	}

	/** Las cajas macizas en el mundo (con el giro redondeado). */
	public List<Box> worldBoxes() {
		float yaw = snap(getYaw());
		if (!getPos().equals(boxesPos) || yaw != boxesYaw || boxesVersion != boxesVersion()) {
			List<Box> out = new ArrayList<>();
			for (Box b : localBoxes()) {
				Vec3d a = toWorld(getPos(), yaw, b.minX, b.minY, b.minZ);
				Vec3d c = toWorld(getPos(), yaw, b.maxX, b.maxY, b.maxZ);
				out.add(new Box(a, c));
			}
			worldBoxes = out;
			boxesPos = getPos();
			boxesYaw = yaw;
			boxesVersion = boxesVersion();
		}
		return worldBoxes;
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
		float yaw = snap(getYaw());
		boolean turned = Math.abs(MathHelper.wrapDegrees(yaw)) % 180.0F > 45.0F;
		double hx = turned ? halfLength() : halfWidth(), hz = turned ? halfWidth() : halfLength();
		Vec3d p = getPos();
		return new Box(p.x - hx, p.y, p.z - hz, p.x + hx, p.y + height(), p.z + hz);
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

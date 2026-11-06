package com.evolvedwarfare.train;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La locomotora de vapor: 12 de largo, va por las vías (las de siempre) y tira de hasta {@link #MAX_WAGONS} plataformas.
 * Quema bloques de carbón (clic derecho con ellos; agachado, el montón entero). Se sube con clic derecho y se lleva con W
 * (acelerar) y S (frenar y marcha atrás); espacio, el silbato. Se baja agachándose.
 */
public class LocomotiveEntity extends TrainCarEntity {
	public static final int MAX_WAGONS = 12;
	public static final double HALF_LENGTH = 6.0, BOGIE = 4.0, GAP = 0.5;
	public static final double MAX_SPEED = 0.6, MAX_REVERSE = 0.25, ACCEL = 0.004, BRAKE = 0.012;
	/** Lo que dura un bloque de carbón (tics a toda marcha). */
	public static final int COAL_BLOCK = 2400;
	public static final int MAX_FUEL = COAL_BLOCK * 64;
	private static final TrackedData<Integer> FUEL = DataTracker.registerData(LocomotiveEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> SPEED = DataTracker.registerData(LocomotiveEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final List<Box> BOXES = boxes();

	@Nullable
	private Track.Pos track;
	private double target;
	private final List<UUID> wagons = new ArrayList<>();
	private int whistle;

	public LocomotiveEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(FUEL, 0);
		builder.add(SPEED, 0.0F);
	}

	/** Lo macizo de la locomotora (en bloques, coordenadas del coche): el bastidor, la caldera, la chimenea, la cabina y la carbonera. */
	private static List<Box> boxes() {
		List<Box> b = new ArrayList<>();
		b.add(new Box(-1.5, 0.55, -6.0, 1.5, FlatcarEntity.DECK, 6.0));     // el bastidor (se pisa)
		b.add(new Box(-1.0, FlatcarEntity.DECK, -1.75, 1.0, 3.25, 5.75));   // la caldera
		b.add(new Box(-0.375, 3.25, 4.25, 0.375, 4.75, 5.0));              // la chimenea
		b.add(new Box(-1.5, FlatcarEntity.DECK, -2.25, 1.5, 4.75, -1.75));  // la pared delantera de la cabina
		b.add(new Box(-1.625, 4.75, -5.25, 1.625, 5.0, -1.75));            // el techo de la cabina (se puede subir)
		b.add(new Box(-1.375, FlatcarEntity.DECK, -6.0, 1.375, 3.0, -5.0)); // la carbonera
		return b;
	}

	@Override
	protected List<Box> localBoxes() {
		return BOXES;
	}

	@Override
	protected int boxesVersion() {
		return 0;
	}

	@Override
	protected double wheelRadius() {
		return 0.75;
	}

	@Override
	protected double halfWidth() {
		return 1.625;
	}

	@Override
	protected double halfLength() {
		return HALF_LENGTH;
	}

	@Override
	protected double height() {
		return 5.0;
	}

	public int fuel() {
		return dataTracker.get(FUEL);
	}

	public void addFuel(int ticks) {
		dataTracker.set(FUEL, MathHelper.clamp(fuel() + ticks, 0, MAX_FUEL));
	}

	public double speed() {
		return dataTracker.get(SPEED);
	}

	public void setTarget(double t) {
		target = MathHelper.clamp(t, -MAX_REVERSE, MAX_SPEED);
	}

	public double target() {
		return target;
	}

	@Nullable
	public Track.Pos track() {
		return track;
	}

	public void setTrack(Track.Pos pos) {
		this.track = pos;
		place();
	}

	public List<UUID> wagons() {
		return wagons;
	}

	/** Las plataformas cargadas, en orden. */
	public List<FlatcarEntity> loadedWagons() {
		List<FlatcarEntity> out = new ArrayList<>();
		if (getWorld() instanceof ServerWorld sw) {
			for (UUID id : wagons) {
				if (sw.getEntity(id) instanceof FlatcarEntity f) {
					out.add(f);
				}
			}
		}
		return out;
	}

	public void addWagon(FlatcarEntity wagon) {
		wagons.add(wagon.getUuid());
		wagon.setLocomotive(getUuid());
		place();
	}

	/** Lo lejos (por la vía, hacia atrás) que va el centro de la plataforma i. */
	public static double wagonOffset(int i) {
		return HALF_LENGTH + GAP + FlatcarEntity.HALF_LENGTH + i * (2 * FlatcarEntity.HALF_LENGTH + GAP);
	}

	// ------------------------------------------------------------------ en la vía

	/** Pone un coche entre sus dos bogies (a "behind" bloques por detrás de la locomotora y con esa separación de bogies). */
	private boolean placeCar(TrainCarEntity car, double behind, double bogie) {
		World world = getWorld();
		Track.Walk front = Track.walk(world, track, -behind + bogie);
		Track.Walk back = Track.walk(world, track, -behind - bogie);
		Vec3d f = Track.point(world, front.pos())[0], b = Track.point(world, back.pos())[0];
		Vec3d mid = f.add(b).multiply(0.5);
		Vec3d dir = f.subtract(b);
		if (dir.horizontalLengthSquared() < 1e-6) {
			dir = Track.point(world, front.pos())[1];
		}
		float yaw = Track.yaw(dir);
		float pitch = (float) (-MathHelper.atan2(dir.y, Math.sqrt(dir.horizontalLengthSquared())) * MathHelper.DEGREES_PER_RADIAN);
		car.moveCar(mid, yaw, pitch);
		return !back.blocked();
	}

	/** Coloca la locomotora y las plataformas en la vía. Devuelve si cabe todo el tren por detrás. */
	public boolean place() {
		if (track == null) {
			return false;
		}
		boolean ok = placeCar(this, 0, BOGIE);
		List<FlatcarEntity> list = loadedWagons();
		for (int i = 0; i < list.size(); i++) {
			ok &= placeCar(list.get(i), wagonOffset(i), 3.5);
		}
		return ok;
	}

	@Override
	public void tick() {
		World world = getWorld();
		if (!world.isClient && world instanceof ServerWorld sw) {
			serverTick(sw);
		} else {
			double v = Math.abs(speed());
			if (fuel() > 0 && (v > 0.001 || age % 6 == 0)) {
				Vec3d chimney = toWorld(0, 4.9, 4.6);
				world.addParticle(v > 0.2 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE, chimney.x, chimney.y, chimney.z,
						(random.nextDouble() - 0.5) * 0.02, 0.08 + v * 0.1, (random.nextDouble() - 0.5) * 0.02);
			}
		}
		super.tick();
	}

	private void serverTick(ServerWorld world) {
		if (track == null || Track.shape(world, track.rail()) == null) {
			dataTracker.set(SPEED, 0.0F);
			return;
		}
		// quien conduce: W acelera, S frena (y, parada, marcha atrás); espacio, el silbato
		Entity driver = getFirstPassenger();
		if (driver instanceof PlayerEntity p) {
			if (p.forwardSpeed > 0.1F) {
				setTarget(target + 0.008);
			} else if (p.forwardSpeed < -0.1F) {
				setTarget(target - 0.012);
			}
			if (Trains.jumping(p) && whistle <= 0) {
				whistle = 40;
				world.playSound(null, getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.NEUTRAL, 3.0F, 1.6F);
			}
			if (age % 10 == 0 && p instanceof ServerPlayerEntity sp) {
				int blocks = (fuel() + COAL_BLOCK - 1) / COAL_BLOCK;
				sp.sendMessage(Text.translatable("message.evolvedwarfare.train.dash", Math.round(speed() * 20 * 3.6), Math.round(target / MAX_SPEED * 100),
						blocks).formatted(fuel() > 0 ? Formatting.GOLD : Formatting.RED), true);
			}
		}
		whistle--;
		double speed = speed();
		if (fuel() <= 0) {
			target = 0;
		}
		// acelera o frena hacia lo que se pide (frena más fuerte que acelera)
		double accel = Math.signum(target - speed) == Math.signum(speed) || speed == 0 ? ACCEL : BRAKE;
		speed = Math.abs(target - speed) <= accel ? target : speed + Math.signum(target - speed) * accel;
		if (Math.abs(speed) > 1e-4 && fuel() > 0) {
			int burn = Math.max(1, (int) Math.round(Math.abs(speed) / MAX_SPEED * 2));
			dataTracker.set(FUEL, Math.max(0, fuel() - burn));
			if (age % Math.max(4, (int) (12 - Math.abs(speed) * 14)) == 0) {
				world.playSound(null, getBlockPos(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.NEUTRAL, 0.35F, 0.5F + (float) Math.abs(speed));
			}
		}
		if (Math.abs(speed) > 1e-5) {
			// lo que mueve: la locomotora va delante; si va marcha atrás, que le quepan las plataformas por detrás
			Track.Walk w = Track.walk(world, track, speed);
			Track.Pos old = track;
			track = w.pos();
			boolean fits = place();
			if (w.blocked() || speed < 0 && !fits) {
				if (!fits) {
					track = old;
					place();
				}
				speed = 0;
				target = 0;
			}
			keepLoaded(world);
		}
		dataTracker.set(SPEED, (float) speed);
		if (age % 20 == 0) {
			place();
		}
	}

	/** Mantiene cargados los chunks por donde va el tren (mientras se mueve). */
	private void keepLoaded(ServerWorld world) {
		ServerChunkManager cm = world.getChunkManager();
		cm.addTicket(Trains.TICKET, new ChunkPos(getBlockPos()), 2, new ChunkPos(getBlockPos()));
		for (FlatcarEntity f : loadedWagons()) {
			ChunkPos c = new ChunkPos(f.getBlockPos());
			cm.addTicket(Trains.TICKET, c, 2, c);
		}
	}

	// ------------------------------------------------------------------ subir, cargar carbón, enganchar

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (stack.isOf(Items.COAL_BLOCK)) {
			if (!getWorld().isClient) {
				int n = player.isSneaking() ? stack.getCount() : 1;
				n = Math.min(n, (MAX_FUEL - fuel()) / COAL_BLOCK);
				if (n <= 0) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.train.full").formatted(Formatting.GRAY), true);
					return ActionResult.FAIL;
				}
				addFuel(n * COAL_BLOCK);
				stack.decrementUnlessCreative(n, player);
				getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.NEUTRAL, 1.0F, 0.8F);
				player.sendMessage(Text.translatable("message.evolvedwarfare.train.fuel", (fuel() + COAL_BLOCK - 1) / COAL_BLOCK).formatted(Formatting.GOLD), true);
			}
			return ActionResult.SUCCESS;
		}
		if (stack.isOf(Trains.FLATCAR_ITEM)) {
			return Trains.couple(this, player, stack);
		}
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (stack.isEmpty() && player.isSneaking()) {
			return Trains.uncouple(this, player);
		}
		if (stack.isEmpty() && !hasPassengers()) {
			if (!getWorld().isClient) {
				player.startRiding(this);
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().isEmpty() && passenger instanceof PlayerEntity;
	}

	/** El maquinista va en la cabina, al lado de la puerta del hogar. */
	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
		return new Vec3d(0.6, FlatcarEntity.DECK, -3.4).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
	}

	@Override
	public Vec3d updatePassengerForDismount(net.minecraft.entity.LivingEntity passenger) {
		Vec3d side = toWorld(2.4, 0.2, -3.4);
		BlockPos p = BlockPos.ofFloored(side);
		return getWorld().getBlockState(p).isAir() ? side : toWorld(0.6, FlatcarEntity.DECK + 0.1, -3.4);
	}

	@Override
	public boolean handleAttack(Entity attacker) {
		return true;
	}

	// ------------------------------------------------------------------ guardar

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Rail")) {
			BlockPos rail = NbtHelper.toBlockPos(nbt, "Rail").orElse(getBlockPos());
			track = new Track.Pos(rail, Direction.byId(nbt.getInt("From")), nbt.getDouble("T"));
		}
		dataTracker.set(FUEL, nbt.getInt("Fuel"));
		dataTracker.set(SPEED, nbt.getFloat("Speed"));
		target = nbt.getDouble("Target");
		wagons.clear();
		for (NbtElement e : nbt.getList("Wagons", NbtElement.INT_ARRAY_TYPE)) {
			wagons.add(NbtHelper.toUuid(e));
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (track != null) {
			nbt.put("Rail", NbtHelper.fromBlockPos(track.rail()));
			nbt.putInt("From", track.from().getId());
			nbt.putDouble("T", track.t());
		}
		nbt.putInt("Fuel", fuel());
		nbt.putFloat("Speed", (float) speed());
		nbt.putDouble("Target", target);
		NbtList list = new NbtList();
		for (UUID id : wagons) {
			list.add(NbtHelper.fromUuid(id));
		}
		nbt.put("Wagons", list);
	}
}

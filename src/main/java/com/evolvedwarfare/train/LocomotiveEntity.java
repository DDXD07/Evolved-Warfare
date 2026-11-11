package com.evolvedwarfare.train;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La locomotora de vapor (fase 52): unos 5 bloques de largo, sobre las vías grandes, y tira de los vagones (ténderes y
 * plataformas) enganchados detrás. Quema carbón (o cualquier combustible de horno) de su caldera (agachado + clic derecho)
 * y, cuando se le acaba, el de los ténderes. Se sube con clic derecho; W y S mueven el regulador (de marcha atrás a toda
 * máquina) y espacio pita.
 */
public class LocomotiveEntity extends TrainCarEntity {
	public static final double HALF_LENGTH = 2.6, BOGIE = 1.6, GAP = 0.3;
	/** Lo más rápido que va (bloques por tic). */
	public static final double TOP_SPEED = 0.5;
	public static final int MAX_NOTCH = 4, MIN_NOTCH = -2, MAX_WAGONS = 10;
	private static final TrackedData<Integer> THROTTLE = DataTracker.registerData(LocomotiveEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> BURNING = DataTracker.registerData(LocomotiveEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Float> SPEED = DataTracker.registerData(LocomotiveEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final double K = 2.5 / 16.0;
	private static final List<Box> BOXES = boxes();
	private static Map<Item, Integer> fuelTimes;

	@Nullable
	private Track.Pos track;
	private final List<UUID> wagons = new ArrayList<>();
	private int fuel, maxFuel = 1, inputCooldown, whistleCooldown;
	private double speed;
	public final Firebox firebox = new Firebox();
	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> fuel;
				case 1 -> maxFuel;
				case 2 -> throttle() + 10;
				default -> (int) Math.round(Math.abs(speed()) * 72.0 * 10.0);
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return 4;
		}
	};

	/** La caldera: tres huecos de combustible. */
	public final class Firebox extends SimpleInventory {
		Firebox() {
			super(3);
		}

		@Override
		public boolean isValid(int slot, ItemStack stack) {
			return fuelTime(stack) > 0;
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return isAlive() && player.squaredDistanceTo(LocomotiveEntity.this) < 10 * 10;
		}
	}

	public LocomotiveEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(THROTTLE, 0);
		builder.add(BURNING, false);
		builder.add(SPEED, 0.0F);
	}

	/** Lo macizo (en bloques, x a lo ancho, z hacia delante): el bastidor, la caldera, la chimenea y la cabina. */
	private static List<Box> boxes() {
		List<Box> b = new ArrayList<>();
		for (Box big : new Box[] {
				new Box(-1.1, 0.45, -2.5, 1.1, 6 * K, 2.5),                  // el bastidor (se pisa)
				new Box(-0.94, 6 * K, -4 * K, 0.94, 16 * K, 15 * K),          // la caldera
				new Box(-0.4, 16 * K, 9 * K, 0.4, 23 * K, 14 * K),            // la chimenea
				new Box(-1.25, 6 * K, -5 * K, 1.25, 22 * K, -4 * K),          // la pared delantera de la cabina
				new Box(-1.4, 22 * K, -16 * K, 1.4, 24 * K, -3 * K)}) {      // el techo de la cabina
			b.addAll(split(big));
		}
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
		return 3 * K;
	}

	@Override
	protected double halfWidth() {
		return 1.4;
	}

	@Override
	protected double halfLength() {
		return HALF_LENGTH;
	}

	@Override
	protected double bogie() {
		return BOGIE;
	}

	@Override
	protected double height() {
		return 24 * K;
	}

	public int throttle() {
		return dataTracker.get(THROTTLE);
	}

	public void setThrottle(int notch) {
		dataTracker.set(THROTTLE, MathHelper.clamp(notch, MIN_NOTCH, MAX_NOTCH));
	}

	public boolean burning() {
		return dataTracker.get(BURNING);
	}

	public double speed() {
		return dataTracker.get(SPEED);
	}

	public int fuel() {
		return fuel;
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

	/** Lo que dura un combustible en la caldera (tics; el carbón, lo que en un horno). */
	public static int fuelTime(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (fuelTimes == null) {
			fuelTimes = AbstractFurnaceBlockEntity.createFuelTimeMap();
		}
		return fuelTimes.getOrDefault(stack.getItem(), 0);
	}

	/** Los vagones cargados, en orden. */
	public List<TrainCarEntity> loadedWagons() {
		List<TrainCarEntity> out = new ArrayList<>();
		if (getWorld() instanceof ServerWorld sw) {
			for (UUID id : wagons) {
				if (sw.getEntity(id) instanceof TrainCarEntity c && c.isAlive()) {
					out.add(c);
				}
			}
		}
		return out;
	}

	public void addWagon(TrainCarEntity wagon) {
		wagons.add(wagon.getUuid());
		wagon.setLocomotive(getUuid());
		place();
	}

	// ------------------------------------------------------------------ en la vía

	/** Pone un coche entre sus dos bogies, a "behind" bloques por detrás de la locomotora. Devuelve si cabía. */
	private boolean placeCar(TrainCarEntity car, double behind) {
		World world = getWorld();
		double bogie = car.bogie();
		Track.Walk front = Track.walk(world, track, -behind + bogie);
		Track.Walk back = Track.walk(world, track, -behind - bogie);
		Vec3d f = Track.point(world, front.pos()), b = Track.point(world, back.pos());
		Vec3d mid = f.add(b).multiply(0.5);
		Vec3d dir = f.subtract(b);
		if (dir.horizontalLengthSquared() < 1e-6) {
			dir = Track.forward(world, front.pos());
		}
		float yaw = Track.yaw(dir);
		float pitch = (float) (-MathHelper.atan2(dir.y, Math.sqrt(dir.horizontalLengthSquared())) * MathHelper.DEGREES_PER_RADIAN);
		car.moveCar(mid, yaw, pitch);
		return !back.blocked() && !front.blocked();
	}

	/** Coloca la locomotora y los vagones en la vía. Devuelve si cabe todo el tren por detrás. */
	public boolean place() {
		if (track == null) {
			return false;
		}
		boolean ok = placeCar(this, 0);
		double behind = HALF_LENGTH;
		for (TrainCarEntity w : loadedWagons()) {
			behind += GAP + w.halfLength();
			ok &= placeCar(w, behind);
			behind += w.halfLength();
		}
		return ok;
	}

	@Override
	public void tick() {
		World world = getWorld();
		if (world instanceof ServerWorld sw) {
			serverTick(sw);
		} else if (burning()) {
			Vec3d chimney = toWorld(0, 24 * K, 12 * K);
			double v = Math.abs(speed());
			if (random.nextInt(2) == 0) {
				world.addParticle(v > 0.25 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE, chimney.x, chimney.y, chimney.z,
						(random.nextDouble() - 0.5) * 0.02, 0.07 + v * 0.1, (random.nextDouble() - 0.5) * 0.02);
			}
		}
		super.tick();
	}

	private void serverTick(ServerWorld world) {
		if (track == null || Track.piece(world, track.piece()) == null) {
			speed = 0;
			dataTracker.set(SPEED, 0.0F);
			dataTracker.set(BURNING, false);
			return;
		}
		if (getFirstPassenger() instanceof ServerPlayerEntity driver) {
			if (inputCooldown > 0) {
				inputCooldown--;
			} else if (driver.forwardSpeed > 0.1F) {
				setThrottle(throttle() + 1);
				inputCooldown = 6;
				click(world);
			} else if (driver.forwardSpeed < -0.1F) {
				setThrottle(throttle() - 1);
				inputCooldown = 6;
				click(world);
			}
			if (whistleCooldown > 0) {
				whistleCooldown--;
			} else if (((com.evolvedwarfare.mixin.LivingEntityJumpingAccessor) driver).evolvedwarfare$jumping()) {
				world.playSound(null, getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), SoundCategory.NEUTRAL, 3.0F, 0.5F);
				world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.NEUTRAL, 0.4F, 2.0F);
				whistleCooldown = 30;
			}
			if (age % 10 == 0) {
				driver.sendMessage(dash(), true);
			}
		}
		int notch = throttle();
		if (notch != 0 && fuel <= 0) {
			refuel(world);
		}
		boolean burning = fuel > 0 && notch != 0;
		if (burning) {
			fuel = Math.max(0, fuel - Math.abs(notch));
			double want = notch * TOP_SPEED / MAX_NOTCH;
			speed += MathHelper.clamp(want - speed, -0.02, 0.01);
			if (age % 40 == 0) {
				world.playSound(null, getBlockPos(), SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.NEUTRAL, 0.8F, 0.8F);
			}
			if (Math.abs(speed) > 0.02 && age % Math.max(4, (int) (14 - Math.abs(speed) * 18)) == 0) {
				world.playSound(null, getBlockPos(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.NEUTRAL, 0.3F, 0.5F + (float) Math.abs(speed));
			}
		} else {
			// sin vapor (o con el regulador cerrado) va frenando
			speed -= Math.signum(speed) * Math.min(Math.abs(speed), 0.006);
		}
		dataTracker.set(BURNING, burning);
		if (Math.abs(speed) > 1e-5) {
			Track.Walk w = Track.walk(world, track, speed);
			Track.Pos old = track;
			track = w.pos();
			boolean fits = place();
			if (w.blocked() || !fits) {
				// fin de la vía (por delante, o por detrás para los vagones): se queda donde cabía
				track = old;
				place();
				speed = 0;
			}
			keepLoaded(world);
		} else if (age % 20 == 0) {
			place();
		}
		dataTracker.set(SPEED, (float) speed);
	}

	/** Coge más combustible: el de su caldera y, si no hay, el de los ténderes de su tren. */
	private boolean refuel(ServerWorld world) {
		if (takeFuel(firebox)) {
			return true;
		}
		for (TrainCarEntity car : loadedWagons()) {
			if (car instanceof TenderEntity tender && takeFuel(tender.inventory)) {
				return true;
			}
		}
		return false;
	}

	private boolean takeFuel(Inventory inv) {
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			int t = fuelTime(s);
			if (t > 0) {
				fuel += t;
				maxFuel = Math.max(t, fuel);
				ItemStack rest = s.getRecipeRemainder();
				s.decrement(1);
				if (s.isEmpty() && !rest.isEmpty()) {
					inv.setStack(i, rest);
				}
				inv.markDirty();
				return true;
			}
		}
		return false;
	}

	private void click(ServerWorld world) {
		world.playSound(null, getBlockPos(), SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.NEUTRAL, 0.5F, 0.8F + throttle() * 0.08F);
	}

	public Text dash() {
		int kmh = (int) Math.round(Math.abs(speed()) * 72.0);
		int notch = throttle();
		Text gear = Text.translatable("message.evolvedwarfare.train.notch." + (notch > 0 ? "forward" : notch < 0 ? "reverse" : "neutral"), Math.abs(notch));
		int minutes = fuel / 1200;
		return Text.translatable("message.evolvedwarfare.train.dash", kmh, gear, fuel > 0 ? Math.max(1, minutes) : 0)
				.formatted(fuel > 0 ? Formatting.GOLD : Formatting.RED);
	}

	/** Mantiene cargados los chunks por donde va el tren (mientras se mueve). */
	private void keepLoaded(ServerWorld world) {
		ServerChunkManager cm = world.getChunkManager();
		ChunkPos c0 = new ChunkPos(getBlockPos());
		cm.addTicket(Trains.TICKET, c0, 2, c0);
		for (TrainCarEntity f : loadedWagons()) {
			ChunkPos c = new ChunkPos(f.getBlockPos());
			cm.addTicket(Trains.TICKET, c, 2, c);
		}
	}

	// ------------------------------------------------------------------ subir, la caldera, enganchar

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		ActionResult common = Trains.carInteract(this, player, hand);
		if (common != ActionResult.PASS) {
			return common;
		}
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (player.isSneaking()) {
			if (getWorld().isClient) {
				return ActionResult.SUCCESS;
			}
			// agachado: la caldera (con un combustible en la mano, se mete directamente)
			if (fuelTime(stack) > 0) {
				ItemStack rest = firebox.addStack(stack.copy());
				if (rest.getCount() != stack.getCount()) {
					if (!player.getAbilities().creativeMode) {
						player.setStackInHand(hand, rest);
					}
					getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
					return ActionResult.CONSUME;
				}
			}
			player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, p) -> new LocomotiveScreenHandler(syncId, inv, firebox, properties, this),
					Text.translatable("container.evolvedwarfare.steam_locomotive")));
			return ActionResult.CONSUME;
		}
		if (!hasPassengers()) {
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
		return new Vec3d(0.45, 6 * K, -11 * K).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
	}

	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		Vec3d side = toWorld(2.2, 0.2, -1.6);
		BlockPos p = BlockPos.ofFloored(side);
		return getWorld().getBlockState(p).isAir() ? side : toWorld(0.45, 6 * K + 0.1, -1.6);
	}

	/** Al quitarla (recogida): suelta lo que lleva en la caldera. */
	public void dropContents() {
		for (int i = 0; i < firebox.size(); i++) {
			ItemStack s = firebox.removeStack(i);
			if (!s.isEmpty()) {
				dropStack(s);
			}
		}
	}

	// ------------------------------------------------------------------ guardar

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Piece")) {
			BlockPos piece = NbtHelper.toBlockPos(nbt, "Piece").orElse(getBlockPos());
			track = new Track.Pos(piece, nbt.getInt("Route"), nbt.getDouble("T"), nbt.getInt("Dir") >= 0 ? 1 : -1);
		}
		fuel = nbt.getInt("Fuel");
		maxFuel = Math.max(1, nbt.getInt("MaxFuel"));
		setThrottle(nbt.getInt("Throttle"));
		speed = nbt.getDouble("Speed");
		dataTracker.set(SPEED, (float) speed);
		wagons.clear();
		for (NbtElement e : nbt.getList("Wagons", NbtElement.INT_ARRAY_TYPE)) {
			wagons.add(NbtHelper.toUuid(e));
		}
		firebox.readNbtList(nbt.getList("Firebox", NbtElement.COMPOUND_TYPE), getRegistryManager());
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (track != null) {
			nbt.put("Piece", NbtHelper.fromBlockPos(track.piece()));
			nbt.putInt("Route", track.route());
			nbt.putDouble("T", track.t());
			nbt.putInt("Dir", track.dir());
		}
		nbt.putInt("Fuel", fuel);
		nbt.putInt("MaxFuel", maxFuel);
		nbt.putInt("Throttle", throttle());
		nbt.putDouble("Speed", speed);
		NbtList list = new NbtList();
		for (UUID id : wagons) {
			list.add(NbtHelper.fromUuid(id));
		}
		nbt.put("Wagons", list);
		nbt.put("Firebox", firebox.toNbtList(getRegistryManager()));
	}
}

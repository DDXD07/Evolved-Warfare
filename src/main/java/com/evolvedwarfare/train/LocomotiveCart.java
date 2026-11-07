package com.evolvedwarfare.train;

import java.util.Map;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * La locomotora de vapor: del tamaño de una vagoneta. Se le mete carbón (o carbón vegetal, o bloques de carbón, o
 * cualquier combustible de horno) en la caldera (agachado + clic derecho), se sube uno (clic derecho) y con W y S se
 * sube y se baja el regulador (de marcha atrás a toda máquina), espacio pita. También se puede mover el regulador desde
 * la caldera, sin subirse. Cuando se le acaba el carbón, tira del ténder. Arrastra a los vagones enganchados detrás.
 */
public class LocomotiveCart extends StorageMinecartEntity implements TrainCar {
	/** Lo más rápido que va el tren (bloques por tic; una vagoneta, 0,4). */
	public static final double TOP_SPEED = 0.5;
	public static final int MAX_NOTCH = 4, MIN_NOTCH = -2;
	private static final TrackedData<Integer> THROTTLE = DataTracker.registerData(LocomotiveCart.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> BURNING = DataTracker.registerData(LocomotiveCart.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static Map<Item, Integer> fuelTimes;

	private final Link link = new Link();
	private int fuel, maxFuel = 1;
	private double headingX = 1.0, headingZ = 0.0;
	private int inputCooldown, whistleCooldown;
	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> fuel;
				case 1 -> maxFuel;
				case 2 -> throttle() + 10;
				default -> (int) Math.round(getVelocity().horizontalLength() * 72.0 * 10.0);
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

	public LocomotiveCart(EntityType<? extends LocomotiveCart> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(THROTTLE, 0);
		builder.add(BURNING, false);
	}

	@Override
	public Link link() {
		return link;
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

	public int fuel() {
		return fuel;
	}

	public void setHeading(Vec3d h) {
		double l = Math.sqrt(h.x * h.x + h.z * h.z);
		if (l > 1e-4) {
			headingX = h.x / l;
			headingZ = h.z / l;
		}
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

	@Override
	public int size() {
		return 3;
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return fuelTime(stack) > 0;
	}

	@Override
	protected ScreenHandler getScreenHandler(int syncId, PlayerInventory inv) {
		return new LocomotiveScreenHandler(syncId, inv, this, properties, this);
	}

	@Override
	public AbstractMinecartEntity.Type getMinecartType() {
		return Type.FURNACE;
	}

	@Override
	public Item asItem() {
		return Trains.LOCOMOTIVE_ITEM;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Trains.LOCOMOTIVE_ITEM);
	}

	@Override
	protected double getMaxSpeed() {
		return (isTouchingWater() ? 0.5 : 1.0) * TOP_SPEED;
	}

	/** Coge más combustible: el de su caldera y, si no hay, el del ténder (o los ténderes) de su tren. */
	private boolean refuel(ServerWorld world) {
		if (takeFuel(this)) {
			return true;
		}
		TrainCar car = TrainCar.find(world, link.back);
		for (int n = 0; car != null && n < 32; n++) {
			if (car instanceof TenderCart tender && takeFuel(tender)) {
				return true;
			}
			car = TrainCar.find(world, car.link().back);
		}
		return false;
	}

	private boolean takeFuel(net.minecraft.inventory.Inventory inv) {
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

	@Override
	public void tick() {
		if (getWorld() instanceof ServerWorld world) {
			drive(world);
		} else if (burning()) {
			smoke();
		}
		super.tick();
		// hacia dónde mira: sigue a la vía (sin darse la vuelta al ir marcha atrás)
		Vec3d v = getVelocity();
		double h = Math.sqrt(v.x * v.x + v.z * v.z);
		if (h > 0.01) {
			double vx = v.x / h, vz = v.z / h;
			if (vx * headingX + vz * headingZ < 0) {
				vx = -vx;
				vz = -vz;
			}
			headingX = vx;
			headingZ = vz;
		}
	}

	private void drive(ServerWorld world) {
		// el maquinista: W y S mueven el regulador, espacio pita
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
				world.playSound(null, getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), SoundCategory.NEUTRAL, 2.5F, 0.5F);
				world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.NEUTRAL, 0.3F, 2.0F);
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
			// hacia la velocidad del regulador, poco a poco (y frenando si va más rápido)
			Vec3d v = getVelocity();
			double along = v.x * headingX + v.z * headingZ;
			double want = notch * TOP_SPEED / MAX_NOTCH;
			double change = MathHelper.clamp(want - along, -0.02, 0.012);
			setVelocity(v.add(headingX * change, 0, headingZ * change));
			if (age % 40 == 0) {
				world.playSound(null, getBlockPos(), SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.NEUTRAL, 0.8F, 0.8F);
			}
		} else if (notch == 0) {
			// con el regulador cerrado, frena poco a poco
			setVelocity(getVelocity().multiply(0.97, 1.0, 0.97));
		}
		dataTracker.set(BURNING, burning);
	}

	private void click(ServerWorld world) {
		world.playSound(null, getBlockPos(), SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.NEUTRAL, 0.5F, 0.8F + throttle() * 0.08F);
	}

	public Text dash() {
		int kmh = (int) Math.round(getVelocity().horizontalLength() * 72.0);
		int notch = throttle();
		Text gear = Text.translatable("message.evolvedwarfare.train.notch." + (notch > 0 ? "forward" : notch < 0 ? "reverse" : "neutral"), Math.abs(notch));
		int minutes = fuel / 1200;
		return Text.translatable("message.evolvedwarfare.train.dash", kmh, gear, fuel > 0 ? Math.max(1, minutes) : 0)
				.formatted(fuel > 0 ? Formatting.GOLD : Formatting.RED);
	}

	private void smoke() {
		Vec3d p = getPos().add(headingX * 0.45, 1.45, headingZ * 0.45);
		if (random.nextInt(2) == 0) {
			getWorld().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z, 0.0, 0.07, 0.0);
		}
		if (random.nextInt(6) == 0) {
			getWorld().addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 0.0, 0.03, 0.0);
		}
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (player.isSneaking()) {
			// agachado: la caldera (con un combustible en la mano, se mete directamente)
			if (fuelTime(stack) > 0 && !getWorld().isClient) {
				ItemStack rest = stack.copy();
				for (int i = 0; i < size() && !rest.isEmpty(); i++) {
					ItemStack in = getStack(i);
					if (in.isEmpty()) {
						setStack(i, rest.copyWithCount(Math.min(rest.getCount(), rest.getMaxCount())));
						rest.decrement(getStack(i).getCount());
					} else if (ItemStack.areItemsAndComponentsEqual(in, rest)) {
						int move = Math.min(rest.getCount(), in.getMaxCount() - in.getCount());
						in.increment(move);
						rest.decrement(move);
					}
				}
				if (rest.getCount() != stack.getCount()) {
					if (!player.getAbilities().creativeMode) {
						player.setStackInHand(hand, rest);
					}
					getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
					return ActionResult.CONSUME;
				}
			}
			return super.interact(player, hand);
		}
		if (hasPassengers()) {
			return ActionResult.PASS;
		}
		if (!getWorld().isClient) {
			return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().isEmpty() && passenger instanceof PlayerEntity;
	}

	@Override
	public void pushAwayFrom(Entity entity) {
		if (!TrainCar.sameTrain(this, entity)) {
			super.pushAwayFrom(entity);
		}
	}

	static void uncoupledMessage(PlayerEntity player, TrainCar car) {
		car.cart().getWorld().playSound(null, car.cart().getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.BLOCKS, 1.0F, 0.8F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.train.uncoupled").formatted(Formatting.GRAY), true);
	}

	@Override
	public void remove(RemovalReason reason) {
		TrainCar.removed(this);
		super.remove(reason);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		link.write(nbt);
		nbt.putInt("Fuel", fuel);
		nbt.putInt("MaxFuel", maxFuel);
		nbt.putInt("Throttle", throttle());
		nbt.putDouble("HeadingX", headingX);
		nbt.putDouble("HeadingZ", headingZ);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		link.read(nbt);
		fuel = nbt.getInt("Fuel");
		maxFuel = Math.max(1, nbt.getInt("MaxFuel"));
		setThrottle(nbt.getInt("Throttle"));
		if (nbt.contains("HeadingX")) {
			setHeading(new Vec3d(nbt.getDouble("HeadingX"), 0, nbt.getDouble("HeadingZ")));
		}
	}
}

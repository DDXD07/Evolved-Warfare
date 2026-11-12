package com.evolvedwarfare.cart;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import com.evolvedwarfare.train.TrainCarEntity;
import com.evolvedwarfare.voxel.VoxelBuild;
import com.evolvedwarfare.voxel.VoxelGrid;
import com.evolvedwarfare.voxel.VoxelHost;
import com.evolvedwarfare.voxel.VoxelMachine;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.screen.BlastFurnaceScreenHandler;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.SmokerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * La carreta: una plataforma de 5x3 bloques sobre dos ruedas, con dos varas delante, sobre la que se construye bloque a
 * bloque (estilo Scrap Mechanic): hasta 3 de alto, con casi cualquier bloque (ver {@link VoxelBuild#allowed}).
 *
 * <ul>
 * <li><b>Tirar tú</b>: con una rienda en la mano, clic derecho: te pones entre las varas y te sigue (vas un poco más
 * despacio). Agáchate para soltarla.</li>
 * <li><b>Con caballo</b>: rienda en la mano y clic derecho en un caballo (domado) que esté cerca de la carreta, o clic
 * en la carreta con un caballo atado a ti: el caballo se pone entre las varas. Súbete y llévala. Para soltarlo,
 * agachado + clic derecho en la carreta (con la mano vacía o la rienda).</li>
 * <li><b>Construir</b>: con un bloque en la mano, clic derecho en la tabla o en un bloque de la carreta: se pone al lado,
 * como en el mundo (se ve dónde antes de ponerlo). A golpes se quita (más deprisa con la herramienta buena).</li>
 * <li><b>Usar</b>: clic derecho en un cofre, barril, horno, fogata, mesa de crafteo, cama, puerta o trampilla de la
 * carreta (agachado, se pone el bloque en vez de usarlo).</li>
 * </ul>
 *
 * <p>Coordenadas propias: +Z hacia delante (las varas), +X a su izquierda, en bloques desde el centro del suelo. Pasan al
 * mundo como las de los coches del tren ({@link TrainCarEntity#toWorld}). La rejilla empieza en la tabla ({@link #DECK}).</p>
 */
public class CartEntity extends TrainCarEntity implements VoxelHost {
	/** Altura de la tabla (encima van los bloques). */
	public static final double DECK = 0.75;
	/** La rejilla: 3 de ancho, 3 de alto, 5 de largo. */
	public static final int WIDTH = 3, HEIGHT = 3, LENGTH = 5;
	/** Del centro de la carreta a quien tira. */
	public static final double PLAYER_HITCH = 4.0;
	public static final double HORSE_HITCH = 4.6;
	public static final double WHEEL_RADIUS = 0.55;
	private static final Identifier PULL_SLOWNESS = EvolvedWarfare.id("cart_pull");
	private static final List<Box> BASE = List.of(new Box(-1.5, 0.0, -2.5, 1.5, DECK, 2.5));
	private static final List<Box> BASE_SPLIT = TrainCarEntity.split(BASE.get(0));
	private static final List<Box> WHEELS = List.of(new Box(1.55, 0.0, -WHEEL_RADIUS, 1.7, 2 * WHEEL_RADIUS, WHEEL_RADIUS),
			new Box(-1.7, 0.0, -WHEEL_RADIUS, -1.55, 2 * WHEEL_RADIUS, WHEEL_RADIUS));

	private static final TrackedData<Integer> PULLER = DataTracker.registerData(CartEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<NbtCompound> GRID = DataTracker.registerData(CartEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	private static final TrackedData<Integer> HURT = DataTracker.registerData(CartEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/** Lo construido encima. */
	private VoxelGrid grid;
	private final VoxelBuild.Damage blockDamage = new VoxelBuild.Damage();
	@Nullable
	private UUID pullerUuid;
	private int pullerWait;
	private float damage;
	/** Quienes durmieron en ella (su punto de reaparición la sigue) y dónde se les puso. */
	private final Set<UUID> sleepers = new HashSet<>();
	@Nullable
	private BlockPos spawnPos;
	/** Cliente: quiénes tiran ahora de alguna carreta (para la postura de los brazos). */
	public static final Map<Integer, Long> CLIENT_PULLERS = new ConcurrentHashMap<>();

	public CartEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = false;
		grid();
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(PULLER, -1);
		builder.add(GRID, new NbtCompound());
		builder.add(HURT, 0);
	}

	// ------------------------------------------------------------------ la rejilla

	@Override
	public VoxelGrid grid() {
		if (grid == null) {
			grid = new VoxelGrid(WIDTH, HEIGHT, LENGTH, -WIDTH / 2.0, DECK, -LENGTH / 2.0);
		}
		return grid;
	}

	@Override
	public Entity self() {
		return this;
	}

	@Override
	public Vec3d gridToWorld(Vec3d local) {
		return toWorld(local.x, local.y, local.z);
	}

	@Override
	public Vec3d worldToGrid(Vec3d world) {
		return toLocal(world);
	}

	public Vec3d toWorld(Vec3d local) {
		return toWorld(local.x, local.y, local.z);
	}

	@Override
	public List<Box> baseBoxes() {
		return BASE;
	}

	@Override
	public void gridChanged() {
		if (!getWorld().isClient) {
			getDataTracker().set(GRID, grid().write(getRegistryManager(), false));
		}
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (GRID.equals(data) && getWorld().isClient) {
			grid().read(getDataTracker().get(GRID), getRegistryManager());
		}
	}

	@Override
	protected List<Box> localBoxes() {
		List<Box> out = new ArrayList<>(BASE_SPLIT);
		out.addAll(grid().collisionBoxes());
		return out;
	}

	@Override
	protected List<Box> localTargets() {
		List<Box> out = new ArrayList<>(BASE);
		out.addAll(WHEELS);
		out.addAll(grid().outlineBoxes());
		return out;
	}

	@Override
	protected int boxesVersion() {
		return grid().version();
	}

	@Override
	protected double halfWidth() {
		return 1.7;
	}

	@Override
	protected double halfLength() {
		return LENGTH / 2.0;
	}

	@Override
	protected double height() {
		return DECK + Math.max(1, grid == null ? 0 : grid.height());
	}

	@Override
	protected double bogie() {
		return 0.0;
	}

	@Override
	protected double wheelRadius() {
		return WHEEL_RADIUS;
	}

	/** Lo que mira el jugador en la carreta (un bloque o la tabla), o null. */
	@Nullable
	public VoxelGrid.Hit aim(PlayerEntity player) {
		return VoxelBuild.aim(this, player, 1.0F);
	}

	// ------------------------------------------------------------------ clic derecho

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		World world = getWorld();
		ItemStack stack = player.getStackInHand(hand);
		Entity puller = puller();
		if (hand == Hand.MAIN_HAND) {
			// agachado: soltar a quien tira
			if (player.isSneaking() && puller != null && (stack.isEmpty() || stack.isOf(Items.LEAD))) {
				if (!world.isClient) {
					unhitch(player);
				}
				return ActionResult.success(world.isClient);
			}
			// un caballo atado a ti: se engancha
			AbstractHorseEntity horse = leashedHorse(player);
			if (horse != null && puller == null) {
				if (!world.isClient) {
					horse.detachLeash(true, false);
					hitchHorse(horse, player);
				}
				return ActionResult.success(world.isClient);
			}
			// con la rienda: tiras tú (o la sueltas)
			if (stack.isOf(Items.LEAD)) {
				if (!world.isClient) {
					if (puller == player) {
						unhitch(player);
					} else if (puller == null) {
						stack.decrementUnlessCreative(1, player);
						hitchPlayer(player);
					} else {
						player.sendMessage(Text.translatable("message.evolvedwarfare.cart.busy").formatted(Formatting.RED), true);
					}
				}
				return ActionResult.success(world.isClient);
			}
		}
		VoxelGrid.Hit hit = aim(player);
		if (hit == null) {
			return ActionResult.PASS;
		}
		// usar lo de encima (agachado no: así se pueden poner bloques contra un cofre)
		if (hand == Hand.MAIN_HAND && hit.index() >= 0 && !player.isSneaking() && VoxelBuild.usable(grid(), hit.index(), stack)) {
			if (!world.isClient) {
				VoxelBuild.use(this, player, hit.index(), stack);
			}
			return ActionResult.success(world.isClient);
		}
		// construir
		if (VoxelBuild.blockOf(stack) != null) {
			VoxelBuild.Placement plan = VoxelBuild.plan(grid(), hit, stack, VoxelBuild.lookLocal(this, player, 1.0F));
			if (plan == null) {
				if (!world.isClient && hand == Hand.MAIN_HAND) {
					player.sendMessage(Text.translatable("message.evolvedwarfare.build.cant").formatted(Formatting.RED), true);
				}
				return ActionResult.FAIL;
			}
			if (!world.isClient) {
				VoxelBuild.apply(this, plan);
				BlockState placed = plan.states()[0];
				stack.decrementUnlessCreative(1, player);
				var g = placed.getSoundGroup();
				Vec3d at = toWorld(grid().center(plan.cells()[0]));
				world.playSound(null, at.x, at.y, at.z, g.getPlaceSound(), SoundCategory.BLOCKS, (g.getVolume() + 1.0F) / 2.0F, g.getPitch() * 0.8F);
			}
			return ActionResult.success(world.isClient);
		}
		return ActionResult.PASS;
	}

	/** Pone un bloque tal cual en una celda (para las pruebas). */
	public void putBlock(int x, int y, int z, BlockState state) {
		VoxelBuild.put(this, grid().index(x, y, z), state);
	}

	/** Golpes: al bloque al que se apunta (se va rompiendo); si se apunta a la tabla o las ruedas, a la carreta. */
	@Override
	public boolean handleAttack(Entity attacker) {
		if (attacker instanceof PlayerEntity player) {
			VoxelGrid.Hit hit = aim(player);
			if (hit != null && hit.index() >= 0) {
				if (!getWorld().isClient) {
					VoxelBuild.hit(this, player, hit.index(), blockDamage);
				}
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ dormir

	/** Como una cama: fija la reaparición junto a la carreta y, de noche y sin monstruos cerca, duermes. */
	@Override
	public void sleepOn(int head, PlayerEntity sleeper) {
		if (!(sleeper instanceof ServerPlayerEntity player)) {
			return;
		}
		ServerWorld world = (ServerWorld) getWorld();
		BlockPos spawn = BlockPos.ofFloored(getPos());
		player.setSpawnPoint(world.getRegistryKey(), spawn, getYaw(), true, true);
		sleepers.add(player.getUuid());
		spawnPos = spawn;
		if (!world.getDimension().natural()) {
			return;
		}
		if (world.isDay()) {
			player.sendMessage(Text.translatable("block.minecraft.bed.no_sleep"), true);
			return;
		}
		if (player.isSleeping() || player.hasVehicle()) {
			return;
		}
		if (!player.isCreative()) {
			Vec3d at = toWorld(grid().center(head));
			List<HostileEntity> monsters = world.getEntitiesByClass(HostileEntity.class,
					new Box(at.x - 8, at.y - 5, at.z - 8, at.x + 8, at.y + 5, at.z + 8), monster -> monster.isAngryAt(player));
			if (!monsters.isEmpty()) {
				player.sendMessage(Text.translatable("block.minecraft.bed.not_safe"), true);
				return;
			}
		}
		Vec3d pillow = toWorld(grid().corner(head).add(0.5, 0.0, 0.5));
		player.sleep(BlockPos.ofFloored(pillow.x, pillow.y + 0.3, pillow.z));
		// tumbado encima de la cama de la carreta (no en el centro del bloque, que es lo que hace vanilla)
		player.networkHandler.requestTeleport(pillow.x, pillow.y + 0.5625, pillow.z, player.getYaw(), player.getPitch());
		world.updateSleepingPlayers();
	}

	/** Si quien duerme lo hace en una carreta (para que vanilla no lo despierte por no ver una cama). */
	public static boolean sleepingOnCart(LivingEntity sleeper) {
		return sleeper.getSleepingPosition().map(pos -> !sleeper.getWorld().getEntitiesByClass(CartEntity.class, new Box(pos).expand(3.0),
				cart -> cart.hasBed()).isEmpty()).orElse(false);
	}

	private boolean hasBed() {
		VoxelGrid g = grid();
		for (int i = 0; i < g.size(); i++) {
			if (g.get(i).getBlock() instanceof BedBlock) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ tirar

	@Nullable
	public Entity puller() {
		int id = getDataTracker().get(PULLER);
		return id < 0 ? null : getWorld().getEntityById(id);
	}

	private void setPuller(@Nullable Entity entity) {
		pullerUuid = entity == null ? null : entity.getUuid();
		getDataTracker().set(PULLER, entity == null ? -1 : entity.getId());
	}

	public static boolean canPull(Entity entity) {
		return entity instanceof AbstractHorseEntity horse && !(horse instanceof LlamaEntity) && horse.isTame() && !horse.isBaby()
				&& horse.isAlive();
	}

	@Nullable
	private AbstractHorseEntity leashedHorse(PlayerEntity player) {
		List<AbstractHorseEntity> horses = getWorld().getEntitiesByClass(AbstractHorseEntity.class, getBoundingBox().expand(10.0),
				horse -> canPull(horse) && horse.getLeashHolder() == player);
		return horses.isEmpty() ? null : horses.get(0);
	}

	public void hitchPlayer(PlayerEntity player) {
		Vec3d spot = toWorld(new Vec3d(0, 0, PLAYER_HITCH));
		setPuller(player);
		slow(player, -0.2);
		if (player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.networkHandler.requestTeleport(spot.x, getY(), spot.z, getYaw(), serverPlayer.getPitch());
		}
		player.sendMessage(Text.translatable("message.evolvedwarfare.cart.pulling").formatted(Formatting.GOLD), true);
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
	}

	public void hitchHorse(AbstractHorseEntity horse, @Nullable PlayerEntity by) {
		Vec3d spot = toWorld(new Vec3d(0, 0, HORSE_HITCH));
		horse.refreshPositionAndAngles(spot.x, getY(), spot.z, getYaw(), 0.0F);
		horse.setBodyYaw(getYaw());
		horse.setHeadYaw(getYaw());
		horse.getNavigation().stop();
		setPuller(horse);
		slow(horse, -0.15);
		if (by != null) {
			by.sendMessage(Text.translatable("message.evolvedwarfare.cart.horse").formatted(Formatting.GOLD), true);
		}
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
	}

	/** Se suelta: la rienda vuelve a quien la suelta (o cae al suelo). */
	public void unhitch(@Nullable PlayerEntity by) {
		Entity puller = puller();
		if (puller instanceof LivingEntity living) {
			EntityAttributeInstance speed = living.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(PULL_SLOWNESS);
			}
		}
		if (pullerUuid != null || puller != null) {
			ItemStack lead = new ItemStack(Items.LEAD);
			if (by != null && !by.getAbilities().creativeMode) {
				by.getInventory().offerOrDrop(lead);
			} else if (by == null) {
				Entity at = puller != null ? puller : this;
				at.dropStack(lead);
			}
			getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_LEASH_KNOT_BREAK, SoundCategory.NEUTRAL, 1.0F, 1.0F);
		}
		setPuller(null);
	}

	private static void slow(LivingEntity entity, double amount) {
		EntityAttributeInstance speed = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null && !speed.hasModifier(PULL_SLOWNESS)) {
			speed.addTemporaryModifier(new EntityAttributeModifier(PULL_SLOWNESS, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Rienda en la mano y clic en un caballo con una carreta cerca: se engancha directamente. */
	public static void registerEvents() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (!stack.isOf(Items.LEAD) || !canPull(entity) || entity.hasPassengers()) {
				return ActionResult.PASS;
			}
			List<CartEntity> carts = world.getEntitiesByClass(CartEntity.class, entity.getBoundingBox().expand(6.0), cart -> cart.puller() == null);
			if (carts.isEmpty()) {
				return ActionResult.PASS;
			}
			if (!world.isClient) {
				CartEntity cart = carts.get(0);
				for (CartEntity other : carts) {
					if (other.squaredDistanceTo(entity) < cart.squaredDistanceTo(entity)) {
						cart = other;
					}
				}
				stack.decrementUnlessCreative(1, player);
				cart.hitchHorse((AbstractHorseEntity) entity, player);
			}
			return ActionResult.SUCCESS;
		});
	}

	// ------------------------------------------------------------------ cada tic

	@Override
	public void tick() {
		super.tick();
		World world = getWorld();
		if (getDataTracker().get(HURT) > 0) {
			getDataTracker().set(HURT, getDataTracker().get(HURT) - 1);
		}
		Entity puller = world.isClient ? puller() : resolvePuller();
		if (puller != null) {
			follow(puller);
			if (world.isClient) {
				CLIENT_PULLERS.put(puller.getId(), world.getTime());
			}
		} else {
			fall();
		}
		if (world.isClient) {
			particles();
			return;
		}
		tickMachines();
		if (age % 40 == 0) {
			moveSpawns((ServerWorld) world);
		}
	}

	/** Los hornos funden y las fogatas cocinan (también en marcha). */
	private void tickMachines() {
		VoxelGrid g = grid();
		boolean sync = false;
		for (int i = 0; i < g.size(); i++) {
			VoxelMachine m = g.machine(i);
			if (m == null) {
				continue;
			}
			if (m.kind.furnace()) {
				if (m.tickFurnace(getWorld()) && g.get(i).contains(Properties.LIT)) {
					g.set(i, g.get(i).with(Properties.LIT, m.lit()));
					sync = true;
				}
			} else if (m.kind == VoxelMachine.Kind.CAMPFIRE && g.get(i).get(Properties.LIT)) {
				ItemStack done = m.tickCampfire(getWorld());
				if (!done.isEmpty()) {
					Vec3d at = toWorld(grid().corner(i).add(0.5, 0.6, 0.5));
					net.minecraft.entity.ItemEntity item = new net.minecraft.entity.ItemEntity(getWorld(), at.x, at.y, at.z, done);
					item.setVelocity(Vec3d.ZERO);
					getWorld().spawnEntity(item);
					sync = true;
				}
			}
		}
		if (sync) {
			gridChanged();
		}
	}

	/** Cliente: humo de las fogatas y los hornos encendidos, y la llama de antorchas. */
	private void particles() {
		VoxelGrid g = grid();
		for (int i = 0; i < g.size(); i++) {
			BlockState s = g.get(i);
			if (s.isAir()) {
				continue;
			}
			Block b = s.getBlock();
			boolean lit = s.contains(Properties.LIT) && s.get(Properties.LIT);
			Vec3d c = g.corner(i);
			if (b instanceof CampfireBlock && lit && random.nextInt(3) == 0) {
				Vec3d at = toWorld(c.x + 0.5 + (random.nextDouble() - 0.5) * 0.4, c.y + 0.5, c.z + 0.5 + (random.nextDouble() - 0.5) * 0.4);
				getWorld().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 0.0, 0.07, 0.0);
			} else if (b instanceof AbstractFurnaceBlock && lit && random.nextInt(12) == 0) {
				Vec3d at = toWorld(c.x + 0.5, c.y + 1.0, c.z + 0.5);
				getWorld().addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0.0, 0.02, 0.0);
			} else if ((b == Blocks.TORCH || b == Blocks.SOUL_TORCH) && random.nextInt(4) == 0) {
				Vec3d at = toWorld(c.x + 0.5, c.y + 0.7, c.z + 0.5);
				getWorld().addParticle(b == Blocks.TORCH ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 0.0, 0.0, 0.0);
			}
		}
	}

	/** El calor de lo que arde encima (para el clima), con su sitio. */
	public void heat(java.util.function.BiConsumer<Vec3d, Float> out) {
		VoxelGrid g = grid();
		for (int i = 0; i < g.size(); i++) {
			VoxelMachine m = g.machine(i);
			if (m != null) {
				float w = m.warmth(g.get(i));
				if (w > 0.0F) {
					out.accept(toWorld(g.center(i)), w);
				}
			}
		}
	}

	@Nullable
	private Entity resolvePuller() {
		if (pullerUuid == null) {
			return null;
		}
		Entity puller = ((ServerWorld) getWorld()).getEntity(pullerUuid);
		if (puller == null || !puller.isAlive()) {
			// aún no ha cargado (o se fue): se espera un rato y si no, se suelta
			if (++pullerWait > 600 || puller != null) {
				unhitch(null);
			}
			return null;
		}
		pullerWait = 0;
		if (getDataTracker().get(PULLER) != puller.getId()) {
			getDataTracker().set(PULLER, puller.getId());
		}
		if (puller instanceof LivingEntity living) {
			slow(living, puller instanceof PlayerEntity ? -0.2 : -0.15);
		}
		if (puller instanceof PlayerEntity player && player.isSneaking() && !player.hasVehicle()) {
			unhitch(player);
			player.sendMessage(Text.translatable("message.evolvedwarfare.cart.released"), true);
			return null;
		}
		if (puller instanceof AbstractHorseEntity horse && !horse.hasPassengers()) {
			horse.getNavigation().stop();
		}
		return puller;
	}

	/** Detrás de quien tira, a su distancia, mirando hacia él (como un remolque). */
	private void follow(Entity puller) {
		double length = puller instanceof AbstractHorseEntity ? HORSE_HITCH : PLAYER_HITCH;
		double dx = puller.getX() - getX();
		double dz = puller.getZ() - getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d < 1.0E-4) {
			return;
		}
		if (!getWorld().isClient && (d > length + 6.0 || puller.getWorld() != getWorld())) {
			if (puller instanceof PlayerEntity player) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.cart.snapped").formatted(Formatting.RED), true);
			}
			unhitch(null);
			return;
		}
		double nx = dx / d;
		double nz = dz / d;
		setYaw((float) (MathHelper.atan2(-nx, nz) * MathHelper.DEGREES_PER_RADIAN));
		double vy = isOnGround() ? -0.04 : Math.max(-2.0, getVelocity().y - 0.08);
		move(MovementType.SELF, new Vec3d(puller.getX() - nx * length - getX(), vy, puller.getZ() - nz * length - getZ()));
		setVelocity(0.0, isOnGround() ? 0.0 : vy, 0.0);
	}

	private void fall() {
		if (isOnGround() && getVelocity().y <= 0.0) {
			setVelocity(Vec3d.ZERO);
			return;
		}
		double vy = Math.max(-2.0, getVelocity().y - 0.08);
		move(MovementType.SELF, new Vec3d(0.0, vy, 0.0));
		setVelocity(0.0, isOnGround() ? 0.0 : vy, 0.0);
	}

	/** Quien durmió en ella reaparece junto a la carreta, aunque se haya movido (si no ha dormido en otra cama). */
	private void moveSpawns(ServerWorld world) {
		if (sleepers.isEmpty() || spawnPos == null) {
			return;
		}
		BlockPos now = BlockPos.ofFloored(getPos());
		if (now.isWithinDistance(spawnPos, 2.0)) {
			return;
		}
		for (UUID uuid : new ArrayList<>(sleepers)) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
			if (player == null) {
				continue;
			}
			if (!spawnPos.equals(player.getSpawnPointPosition()) || player.getSpawnPointDimension() != world.getRegistryKey()) {
				sleepers.remove(uuid);
				continue;
			}
			player.setSpawnPoint(world.getRegistryKey(), now, getYaw(), true, false);
		}
		spawnPos = now;
	}

	/** En el cliente, la posición la lleva él mismo mientras alguien tira (si no, va a tirones). */
	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
		if (getWorld().isClient && puller() != null && squaredDistanceTo(x, y, z) < 4.0) {
			return;
		}
		super.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, interpolationSteps);
	}

	@Override
	public float getStepHeight() {
		return 1.0F;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public float getTargetingMargin() {
		return 0.6F;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(ModItems.CART);
	}

	// ------------------------------------------------------------------ romperla

	public int hurtTime() {
		return getDataTracker().get(HURT);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		World world = getWorld();
		if (world.isClient || isRemoved()) {
			return true;
		}
		if (isInvulnerableTo(source)) {
			return false;
		}
		Entity attacker = source.getAttacker();
		boolean creative = attacker instanceof PlayerEntity player && player.getAbilities().creativeMode && source.getSource() == player;
		if (attacker instanceof LivingEntity living && source.getSource() == living && living.getMainHandStack().getItem() instanceof AxeItem) {
			amount *= 2.0F;
		}
		damage += amount * 5.0F;
		getDataTracker().set(HURT, 10);
		emitGameEvent(GameEvent.ENTITY_DAMAGE, attacker);
		if (creative || damage >= 40.0F) {
			breakApart(!creative);
		} else {
			world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 1.0F, 0.9F);
		}
		return true;
	}

	/** Rota: suelta lo de encima (con lo de dentro) y, si no es en creativo, la propia carreta. */
	private void breakApart(boolean dropCart) {
		unhitch(null);
		VoxelGrid g = grid();
		for (int i = 0; i < g.size(); i++) {
			if (!g.isEmpty(i)) {
				Vec3d at = toWorld(g.center(i));
				for (ItemStack item : VoxelBuild.remove(this, i, true)) {
					VoxelBuild.drop(getWorld(), at, item);
				}
			}
		}
		if (dropCart) {
			dropStack(new ItemStack(ModItems.CART));
		}
		if (getWorld() instanceof ServerWorld serverWorld) {
			BlockState planks = Blocks.OAK_PLANKS.getDefaultState();
			serverWorld.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, planks), getX(), getY() + 0.6, getZ(), 50, 1.0, 0.3, 1.0, 0.1);
		}
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 1.0F, 1.1F);
		discard();
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!getWorld().isClient && reason.shouldDestroy() && (pullerUuid != null || puller() != null)) {
			unhitch(null);
		}
		super.remove(reason);
	}

	// ------------------------------------------------------------------ guardar

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.put("Grid", grid().write(getRegistryManager(), true));
		if (pullerUuid != null) {
			nbt.putUuid("Puller", pullerUuid);
		}
		NbtList sleeping = new NbtList();
		for (UUID uuid : sleepers) {
			NbtCompound c = new NbtCompound();
			c.putUuid("Id", uuid);
			sleeping.add(c);
		}
		nbt.put("Sleepers", sleeping);
		if (spawnPos != null) {
			nbt.putLong("SpawnPos", spawnPos.asLong());
		}
		nbt.putFloat("Damage", damage);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Grid")) {
			grid().read(nbt.getCompound("Grid"), getRegistryManager());
		} else {
			migrate(nbt.getList("Modules", NbtElement.COMPOUND_TYPE));
		}
		pullerUuid = nbt.containsUuid("Puller") ? nbt.getUuid("Puller") : null;
		sleepers.clear();
		NbtList sleeping = nbt.getList("Sleepers", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < sleeping.size(); i++) {
			sleepers.add(sleeping.getCompound(i).getUuid("Id"));
		}
		spawnPos = nbt.contains("SpawnPos") ? BlockPos.fromLong(nbt.getLong("SpawnPos")) : null;
		damage = nbt.getFloat("Damage");
		gridChanged();
	}

	/**
	 * Las carretas de antes (3x3 huecos con cofres, hornos, mesas y camas): cada cosa pasa a su bloque, en el mismo sitio
	 * de la tabla nueva (las tres filas del centro), con lo que tenía dentro.
	 */
	private void migrate(NbtList list) {
		VoxelGrid g = grid();
		for (int k = 0; k < list.size(); k++) {
			NbtCompound c = list.getCompound(k);
			int cell = c.getInt("Cell");
			CartModule module = CartModule.fromNbt(this, c, getRegistryManager());
			if (module == null || cell < 0 || cell >= 9) {
				continue;
			}
			int at = g.index(2 - cell % 3, 0, 3 - cell / 3);
			BlockState state = switch (module.kind) {
				case CHEST -> Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH);
				case BARREL -> Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP);
				case FURNACE -> Blocks.FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH).with(Properties.LIT, module.lit());
				case SMOKER -> Blocks.SMOKER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH).with(Properties.LIT, module.lit());
				case BLAST_FURNACE -> Blocks.BLAST_FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH)
						.with(Properties.LIT, module.lit());
				case CRAFTING -> Blocks.CRAFTING_TABLE.getDefaultState();
				case BED -> null;
			};
			if (module.kind == CartModule.Kind.BED) {
				if (module.partner < 0 || module.partner >= 9 || !(module.item.getItem() instanceof BlockItem bed)) {
					continue;
				}
				int foot = g.index(2 - module.partner % 3, 0, 3 - module.partner / 3);
				Direction toHead = Direction.getFacing(g.x(at) - g.x(foot), 0, g.z(at) - g.z(foot));
				BlockState s = bed.getBlock().getDefaultState().with(Properties.HORIZONTAL_FACING, toHead);
				g.set(foot, s.with(Properties.BED_PART, net.minecraft.block.enums.BedPart.FOOT));
				g.set(at, s.with(Properties.BED_PART, net.minecraft.block.enums.BedPart.HEAD));
				continue;
			}
			g.set(at, state);
			VoxelMachine.Kind kind = VoxelMachine.Kind.of(state.getBlock());
			if (kind != null) {
				VoxelMachine m = new VoxelMachine(kind);
				for (int i = 0; i < Math.min(m.inventory.size(), module.inventory.size()); i++) {
					m.inventory.setStack(i, module.inventory.getStack(i).copy());
				}
				m.burn = module.burnTime();
				m.burnTotal = module.fuelTime();
				g.setMachine(at, m);
			}
		}
	}
}

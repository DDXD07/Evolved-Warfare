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
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
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
 * La carreta: una plataforma de 3x3 bloques sobre dos ruedas, con dos varas delante.
 *
 * <ul>
 * <li><b>Tirar tú</b>: con una rienda en la mano, clic derecho: te pones entre las varas y te sigue (vas un poco más
 * despacio). Agáchate para soltarla.</li>
 * <li><b>Con caballo</b>: rienda en la mano y clic derecho en un caballo (domado) que esté cerca de la carreta, o clic
 * en la carreta con un caballo atado a ti: el caballo se pone entre las varas. Súbete y llévala. Para soltarlo,
 * agachado + clic derecho en la carreta.</li>
 * <li><b>Clic derecho con la mano vacía</b>: la pantalla de la carga, vista desde arriba: 9 huecos (3x3) para cofres,
 * barriles, hornos, mesas de crafteo y camas (la cama ocupa dos). Lo que pongas se ve encima y se usa como en el mundo
 * (clic derecho en ello).</li>
 * </ul>
 *
 * <p>Coordenadas propias (para las celdas y el dibujo): +Z hacia delante (las varas), +X a su izquierda, en bloques
 * desde el centro del suelo. Pasan al mundo girando -yaw, como {@link Vec3d#rotateY}. Hueco {@code i}: fila
 * {@code i / 3} (0 = delante) y columna {@code i % 3} (0 = izquierda, como se ve en la pantalla).</p>
 */
public class CartEntity extends Entity implements ExtendedScreenHandlerFactory<Integer> {
	public static final int CELLS = 9;
	/** Altura de la tabla (encima van los bloques). */
	public static final double DECK = 0.75;
	/** Del centro de la carreta a quien tira. */
	public static final double PLAYER_HITCH = 3.0;
	public static final double HORSE_HITCH = 3.6;
	public static final double WHEEL_RADIUS = 0.55;
	private static final Identifier PULL_SLOWNESS = EvolvedWarfare.id("cart_pull");

	private static final TrackedData<Integer> PULLER = DataTracker.registerData(CartEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<NbtCompound> LOOK = DataTracker.registerData(CartEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	private static final TrackedData<Integer> HURT = DataTracker.registerData(CartEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/** Lo que hay en cada hueco (solo en el de la cabecera de la cama; el de los pies queda vacío pero ocupado). */
	private final CartModule[] modules = new CartModule[CELLS];
	@Nullable
	private UUID pullerUuid;
	private int pullerWait;
	private float damage;
	/** Quienes durmieron en ella (su punto de reaparición la sigue) y dónde se les puso. */
	private final Set<UUID> sleepers = new HashSet<>();
	@Nullable
	private BlockPos spawnPos;
	// cliente: lo que lleva encima (para dibujarlo) y el giro de las ruedas
	private final Look[] look = new Look[CELLS];
	public float wheelAngle;
	public float prevWheelAngle;
	/** Cliente: quiénes tiran ahora de alguna carreta (para la postura de los brazos). */
	public static final Map<Integer, Long> CLIENT_PULLERS = new ConcurrentHashMap<>();

	/** Lo que se ve de un hueco: el objeto, dónde van los pies (cama) y si arde (horno). */
	public record Look(Item item, int partner, boolean lit) {
	}

	public CartEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(PULLER, -1);
		builder.add(LOOK, new NbtCompound());
		builder.add(HURT, 0);
	}

	// ------------------------------------------------------------------ geometría

	/** El centro de un hueco, a la altura de la tabla (coordenadas propias). */
	public static Vec3d cellCenter(int cell) {
		return new Vec3d(1 - cell % 3, DECK, 1 - cell / 3);
	}

	public Vec3d toWorld(Vec3d local) {
		return getPos().add(local.rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE));
	}

	public Vec3d toLocal(Vec3d world) {
		return world.subtract(getPos()).rotateY(getYaw() * MathHelper.RADIANS_PER_DEGREE);
	}

	private static boolean inside(int row, int col) {
		return row >= 0 && row < 3 && col >= 0 && col < 3;
	}

	/** Dónde irían los pies de una cama con la cabecera en {@code head}: detrás, delante, a un lado o al otro. */
	public static int bedPartner(int head, java.util.function.IntPredicate taken) {
		int r = head / 3;
		int c = head % 3;
		int[][] tries = {{r + 1, c}, {r - 1, c}, {r, c + 1}, {r, c - 1}};
		for (int[] t : tries) {
			if (inside(t[0], t[1]) && !taken.test(t[0] * 3 + t[1])) {
				return t[0] * 3 + t[1];
			}
		}
		return -1;
	}

	/** La caja de lo que hay en un hueco (coordenadas propias): un bloque, o la cama entera (baja y de dos huecos). */
	public static Box moduleBox(int cell, @Nullable CartModule.Kind kind, int partner) {
		Vec3d c = cellCenter(cell);
		Box box = new Box(c.x - 0.5, DECK, c.z - 0.5, c.x + 0.5, DECK + 1.0, c.z + 0.5);
		if (kind == CartModule.Kind.BED && partner >= 0) {
			Vec3d p = cellCenter(partner);
			box = new Box(Math.min(c.x, p.x) - 0.5, DECK, Math.min(c.z, p.z) - 0.5, Math.max(c.x, p.x) + 0.5, DECK + 0.5625,
					Math.max(c.z, p.z) + 0.5);
		}
		return box;
	}

	/** El hueco de lo que mira el jugador encima de la carreta, o -1 (la tabla u otra cosa). */
	public int pickCell(PlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(player.getEntityInteractionRange() + 1.0));
		Vec3d a = toLocal(eye);
		Vec3d b = toLocal(end);
		int best = -1;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < CELLS; i++) {
			CartModule.Kind kind = kindAt(i);
			if (kind == null) {
				continue;
			}
			Optional<Vec3d> hit = moduleBox(i, kind, partnerAt(i)).raycast(a, b);
			if (hit.isPresent() && hit.get().squaredDistanceTo(a) < bestDistance) {
				best = i;
				bestDistance = hit.get().squaredDistanceTo(a);
			}
		}
		Optional<Vec3d> deck = new Box(-1.5, 0.0, -1.5, 1.5, DECK, 1.5).raycast(a, b);
		if (deck.isPresent() && deck.get().squaredDistanceTo(a) < bestDistance) {
			return -1;
		}
		return best;
	}

	// ------------------------------------------------------------------ lo que lleva

	@Nullable
	public CartModule module(int cell) {
		return cell >= 0 && cell < CELLS ? modules[cell] : null;
	}

	/** Si ese hueco está ocupado (por algo o por los pies de una cama). */
	public boolean taken(int cell) {
		return modules[cell] != null || bedHeadOf(cell) >= 0;
	}

	/** Si en ese hueco están los pies de una cama, el hueco de su cabecera; si no, -1. */
	public int bedHeadOf(int cell) {
		for (int i = 0; i < CELLS; i++) {
			if (modules[i] != null && modules[i].partner == cell) {
				return i;
			}
		}
		return -1;
	}

	@Nullable
	private CartModule.Kind kindAt(int cell) {
		if (getWorld().isClient) {
			Look l = look[cell];
			return l == null ? null : CartModule.kindOf(new ItemStack(l.item()));
		}
		return modules[cell] == null ? null : modules[cell].kind;
	}

	private int partnerAt(int cell) {
		if (getWorld().isClient) {
			return look[cell] == null ? -1 : look[cell].partner();
		}
		return modules[cell] == null ? -1 : modules[cell].partner;
	}

	@Nullable
	public Look look(int cell) {
		return look[cell];
	}

	/** Pone algo en un hueco (la cama busca dónde van los pies). Devuelve si se pudo. */
	public boolean install(int cell, ItemStack stack) {
		CartModule.Kind kind = CartModule.kindOf(stack);
		if (kind == null || taken(cell)) {
			return false;
		}
		CartModule module = new CartModule(this, kind, stack);
		if (kind == CartModule.Kind.BED) {
			module.partner = bedPartner(cell, i -> i == cell || taken(i));
			if (module.partner < 0) {
				return false;
			}
		}
		modules[cell] = module;
		syncLook();
		return true;
	}

	/** Quita lo de un hueco: lo de dentro cae al suelo y se devuelve el objeto. */
	public ItemStack uninstall(int cell) {
		CartModule module = modules[cell];
		if (module == null) {
			return ItemStack.EMPTY;
		}
		modules[cell] = null;
		if (!getWorld().isClient) {
			module.dropContents(getWorld(), toWorld(cellCenter(cell).add(0, 0.5, 0)));
		}
		syncLook();
		return module.item.copy();
	}

	private void syncLook() {
		if (getWorld().isClient) {
			return;
		}
		NbtList list = new NbtList();
		for (int i = 0; i < CELLS; i++) {
			CartModule m = modules[i];
			if (m != null) {
				NbtCompound c = new NbtCompound();
				c.putInt("s", i);
				c.putString("i", Registries.ITEM.getId(m.item.getItem()).toString());
				c.putInt("p", m.partner);
				c.putBoolean("l", m.lit());
				list.add(c);
			}
		}
		NbtCompound nbt = new NbtCompound();
		nbt.put("M", list);
		getDataTracker().set(LOOK, nbt);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (LOOK.equals(data)) {
			java.util.Arrays.fill(look, null);
			NbtList list = getDataTracker().get(LOOK).getList("M", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				NbtCompound c = list.getCompound(i);
				int s = c.getInt("s");
				if (s >= 0 && s < CELLS) {
					look[s] = new Look(Registries.ITEM.get(Identifier.of(c.getString("i"))), c.getInt("p"), c.getBoolean("l"));
				}
			}
		}
	}

	/** Los 9 huecos como inventario (para la pantalla de la carga). */
	public final Inventory cells = new Inventory() {
		@Override
		public int size() {
			return CELLS;
		}

		@Override
		public boolean isEmpty() {
			for (CartModule m : modules) {
				if (m != null) {
					return false;
				}
			}
			return true;
		}

		@Override
		public ItemStack getStack(int slot) {
			return modules[slot] == null ? ItemStack.EMPTY : modules[slot].item;
		}

		@Override
		public ItemStack removeStack(int slot, int amount) {
			return amount > 0 ? uninstall(slot) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeStack(int slot) {
			return uninstall(slot);
		}

		@Override
		public void setStack(int slot, ItemStack stack) {
			if (modules[slot] != null && stack == modules[slot].item) {
				return;
			}
			uninstall(slot);
			if (!stack.isEmpty()) {
				install(slot, stack);
			}
		}

		@Override
		public int getMaxCountPerStack() {
			return 1;
		}

		@Override
		public boolean isValid(int slot, ItemStack stack) {
			CartModule.Kind kind = CartModule.kindOf(stack);
			return kind != null && !taken(slot) && (kind != CartModule.Kind.BED || bedPartner(slot, i -> i == slot || taken(i)) >= 0);
		}

		@Override
		public void markDirty() {
			syncLook();
		}

		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return isAlive() && player.squaredDistanceTo(CartEntity.this) <= 64.0;
		}

		@Override
		public void clear() {
			for (int i = 0; i < CELLS; i++) {
				uninstall(i);
			}
		}
	};

	/** Para la pantalla: en cada hueco con los pies de una cama, su cabecera + 1 (0 si no). */
	public final PropertyDelegate links = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return bedHeadOf(index) + 1;
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return CELLS;
		}
	};

	@Override
	public Integer getScreenOpeningData(ServerPlayerEntity player) {
		return getId();
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new CartScreenHandler(syncId, playerInventory, this);
	}

	// ------------------------------------------------------------------ clic derecho

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		World world = getWorld();
		ItemStack stack = player.getStackInHand(hand);
		Entity puller = puller();
		// agachado: soltar a quien tira
		if (player.isSneaking() && puller != null) {
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
		int cell = pickCell(player);
		if (cell >= 0) {
			if (!world.isClient) {
				use(cell, player);
			}
			return ActionResult.success(world.isClient);
		}
		if (stack.isEmpty()) {
			if (!world.isClient) {
				player.openHandledScreen(this);
			}
			return ActionResult.success(world.isClient);
		}
		return ActionResult.PASS;
	}

	/** Usar lo que hay en un hueco, como en el mundo. */
	public void use(int cell, PlayerEntity player) {
		CartModule m = modules[cell];
		if (m == null) {
			return;
		}
		switch (m.kind) {
			case CHEST, BARREL -> player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(id, inventory, p) -> GenericContainerScreenHandler.createGeneric9x3(id, inventory, m.inventory), m.item.getName()));
			case FURNACE -> player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(id, inventory, p) -> new FurnaceScreenHandler(id, inventory, m.inventory, m.furnaceProperties), m.item.getName()));
			case SMOKER -> player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(id, inventory, p) -> new SmokerScreenHandler(id, inventory, m.inventory, m.furnaceProperties), m.item.getName()));
			case BLAST_FURNACE -> player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(id, inventory, p) -> new BlastFurnaceScreenHandler(id, inventory, m.inventory, m.furnaceProperties), m.item.getName()));
			case CRAFTING -> player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(id, inventory, p) -> new CartCraftingScreenHandler(id, inventory, this), Text.translatable("container.crafting")));
			case BED -> {
				if (player instanceof ServerPlayerEntity serverPlayer) {
					sleep(cell, serverPlayer);
				}
			}
		}
	}

	// ------------------------------------------------------------------ dormir

	/** Como una cama: fija la reaparición junto a la carreta y, de noche y sin monstruos cerca, duermes. */
	private void sleep(int cell, ServerPlayerEntity player) {
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
			Vec3d at = toWorld(cellCenter(cell));
			List<HostileEntity> monsters = world.getEntitiesByClass(HostileEntity.class,
					new Box(at.x - 8, at.y - 5, at.z - 8, at.x + 8, at.y + 5, at.z + 8), monster -> monster.isAngryAt(player));
			if (!monsters.isEmpty()) {
				player.sendMessage(Text.translatable("block.minecraft.bed.not_safe"), true);
				return;
			}
		}
		Vec3d head = toWorld(cellCenter(cell));
		player.sleep(BlockPos.ofFloored(head.x, head.y + 0.3, head.z));
		// tumbado encima de la cama de la carreta (no en el centro del bloque, que es lo que hace vanilla)
		player.networkHandler.requestTeleport(head.x, head.y + 0.5625, head.z, player.getYaw(), player.getPitch());
		world.updateSleepingPlayers();
	}

	/** Si quien duerme lo hace en una carreta (para que vanilla no lo despierte por no ver una cama). */
	public static boolean sleepingOnCart(LivingEntity sleeper) {
		return sleeper.getSleepingPosition().map(pos -> !sleeper.getWorld().getEntitiesByClass(CartEntity.class, new Box(pos).expand(3.0),
				cart -> cart.hasBed()).isEmpty()).orElse(false);
	}

	private boolean hasBed() {
		for (int i = 0; i < CELLS; i++) {
			if (kindAt(i) == CartModule.Kind.BED) {
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
		prevWheelAngle = wheelAngle;
		double beforeX = getX();
		double beforeZ = getZ();
		Entity puller = world.isClient ? puller() : resolvePuller();
		if (puller != null) {
			follow(puller);
			if (world.isClient) {
				CLIENT_PULLERS.put(puller.getId(), world.getTime());
			}
		} else {
			fall();
		}
		// las ruedas giran con lo que avanza hacia delante (o hacia atrás)
		double fx = -MathHelper.sin(getYaw() * MathHelper.RADIANS_PER_DEGREE);
		double fz = MathHelper.cos(getYaw() * MathHelper.RADIANS_PER_DEGREE);
		wheelAngle += (float) (((getX() - beforeX) * fx + (getZ() - beforeZ) * fz) / WHEEL_RADIUS);
		if (!world.isClient) {
			boolean lit = false;
			for (CartModule m : modules) {
				if (m != null && m.kind.furnace()) {
					lit |= m.tickFurnace(world);
				}
			}
			if (lit) {
				syncLook();
			}
			if (age % 40 == 0) {
				moveSpawns((ServerWorld) world);
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
		for (int i = 0; i < CELLS; i++) {
			if (modules[i] != null) {
				Vec3d at = toWorld(cellCenter(i).add(0, 0.5, 0));
				ItemStack item = uninstall(i);
				getWorld().spawnEntity(new net.minecraft.entity.ItemEntity(getWorld(), at.x, at.y, at.z, item));
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
		NbtList list = new NbtList();
		for (int i = 0; i < CELLS; i++) {
			if (modules[i] != null) {
				list.add(modules[i].toNbt(i, getRegistryManager()));
			}
		}
		nbt.put("Modules", list);
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
		java.util.Arrays.fill(modules, null);
		NbtList list = nbt.getList("Modules", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound c = list.getCompound(i);
			int cell = c.getInt("Cell");
			CartModule module = CartModule.fromNbt(this, c, getRegistryManager());
			if (module != null && cell >= 0 && cell < CELLS) {
				modules[cell] = module;
			}
		}
		pullerUuid = nbt.containsUuid("Puller") ? nbt.getUuid("Puller") : null;
		sleepers.clear();
		NbtList sleeping = nbt.getList("Sleepers", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < sleeping.size(); i++) {
			sleepers.add(sleeping.getCompound(i).getUuid("Id"));
		}
		spawnPos = nbt.contains("SpawnPos") ? BlockPos.fromLong(nbt.getLong("SpawnPos")) : null;
		damage = nbt.getFloat("Damage");
		syncLook();
	}
}

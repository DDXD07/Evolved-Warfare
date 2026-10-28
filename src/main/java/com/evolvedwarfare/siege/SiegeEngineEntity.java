package com.evolvedwarfare.siege;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.NpcSides;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.item.CommandStaffItem;
import com.evolvedwarfare.mixin.LivingEntityAccessor;
import com.evolvedwarfare.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Máquina de asedio (catapulta, balista, trebuchet o ariete). Se monta con clic derecho; W/S la empujan, A/D la
 * giran y <b>saltar (espacio)</b> dispara o golpea. Se apunta mirando: el tiro cae donde miras (si llega). La munición
 * se gasta de la que tenga dentro (clic derecho con piedras, vasijas o virotes para cargarla) o del inventario de quien
 * la maneja. Con el bastón de mando, uno de tus soldados se pone a manejarla y dispara solo a tus enemigos.
 *
 * <p>Las de una facción (asaltos, fortalezas) las maneja uno de los suyos; si te subes cuando no hay nadie, la
 * capturas.</p>
 */
public class SiegeEngineEntity extends Entity {
	private static final TrackedData<Float> DAMAGE = DataTracker.registerData(SiegeEngineEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Integer> RELOAD = DataTracker.registerData(SiegeEngineEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> HURT = DataTracker.registerData(SiegeEngineEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> STRIKE = DataTracker.registerData(SiegeEngineEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> RELOAD_TOTAL = DataTracker.registerData(SiegeEngineEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Animación del golpe del ariete (tics). */
	public static final int STRIKE_TICKS = 12;
	/** Tiempo sin recibir daño para poder desmontarla. */
	private static final int PACK_UP_DELAY = 200;

	private final SiegeKind kind;
	@Nullable
	UUID owner;
	@Nullable
	Faction faction;
	final int[] ammo = new int[SiegeAmmo.values().length];
	/** La trajo un asalto: si el asalto acaba sin que la captures, desaparece. */
	boolean raidEngine;
	/** Hacia dónde va o a qué dispara la dotación de un asalto. */
	@Nullable
	BlockPos siegeTarget;
	int stuckTicks;
	private boolean wasJumping;
	private long lastHurtTime = -10000L;
	private final Map<BlockPos, Integer> strikes = new HashMap<>();
	@Nullable
	private Vec3d lastStrikePos;

	private int lerpTicks;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private float lerpYaw;

	public SiegeEngineEntity(EntityType<? extends SiegeEngineEntity> type, World world, SiegeKind kind) {
		super(type, world);
		this.kind = kind;
		this.intersectionChecked = true;
	}

	public SiegeKind getKind() {
		return kind;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(DAMAGE, 0.0F);
		builder.add(RELOAD, 0);
		builder.add(HURT, 0);
		builder.add(STRIKE, 0);
		builder.add(RELOAD_TOTAL, Math.max(1, kind.reload));
	}

	// ------------------------------------------------------------------ estado

	public float getDamageTaken() {
		return getDataTracker().get(DAMAGE);
	}

	private void setDamageTaken(float damage) {
		getDataTracker().set(DAMAGE, Math.max(0.0F, damage));
	}

	public float getHealth() {
		return Math.max(0.0F, maxHealth() - getDamageTaken());
	}

	/** Vida máxima (la de la máquina por lo que diga la configuración). */
	public float maxHealth() {
		return kind.health * (float) EwConfig.get().siegeEngineHealth;
	}

	/** Cuánto dura la recarga entera (para la animación). */
	public int getReloadTotal() {
		return Math.max(1, getDataTracker().get(RELOAD_TOTAL));
	}

	private void startReload() {
		int total = Math.max(1, Math.round(kind.reload * (float) EwConfig.get().siegeReload));
		getDataTracker().set(RELOAD_TOTAL, total);
		setReload(total);
	}

	public int getReload() {
		return getDataTracker().get(RELOAD);
	}

	private void setReload(int ticks) {
		getDataTracker().set(RELOAD, Math.max(0, ticks));
	}

	public int getHurtTicks() {
		return getDataTracker().get(HURT);
	}

	public int getStrikeTicks() {
		return getDataTracker().get(STRIKE);
	}

	@Nullable
	public UUID getOwnerUuid() {
		return owner;
	}

	@Nullable
	public Faction getFaction() {
		return faction;
	}

	public boolean isRaidEngine() {
		return raidEngine;
	}

	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
	}

	/** La deja en manos de una facción (asaltos y fortalezas): munición sin fin y la maneja uno de los suyos. */
	public void setFaction(@Nullable Faction faction, boolean raid, @Nullable BlockPos target) {
		this.faction = faction;
		this.raidEngine = raid;
		this.siegeTarget = target;
		if (faction != null) {
			owner = null;
		}
	}

	/** La deja sin dueño (los asaltantes huyen o mueren y la abandonan). */
	public void abandon() {
		faction = null;
		raidEngine = false;
		siegeTarget = null;
		Entity rider = getFirstPassenger();
		if (rider instanceof FactionNpcEntity) {
			rider.stopRiding();
		}
	}

	public Item item() {
		return ModItems.siegeItem(kind);
	}

	// ------------------------------------------------------------------ geometría

	public Vec3d forward() {
		return Vec3d.fromPolar(0.0F, getYaw());
	}

	/** Desde dónde sale el proyectil. */
	public Vec3d muzzle() {
		return getPos().add(forward().multiply(kind.muzzleForward())).add(0.0, kind.muzzleHeight(), 0.0);
	}

	/** Los bloques que tiene justo delante (los que golpea el ariete y los que le cortan el paso). */
	public List<BlockPos> frontBlocks() {
		Vec3d fwd = forward();
		Vec3d side = new Vec3d(-fwd.z, 0.0, fwd.x);
		Vec3d center = getPos().add(fwd.multiply(getWidth() * 0.5 + 0.75));
		Set<BlockPos> out = new LinkedHashSet<>();
		for (double s : new double[] {-0.45, 0.45}) {
			for (double y : new double[] {0.5, 1.5}) {
				out.add(BlockPos.ofFloored(center.add(side.multiply(s)).add(0.0, y, 0.0)));
			}
		}
		return new ArrayList<>(out);
	}

	boolean blockedAhead() {
		World world = getWorld();
		for (BlockPos pos : frontBlocks()) {
			if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Fuerza contra los bloques (con la configuración). */
	public float power() {
		return kind.power * (float) EwConfig.get().siegeBlockPower;
	}

	boolean canBreakAhead() {
		World world = getWorld();
		for (BlockPos pos : frontBlocks()) {
			BlockState state = world.getBlockState(pos);
			if (!state.getCollisionShape(world, pos).isEmpty()) {
				float hardness = state.getHardness(world, pos);
				if (hardness >= 0.0F && hardness <= power() && world.getBlockEntity(pos) == null) {
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ ciclo

	@Override
	public void tick() {
		if (getHurtTicks() > 0) {
			getDataTracker().set(HURT, getHurtTicks() - 1);
		}
		super.tick();
		if (getWorld().isClient) {
			tickLerp();
			return;
		}
		if (getReload() > 0) {
			setReload(getReload() - 1);
		}
		if (getStrikeTicks() > 0) {
			getDataTracker().set(STRIKE, getStrikeTicks() - 1);
		}
		Entity rider = getFirstPassenger();
		double forward = 0.0;
		float turn = 0.0F;
		if (rider instanceof PlayerEntity player) {
			forward = player.forwardSpeed;
			turn = player.sidewaysSpeed;
			boolean jumping = ((LivingEntityAccessor) player).evolvedwarfare$isJumping();
			if (jumping && !wasJumping) {
				trigger(player);
			}
			wasJumping = jumping;
			if (player instanceof ServerPlayerEntity serverPlayer && age % 5 == 0) {
				hud(serverPlayer);
			}
		} else {
			wasJumping = false;
			forward = SiegeCrewAi.tick(this, rider instanceof LivingEntity living ? living : null);
		}
		drive(forward, turn);
		if (lastStrikePos != null && getPos().squaredDistanceTo(lastStrikePos) > 0.25) {
			clearStrikes();
		}
	}

	private void drive(double forward, float turn) {
		if (turn != 0.0F) {
			setYaw(getYaw() - turn * kind.turnSpeed);
		}
		Vec3d velocity = getVelocity();
		double vy = velocity.y;
		if (isTouchingWater()) {
			vy = vy * 0.5 + 0.01;
		} else if (!hasNoGravity()) {
			vy -= 0.08;
		}
		double speed = forward * kind.moveSpeed * (forward < 0.0 ? 0.6 : 1.0);
		if (!isOnGround() && !isTouchingWater()) {
			speed *= 0.3;
		}
		Vec3d dir = forward();
		setVelocity(dir.x * speed, vy, dir.z * speed);
		move(MovementType.SELF, getVelocity());
		if (isOnGround() && getVelocity().y < 0.0) {
			setVelocity(getVelocity().x, 0.0, getVelocity().z);
		}
	}

	@Override
	public float getStepHeight() {
		return 1.0F;
	}

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
		lerpX = x;
		lerpY = y;
		lerpZ = z;
		lerpYaw = yaw;
		lerpTicks = 5;
	}

	private void tickLerp() {
		if (lerpTicks > 0) {
			lerpPosAndRotation(lerpTicks, lerpX, lerpY, lerpZ, lerpYaw, getPitch());
			lerpTicks--;
		}
	}

	// ------------------------------------------------------------------ disparar y golpear

	/** El jugador que la maneja ha pulsado saltar. */
	private void trigger(PlayerEntity player) {
		if (getReload() > 0) {
			return;
		}
		if (kind == SiegeKind.RAM) {
			strike(player);
			return;
		}
		SiegeAmmo chosen = chooseAmmo(player);
		if (chosen == null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.siege.no_ammo").formatted(Formatting.RED), true);
			getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 1.0F, 1.0F);
			return;
		}
		SiegeBallistics.Aim aim = SiegeBallistics.aimFor(kind, getWorld(), muzzle(), getYaw(), player);
		if (takeAmmo(player, chosen)) {
			fire(player, aim, chosen, 0.25F);
		}
	}

	/** La munición que toca: la de la mano si vale, si no la primera que haya (dentro de la máquina o en el inventario). */
	@Nullable
	SiegeAmmo chooseAmmo(@Nullable PlayerEntity player) {
		List<SiegeAmmo> order = new ArrayList<>();
		if (player != null) {
			SiegeAmmo held = SiegeAmmo.of(player.getMainHandStack());
			if (held != null && held.usableBy(kind)) {
				order.add(held);
			}
		}
		for (SiegeAmmo candidate : SiegeAmmo.values()) {
			if (candidate.usableBy(kind) && !order.contains(candidate)) {
				order.add(candidate);
			}
		}
		if (order.isEmpty()) {
			return null;
		}
		for (SiegeAmmo candidate : order) {
			int cost = candidate.cost(kind);
			if (ammo[candidate.ordinal()] >= cost || (player != null && count(player, candidate) >= cost)) {
				return candidate;
			}
		}
		boolean free = faction != null || (player != null && player.getAbilities().creativeMode);
		return free ? order.get(0) : null;
	}

	private static int count(PlayerEntity player, SiegeAmmo kind) {
		int total = 0;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (kind.matches(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private boolean takeAmmo(@Nullable PlayerEntity player, SiegeAmmo kind) {
		if (faction != null || (player != null && player.getAbilities().creativeMode)) {
			return true;
		}
		int cost = kind.cost(this.kind);
		if (ammo[kind.ordinal()] >= cost) {
			ammo[kind.ordinal()] -= cost;
			return true;
		}
		if (player == null || count(player, kind) < cost) {
			return false;
		}
		int left = cost;
		for (int i = 0; i < player.getInventory().size() && left > 0; i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (kind.matches(stack)) {
				int take = Math.min(left, stack.getCount());
				stack.decrement(take);
				left -= take;
			}
		}
		return true;
	}

	/** Munición para la dotación (NPC): la de dentro, o sin fin si es de una facción. */
	@Nullable
	SiegeAmmo crewAmmo() {
		for (SiegeAmmo candidate : SiegeAmmo.values()) {
			if (candidate.usableBy(kind) && (faction != null || ammo[candidate.ordinal()] >= candidate.cost(kind))) {
				return candidate;
			}
		}
		return null;
	}

	boolean takeCrewAmmo(SiegeAmmo kind) {
		return takeAmmo(null, kind);
	}

	/** Lanza un proyectil con ese tiro (con algo de desvío). */
	void fire(LivingEntity shooter, SiegeBallistics.Aim aim, SiegeAmmo used, float spread) {
		World world = getWorld();
		float yaw = aim.yaw() + (float) random.nextTriangular(0.0, spread);
		float elevation = aim.elevation() + (float) random.nextTriangular(0.0, spread * 0.6);
		SiegeShotEntity shot = new SiegeShotEntity(world, used.shotFor(kind), aim.muzzle(), shooter);
		shot.setVelocity(SiegeBallistics.velocity(yaw, elevation, kind.shotSpeed));
		world.spawnEntity(shot);
		startReload();
		if (kind == SiegeKind.BALLISTA) {
			world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 1.6F, 0.6F);
		} else {
			world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.6F, 0.5F);
			world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.PLAYERS, 1.0F, 0.6F);
		}
	}

	/** Golpe del ariete: daña a quien esté delante y va rompiendo los bloques de delante (más golpes cuanto más duros). */
	void strike(LivingEntity driver) {
		World world = getWorld();
		startReload();
		getDataTracker().set(STRIKE, STRIKE_TICKS);
		world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.BLOCKS, 1.5F, 0.7F);
		Vec3d fwd = forward();
		Vec3d head = getPos().add(fwd.multiply(getWidth() * 0.5 + 0.8)).add(0.0, 1.0, 0.0);
		Box box = Box.of(head, 1.6, 2.0, 1.6);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
				e -> e != driver && e.isAlive() && !e.isConnectedThroughVehicle(this) && !NpcSides.allies(driver, e))) {
			DamageSource source = driver instanceof PlayerEntity player ? getDamageSources().playerAttack(player)
					: getDamageSources().mobAttack(driver);
			target.damage(source, 14.0F);
			target.takeKnockback(1.2, -fwd.x, -fwd.z);
		}
		if (!SiegeRules.blockDamage(world) || !(world instanceof ServerWorld serverWorld)) {
			return;
		}
		lastStrikePos = getPos();
		List<BlockPos> front = frontBlocks();
		for (int i = 0; i < front.size(); i++) {
			BlockPos pos = front.get(i);
			BlockState state = world.getBlockState(pos);
			if (state.isAir() || state.getCollisionShape(world, pos).isEmpty()) {
				continue;
			}
			float hardness = state.getHardness(world, pos);
			if (hardness < 0.0F || hardness > power() || world.getBlockEntity(pos) != null) {
				continue;
			}
			int needed = Math.max(1, MathHelper.ceil(hardness * 1.6F));
			int done = strikes.merge(pos, 1, Integer::sum);
			serverWorld.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
					pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
			if (done >= needed) {
				world.setBlockBreakingInfo(breakerId(i), pos, -1);
				world.breakBlock(pos, true, driver);
				strikes.remove(pos);
			} else {
				world.setBlockBreakingInfo(breakerId(i), pos, Math.min(9, done * 10 / needed));
			}
		}
	}

	private int breakerId(int index) {
		return -(getId() * 4 + index) - 1;
	}

	private void clearStrikes() {
		for (int i = 0; i < 4; i++) {
			for (BlockPos pos : strikes.keySet()) {
				getWorld().setBlockBreakingInfo(breakerId(i), pos, -1);
			}
		}
		strikes.clear();
		lastStrikePos = null;
	}

	// ------------------------------------------------------------------ lo que ve quien la maneja

	private void hud(ServerPlayerEntity player) {
		MutableText status;
		int reload = getReload();
		if (reload > 0) {
			status = Text.translatable(kind == SiegeKind.RAM ? "hud.evolvedwarfare.siege.winding" : "hud.evolvedwarfare.siege.reloading",
					String.format(Locale.ROOT, "%.1f", reload / 20.0F)).formatted(Formatting.GOLD);
		} else if (kind == SiegeKind.RAM) {
			status = Text.translatable("hud.evolvedwarfare.siege.strike_ready").formatted(Formatting.GREEN);
		} else if (chooseAmmo(player) == null) {
			status = Text.translatable("hud.evolvedwarfare.siege.no_ammo").formatted(Formatting.RED);
		} else {
			status = Text.translatable("hud.evolvedwarfare.siege.ready").formatted(Formatting.GREEN);
		}
		MutableText line = Text.translatable("entity.evolvedwarfare." + kind.id()).formatted(Formatting.BOLD)
				.append(Text.literal("  ")).append(status);
		if (kind.fires()) {
			for (SiegeAmmo candidate : SiegeAmmo.values()) {
				if (candidate.usableBy(kind)) {
					int total = ammo[candidate.ordinal()] + count(player, candidate);
					line.append(Text.literal("  ")).append(Text.translatable("hud.evolvedwarfare.siege.ammo",
							Text.translatable(candidate.translationKey()), total).formatted(total > 0 ? Formatting.WHITE : Formatting.DARK_GRAY));
				}
			}
		}
		line.append(Text.literal("  ")).append(Text.translatable("hud.evolvedwarfare.siege.health",
				MathHelper.ceil(getHealth()), MathHelper.ceil(maxHealth())).formatted(Formatting.RED));
		player.sendMessage(line, true);
	}

	// ------------------------------------------------------------------ interacción

	public boolean isOwnedBy(PlayerEntity player) {
		return owner == null ? faction == null : owner.equals(player.getUuid());
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		World world = getWorld();
		ItemStack stack = player.getStackInHand(hand);
		if (player.isSneaking()) {
			if (hand == Hand.MAIN_HAND && stack.isEmpty()) {
				if (!world.isClient) {
					packUp(player);
				}
				return ActionResult.success(world.isClient);
			}
			return ActionResult.PASS;
		}
		Entity rider = getFirstPassenger();
		if (rider instanceof FactionNpcEntity && faction != null) {
			if (!world.isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.siege.enemy_crew"), true);
			}
			return ActionResult.CONSUME;
		}
		if (owner != null && !owner.equals(player.getUuid())) {
			if (!world.isClient) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.siege.not_yours"), true);
			}
			return ActionResult.CONSUME;
		}
		if (stack.getItem() instanceof CommandStaffItem) {
			if (!world.isClient) {
				claim(player);
				SiegeCrewAi.assign(this, player);
			}
			return ActionResult.success(world.isClient);
		}
		SiegeAmmo loaded = SiegeAmmo.of(stack);
		if (loaded != null && loaded.usableBy(kind)) {
			if (!world.isClient) {
				claim(player);
				load(player, stack, loaded);
			}
			return ActionResult.success(world.isClient);
		}
		if (stack.isIn(ItemTags.PLANKS) && getDamageTaken() > 0.0F) {
			if (!world.isClient) {
				repair(player, stack);
			}
			return ActionResult.success(world.isClient);
		}
		if (hand == Hand.MAIN_HAND && !hasPassengers()) {
			if (!world.isClient) {
				claim(player);
				player.startRiding(this);
			}
			return ActionResult.success(world.isClient);
		}
		return ActionResult.PASS;
	}

	/** Capturar una de una facción, o quedarse con una sin dueño. */
	private void claim(PlayerEntity player) {
		if (faction != null) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.siege.captured", Text.translatable("entity.evolvedwarfare." + kind.id()),
					faction.displayName()).formatted(Formatting.GOLD), false);
			faction = null;
			raidEngine = false;
			siegeTarget = null;
			owner = player.getUuid();
		} else if (owner == null) {
			owner = player.getUuid();
		}
	}

	private void load(PlayerEntity player, ItemStack stack, SiegeAmmo kind) {
		int room = SiegeAmmo.CAPACITY - ammo[kind.ordinal()];
		if (room <= 0) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.siege.full"), true);
			return;
		}
		int amount = Math.min(room, stack.getCount());
		ammo[kind.ordinal()] += amount;
		if (!player.getAbilities().creativeMode) {
			stack.decrement(amount);
		}
		player.sendMessage(Text.translatable("message.evolvedwarfare.siege.loaded", Text.translatable(kind.translationKey()),
				ammo[kind.ordinal()]), true);
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(), SoundCategory.PLAYERS, 1.0F, 0.8F);
	}

	private void repair(PlayerEntity player, ItemStack stack) {
		setDamageTaken(getDamageTaken() - maxHealth() * 0.1F);
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.PLAYERS, 1.0F, 0.9F);
		player.sendMessage(Text.translatable("message.evolvedwarfare.siege.repaired", MathHelper.ceil(getHealth()),
				MathHelper.ceil(maxHealth())), true);
	}

	/** Agachado y con la mano vacía: se desmonta y vuelve al inventario (con su munición). */
	private void packUp(PlayerEntity player) {
		if (!isOwnedBy(player) && !player.getAbilities().creativeMode) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.siege.not_yours"), true);
			return;
		}
		if (getWorld().getTime() - lastHurtTime < PACK_UP_DELAY) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.siege.under_attack"), true);
			return;
		}
		removeAllPassengers();
		player.getInventory().offerOrDrop(new ItemStack(item()));
		returnAmmo(player);
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0F, 0.8F);
		discard();
	}

	private void returnAmmo(@Nullable PlayerEntity player) {
		for (SiegeAmmo kind : SiegeAmmo.values()) {
			int left = ammo[kind.ordinal()];
			while (left > 0) {
				int n = Math.min(left, 64);
				ItemStack stack = new ItemStack(kind.item(), n);
				if (player != null) {
					player.getInventory().offerOrDrop(stack);
				} else {
					dropStack(stack);
				}
				left -= n;
			}
			ammo[kind.ordinal()] = 0;
		}
	}

	// ------------------------------------------------------------------ daño

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
		if (attacker != null && attacker == getFirstPassenger()) {
			return false;
		}
		if (attacker instanceof PlayerEntity player && player.getAbilities().creativeMode && source.getSource() == player) {
			discard();
			return true;
		}
		float multiplier = 1.0F;
		if (source.isIn(DamageTypeTags.IS_FIRE)) {
			multiplier = kind == SiegeKind.BALLISTA ? 2.0F : 3.0F;
		} else if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
			multiplier = 1.5F;
		} else if (attacker instanceof LivingEntity living && source.getSource() == living
				&& living.getMainHandStack().getItem() instanceof AxeItem) {
			multiplier = 2.0F;
		}
		setDamageTaken(getDamageTaken() + amount * multiplier);
		getDataTracker().set(HURT, 10);
		lastHurtTime = world.getTime();
		emitGameEvent(GameEvent.ENTITY_DAMAGE, attacker);
		if (getDamageTaken() >= maxHealth()) {
			breakApart();
		} else {
			world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 1.0F, 0.8F);
		}
		return true;
	}

	/** Destrozada: suelta parte de sus materiales y la munición que llevaba. */
	private void breakApart() {
		World world = getWorld();
		Entity rider = getFirstPassenger();
		if (rider instanceof PlayerEntity player) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.siege.destroyed", Text.translatable("entity.evolvedwarfare." + kind.id()))
					.formatted(Formatting.RED), true);
		}
		removeAllPassengers();
		switch (kind) {
			case CATAPULT -> {
				dropStack(new ItemStack(Items.OAK_PLANKS, 5));
				dropStack(new ItemStack(Items.STRING, 2));
				dropStack(new ItemStack(Items.IRON_INGOT, 1));
			}
			case BALLISTA -> {
				dropStack(new ItemStack(Items.OAK_PLANKS, 4));
				dropStack(new ItemStack(Items.STRING, 2));
				dropStack(new ItemStack(Items.IRON_INGOT, 2));
			}
			case TREBUCHET -> {
				dropStack(new ItemStack(Items.OAK_PLANKS, 10));
				dropStack(new ItemStack(Items.STRING, 4));
				dropStack(new ItemStack(Items.IRON_INGOT, 3));
				dropStack(new ItemStack(Items.COBBLESTONE, 6));
			}
			case RAM -> {
				dropStack(new ItemStack(Items.OAK_PLANKS, 6));
				dropStack(new ItemStack(Items.IRON_INGOT, 2));
			}
		}
		returnAmmo(null);
		if (world instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.OAK_PLANKS.getDefaultState()),
					getX(), getY() + 0.8, getZ(), 60, getWidth() * 0.4, 0.6, getWidth() * 0.4, 0.15);
		}
		world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 1.5F, 0.8F);
		discard();
	}

	// ------------------------------------------------------------------ como vehículo

	@Override
	public boolean isCollidable() {
		return true;
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
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().isEmpty() && passenger instanceof LivingEntity;
	}

	/** Dónde se sienta quien la maneja (detrás). */
	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
		double back = switch (kind) {
			case CATAPULT -> -1.6;
			case BALLISTA -> -1.25;
			case TREBUCHET -> -2.2;
			case RAM -> -0.4;
		};
		return new Vec3d(0.0, 0.3, back).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(item());
	}

	// ------------------------------------------------------------------ guardar

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
		if (faction != null) {
			nbt.putString("Faction", faction.id());
		}
		nbt.putIntArray("Ammo", ammo.clone());
		nbt.putFloat("Damage", getDamageTaken());
		nbt.putInt("Reload", getReload());
		nbt.putBoolean("RaidEngine", raidEngine);
		if (siegeTarget != null) {
			nbt.putIntArray("SiegeTarget", new int[] {siegeTarget.getX(), siegeTarget.getY(), siegeTarget.getZ()});
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		faction = nbt.contains("Faction") ? Faction.byId(nbt.getString("Faction")) : null;
		int[] stored = nbt.getIntArray("Ammo");
		for (int i = 0; i < ammo.length; i++) {
			ammo[i] = i < stored.length ? MathHelper.clamp(stored[i], 0, SiegeAmmo.CAPACITY) : 0;
		}
		setDamageTaken(nbt.getFloat("Damage"));
		setReload(nbt.getInt("Reload"));
		raidEngine = nbt.getBoolean("RaidEngine");
		int[] target = nbt.getIntArray("SiegeTarget");
		siegeTarget = target.length == 3 ? new BlockPos(target[0], target[1], target[2]) : null;
	}
}

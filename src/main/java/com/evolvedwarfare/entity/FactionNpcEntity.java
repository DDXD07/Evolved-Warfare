package com.evolvedwarfare.entity;

import com.evolvedwarfare.entity.ai.EscortFollowGoal;
import com.evolvedwarfare.entity.ai.EscortTargetGoal;
import com.evolvedwarfare.entity.ai.HealAlliesGoal;
import com.evolvedwarfare.entity.ai.MarchGoal;
import com.evolvedwarfare.entity.ai.NpcMeleeAttackGoal;
import com.evolvedwarfare.entity.ai.NpcRangedAttackGoal;
import com.evolvedwarfare.faction.Faction;
import com.evolvedwarfare.faction.Reputation;
import com.evolvedwarfare.item.WhiteFlagItem;
import com.evolvedwarfare.world.FactionRaids;
import com.evolvedwarfare.world.SettlementLevels;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.GoToWalkTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * Guerrero de una facción (bandidos, vikingos, piratas). Ataca a los jugadores con los que su facción está enemistada
 * (ver {@link Reputation}), a sus NPCs y a las demás facciones. Puede ser:
 * <ul>
 *   <li><b>Guarnición</b> de una estructura: no desaparece y defiende su puesto.</li>
 *   <li><b>Patrulla</b>: camina hacia un destino; desaparece si nadie anda cerca, como cualquier monstruo.</li>
 *   <li><b>Asaltante</b>: marcha hacia el centro de una aldea, ciudad o pueblo y ataca también a los aldeanos.</li>
 *   <li><b>Escolta</b>: guerreros que una facción aliada presta a un jugador; le siguen y le defienden un rato.</li>
 * </ul>
 */
public abstract class FactionNpcEntity extends HostileEntity implements CombatNpc {
	private static final double GARRISON_RADIUS = 24.0;
	private static final double ESCORT_RADIUS = 20.0;
	private static final net.minecraft.util.Identifier LEVEL_HEALTH = com.evolvedwarfare.EvolvedWarfare.id("settlement_level_health");
	private static final net.minecraft.util.Identifier LEVEL_DAMAGE = com.evolvedwarfare.EvolvedWarfare.id("settlement_level_damage");

	private static final TrackedData<Integer> ROLE = DataTracker.registerData(FactionNpcEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> VARIANT = DataTracker.registerData(FactionNpcEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private boolean roleFromNbt;
	private boolean garrison;
	@Nullable
	private BlockPos home;
	@Nullable
	private BlockPos marchTarget;
	private long raidUntil;
	@Nullable
	private UUID escortOf;
	private long escortUntil;
	/** Nivel del asentamiento que defiende (0: aún sin mirar; los que no son guarnición se quedan en 1). */
	private int settlementLevel;

	protected FactionNpcEntity(EntityType<? extends FactionNpcEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 6;
	}

	public static DefaultAttributeContainer.Builder createFactionAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 22.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	// ------------------------------------------------------------------ lo que cambia en cada facción

	public abstract Faction getFaction();

	/** Clase al azar para los que aparecen sin una fija. */
	protected abstract NpcRole randomRole(Random random);

	/** Equipo inicial según la clase. */
	protected abstract void equipFor(NpcRole role, Random random, boolean elite);

	/** Cuántos aspectos distintos tiene (sin contar el del capitán). */
	public abstract int variantCount();

	/** Prefijo de las claves de nombre ("viking" → entity.evolvedwarfare.viking.warrior). */
	protected abstract String nameKey();

	/** Vida y daño extra de la clase en esta facción (sobre la base de {@link NpcRole}). */
	protected double healthFor(NpcRole role) {
		return role.health();
	}

	protected double speedFor(NpcRole role) {
		return role.speed();
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE, NpcRole.WARRIOR.ordinal());
		builder.add(VARIANT, 0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(2, new NpcRangedAttackGoal(this));
		goalSelector.add(2, new NpcMeleeAttackGoal(this, 1.15));
		goalSelector.add(2, new HealAlliesGoal(this));
		goalSelector.add(3, new EscortFollowGoal(this));
		goalSelector.add(3, new MarchGoal(this));
		goalSelector.add(4, new GoToWalkTargetGoal(this, 1.0));
		goalSelector.add(5, new WanderAroundFarGoal(this, 0.8, 0.002F));
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
		goalSelector.add(7, new LookAroundGoal(this));

		targetSelector.add(1, new RevengeGoal(this, getClass()).setGroupRevenge());
		targetSelector.add(2, new EscortTargetGoal(this));
		targetSelector.add(3, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::shouldAttack));
		targetSelector.add(4, new ActiveTargetGoal<>(this, AbstractNpcEntity.class, 10, true, false, this::shouldAttack));
		targetSelector.add(4, new ActiveTargetGoal<>(this, FactionNpcEntity.class, 10, true, false, this::shouldAttack));
		targetSelector.add(4, new ActiveTargetGoal<>(this, IronGolemEntity.class, 10, true, false, e -> isRaiding()));
		targetSelector.add(5, new ActiveTargetGoal<>(this, MerchantEntity.class, 10, true, false, e -> isRaiding()));
	}

	// ------------------------------------------------------------------ a quién ataca

	/** ¿Ataca a esto por su cuenta? Según la reputación de su dueño o jugador, o si es de otra facción. */
	public boolean shouldAttack(LivingEntity target) {
		if (target == this || !target.isAlive()) {
			return false;
		}
		Faction faction = getFaction();
		if (target instanceof PlayerEntity player) {
			if (player.isCreative() || player.isSpectator() || isEscortOf(player.getUuid())) {
				return false;
			}
			if (!Reputation.hostile(getWorld(), player.getUuid(), faction)) {
				return false;
			}
			// bandera blanca: solo si no les has atacado hace poco
			return !(faction.respectsWhiteFlag() && WhiteFlagItem.isRaised(player)
					&& !Reputation.provoked(player.getServer(), player.getUuid(), faction));
		}
		if (target instanceof FactionNpcEntity other) {
			if (!faction.enemies(other.getFaction())) {
				return false;
			}
			// los escoltas de un jugador no se pelean con facciones aliadas de ese jugador
			return !(escortOf != null && other.escortOf != null && escortOf.equals(other.escortOf));
		}
		if (target instanceof AbstractNpcEntity npc) {
			UUID owner = npc.getOwnerUuid();
			return owner != null && !isEscortOf(owner) && Reputation.hostile(getWorld(), owner, faction);
		}
		return true;
	}

	// ------------------------------------------------------------------ clase y aspecto

	@Override
	public NpcRole getRole() {
		return NpcRole.byOrdinal(getDataTracker().get(ROLE));
	}

	/** Fija la clase antes de initialize (patrullas, asaltos y estructuras con una clase concreta). */
	public void presetRole(NpcRole role) {
		getDataTracker().set(ROLE, role.ordinal());
		roleFromNbt = true;
	}

	public void setRole(NpcRole role) {
		getDataTracker().set(ROLE, role.ordinal());
		setBase(EntityAttributes.GENERIC_MAX_HEALTH, healthFor(role));
		setBase(EntityAttributes.GENERIC_ATTACK_DAMAGE, role.attackDamage());
		setBase(EntityAttributes.GENERIC_MOVEMENT_SPEED, speedFor(role));
		this.experiencePoints = role == NpcRole.CAPTAIN ? 30 : 6;
	}

	private void setBase(RegistryEntry<EntityAttribute> attribute, double value) {
		EntityAttributeInstance instance = getAttributeInstance(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	public int getVariant() {
		return getDataTracker().get(VARIANT);
	}

	public int getSettlementLevel() {
		return Math.max(1, settlementLevel);
	}

	/** Cada nivel del asentamiento por encima del 1: +10 % de vida y de daño (también el de sus flechas). */
	public void applySettlementLevel(int level) {
		boolean first = settlementLevel == 0;
		settlementLevel = Math.max(1, Math.min(SettlementLevels.MAX_LEVEL, level));
		double bonus = SettlementLevels.multiplier(settlementLevel) - 1.0;
		EntityAttributeInstance health = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (health != null) {
			health.overwritePersistentModifier(new EntityAttributeModifier(LEVEL_HEALTH, bonus,
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		EntityAttributeInstance damage = getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (damage != null) {
			damage.overwritePersistentModifier(new EntityAttributeModifier(LEVEL_DAMAGE, bonus,
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (first) {
			setHealth(getMaxHealth());
		}
	}

	@Override
	public void prepareShot(PersistentProjectileEntity arrow, boolean crossbow) {
		arrow.setDamage(arrow.getDamage() * SettlementLevels.multiplier(getSettlementLevel()));
	}

	@Override
	public int getCombatLevel() {
		return getRole() == NpcRole.CAPTAIN ? 3 : 1;
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			@Nullable EntityData entityData) {
		EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
		Random random = world.getRandom();
		setLeftHanded(false);
		setRole(roleFromNbt ? getRole() : randomRole(random));
		getDataTracker().set(VARIANT, random.nextInt(variantCount()));
		boolean elite = getRole() == NpcRole.CAPTAIN || difficulty.getGlobalDifficulty() == Difficulty.HARD;
		equipFor(getRole(), random, elite);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			setEquipmentDropChance(slot, 0.06F);
		}
		if (spawnReason == SpawnReason.STRUCTURE) {
			garrison = true;
			setPersistent();
			home = getBlockPos();
			applyGarrisonLeash();
		}
		setHealth(getMaxHealth());
		return data;
	}

	private void applyGarrisonLeash() {
		if (home != null) {
			NpcRole.Kind kind = getRole().kind();
			setPositionTarget(home, kind == NpcRole.Kind.BOW || kind == NpcRole.Kind.CROSSBOW ? 3 : 10);
		}
	}

	// ------------------------------------------------------------------ patrullas y asaltos

	@Nullable
	public BlockPos getMarchTarget() {
		return marchTarget;
	}

	public void startPatrol(BlockPos target) {
		marchTarget = target.toImmutable();
	}

	public void startRaid(BlockPos center, long until) {
		marchTarget = center.toImmutable();
		raidUntil = until;
	}

	public boolean isRaiding() {
		return raidUntil > getWorld().getTime();
	}

	public void endRaid() {
		raidUntil = 0L;
		marchTarget = null;
	}

	/** Ha llegado a su destino: los asaltantes se quedan rondando el centro; las patrullas siguen su camino. */
	public void arrived() {
		if (isRaiding() && marchTarget != null) {
			setPositionTarget(marchTarget, 20);
		}
		marchTarget = null;
	}

	// ------------------------------------------------------------------ escolta (ayuda de un aliado)

	/** Le pone a escoltar a un jugador durante un tiempo. */
	public void startEscort(UUID player, long until) {
		escortOf = player;
		escortUntil = until;
		setPersistent();
	}

	@Nullable
	public PlayerEntity getEscortedPlayer() {
		if (escortOf == null || escortUntil <= getWorld().getTime()) {
			return null;
		}
		PlayerEntity player = getWorld().getPlayerByUuid(escortOf);
		return player != null && player.isAlive() && !player.isSpectator() ? player : null;
	}

	public boolean isEscortOf(@Nullable UUID player) {
		return player != null && player.equals(escortOf) && escortUntil > getWorld().getTime();
	}

	public boolean isEscort() {
		return escortOf != null;
	}

	/** Se acaba la escolta: se despide y se va. */
	private void leave() {
		if (getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.CLOUD, getX(), getBodyY(0.5), getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			PlayerEntity player = escortOf != null ? world.getPlayerByUuid(escortOf) : null;
			if (player != null && player.squaredDistanceTo(this) < 48 * 48 && getRole() == NpcRole.CAPTAIN) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.diplomacy.aid_leaves", getFaction().displayName()), true);
			}
		}
		discard();
	}

	// ------------------------------------------------------------------ ciclo

	@Override
	public void onDeath(DamageSource source) {
		if (!getWorld().isClient) {
			if (raidUntil != 0L) {
				FactionRaids.onRaiderDeath(this);
			}
			Reputation.onMemberKilled(this, source.getAttacker());
		}
		super.onDeath(source);
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			return;
		}
		if (raidUntil != 0L && !isRaiding()) {
			endRaid();
		}
		if (age % 20 == 0) {
			if (settlementLevel == 0 && getWorld() instanceof ServerWorld serverWorld) {
				// la guarnición mira de qué nivel es su asentamiento (una vez)
				applySettlementLevel(garrison && home != null ? SettlementLevels.levelAt(serverWorld, home) : 1);
			}
			if (escortOf != null && (escortUntil <= getWorld().getTime() || getEscortedPlayer() == null && age % 200 == 0)) {
				leave();
				return;
			}
			// si ya no toca pelear con su objetivo (tregua, bandera blanca...), lo deja
			LivingEntity target = getTarget();
			if (target != null && target != getAttacker() && !shouldAttack(target) && !isEscortTarget(target)) {
				setTarget(null);
			}
			tickAbilities();
		}
	}

	/** Habilidades propias de cada facción (cada segundo). */
	protected void tickAbilities() {
	}

	private boolean isEscortTarget(LivingEntity target) {
		PlayerEntity player = getEscortedPlayer();
		return player != null && (player.getAttacker() == target || player.getAttacking() == target);
	}

	// ------------------------------------------------------------------ CombatNpc

	@Override
	@Nullable
	public Vec3d getLeashCenter() {
		PlayerEntity escorted = getEscortedPlayer();
		if (escorted != null) {
			return escorted.getPos();
		}
		return garrison && home != null ? Vec3d.ofBottomCenter(home) : null;
	}

	@Override
	public double getLeashRadius() {
		return escortOf != null ? ESCORT_RADIUS : GARRISON_RADIUS;
	}

	// ------------------------------------------------------------------ comportamiento de monstruo

	/** No buscan la oscuridad como los monstruos normales. */
	@Override
	public float getPathfindingFavor(BlockPos pos, WorldView world) {
		return 0.0F;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return !garrison && !isRaiding() && escortOf == null && super.canImmediatelyDespawn(distanceSquared);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (!getWorld().isClient && NpcCombat.tryShieldBlock(this, source)) {
			return false;
		}
		boolean damaged = super.damage(source, amount);
		if (damaged && !getWorld().isClient) {
			Entity attacker = source.getAttacker();
			if (attacker != null && !(attacker instanceof FactionNpcEntity)) {
				Reputation.onMemberAttacked(this, attacker);
			}
		}
		return damaged;
	}

	@Override
	protected Text getDefaultName() {
		return Text.translatable("entity.evolvedwarfare." + nameKey() + "." + getRole().id());
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_PILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_PILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_PILLAGER_DEATH;
	}

	// ------------------------------------------------------------------ guardado

	private static void putPos(NbtCompound nbt, String prefix, @Nullable BlockPos pos) {
		if (pos != null) {
			nbt.putInt(prefix + "X", pos.getX());
			nbt.putInt(prefix + "Y", pos.getY());
			nbt.putInt(prefix + "Z", pos.getZ());
		}
	}

	@Nullable
	private static BlockPos readPos(NbtCompound nbt, String prefix) {
		return nbt.contains(prefix + "X") ? new BlockPos(nbt.getInt(prefix + "X"), nbt.getInt(prefix + "Y"), nbt.getInt(prefix + "Z")) : null;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("Role", getRole().id());
		nbt.putInt("Variant", getVariant());
		nbt.putBoolean("Garrison", garrison);
		putPos(nbt, "Home", home);
		putPos(nbt, "March", marchTarget);
		nbt.putLong("RaidUntil", raidUntil);
		nbt.putInt("SettlementLevel", settlementLevel);
		if (escortOf != null) {
			nbt.putUuid("EscortOf", escortOf);
			nbt.putLong("EscortUntil", escortUntil);
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Role")) {
			getDataTracker().set(ROLE, NpcRole.byId(nbt.getString("Role"), NpcRole.WARRIOR).ordinal());
			roleFromNbt = true;
		}
		getDataTracker().set(VARIANT, Math.floorMod(nbt.getInt("Variant"), variantCount()));
		garrison = nbt.getBoolean("Garrison");
		home = readPos(nbt, "Home");
		marchTarget = readPos(nbt, "March");
		raidUntil = nbt.getLong("RaidUntil");
		settlementLevel = nbt.getInt("SettlementLevel");
		escortOf = nbt.containsUuid("EscortOf") ? nbt.getUuid("EscortOf") : null;
		escortUntil = nbt.getLong("EscortUntil");
		if (garrison) {
			applyGarrisonLeash();
		}
	}
}

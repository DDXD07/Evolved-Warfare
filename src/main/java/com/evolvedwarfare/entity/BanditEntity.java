package com.evolvedwarfare.entity;

import com.evolvedwarfare.entity.ai.HealAlliesGoal;
import com.evolvedwarfare.entity.ai.MarchGoal;
import com.evolvedwarfare.entity.ai.NpcMeleeAttackGoal;
import com.evolvedwarfare.entity.ai.NpcRangedAttackGoal;
import com.evolvedwarfare.world.BanditRaids;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
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
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
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
 * Bandido: enemigo de todos los jugadores, de sus NPCs y de los gólems de hierro. Viene en varias clases
 * (guerrero, bárbaro, arquero, ballestero, médico y capitán). Puede ser:
 * <ul>
 *   <li><b>Guarnición</b> de un fuerte o campamento: no desaparece y defiende su puesto.</li>
 *   <li><b>Patrulla</b>: camina hacia donde estaba un jugador; desaparece si nadie anda cerca, como cualquier monstruo.</li>
 *   <li><b>Asaltante</b>: marcha hacia el centro de una aldea o ciudad y ataca también a los aldeanos.</li>
 * </ul>
 */
public class BanditEntity extends HostileEntity implements CombatNpc {
	public static final int VARIANTS = 4;
	private static final double GARRISON_RADIUS = 24.0;

	private static final TrackedData<Integer> ROLE = DataTracker.registerData(BanditEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> VARIANT = DataTracker.registerData(BanditEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private boolean roleFromNbt;
	private boolean garrison;
	@Nullable
	private BlockPos home;
	@Nullable
	private BlockPos marchTarget;
	private long raidUntil;

	public BanditEntity(EntityType<? extends BanditEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 6;
	}

	public static DefaultAttributeContainer.Builder createBanditAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 22.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
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
		goalSelector.add(3, new MarchGoal(this));
		goalSelector.add(4, new GoToWalkTargetGoal(this, 1.0));
		goalSelector.add(5, new WanderAroundFarGoal(this, 0.8, 0.002F));
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
		goalSelector.add(7, new LookAroundGoal(this));

		targetSelector.add(1, new RevengeGoal(this, BanditEntity.class).setGroupRevenge());
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
		targetSelector.add(3, new ActiveTargetGoal<>(this, AbstractNpcEntity.class, 10, true, false,
				e -> ((AbstractNpcEntity) e).getOwnerUuid() != null));
		targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
		targetSelector.add(4, new ActiveTargetGoal<>(this, MerchantEntity.class, 10, true, false, e -> isRaiding()));
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
		setBase(EntityAttributes.GENERIC_MAX_HEALTH, role.health());
		setBase(EntityAttributes.GENERIC_ATTACK_DAMAGE, role.attackDamage());
		setBase(EntityAttributes.GENERIC_MOVEMENT_SPEED, role.speed());
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

	@Override
	public int getCombatLevel() {
		return getRole() == NpcRole.CAPTAIN ? 3 : 1;
	}

	/** Reparto al azar: guerreros 30 %, arqueros 25 %, bárbaros 15 %, ballesteros 20 %, médicos 10 %. */
	public static NpcRole randomRole(Random random) {
		int r = random.nextInt(100);
		if (r < 30) return NpcRole.WARRIOR;
		if (r < 55) return NpcRole.ARCHER;
		if (r < 70) return NpcRole.BARBARIAN;
		if (r < 90) return NpcRole.CROSSBOWMAN;
		return NpcRole.MEDIC;
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			@Nullable EntityData entityData) {
		EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
		Random random = world.getRandom();
		setLeftHanded(false);
		setRole(roleFromNbt ? getRole() : randomRole(random));
		getDataTracker().set(VARIANT, random.nextInt(VARIANTS));
		boolean elite = getRole() == NpcRole.CAPTAIN || difficulty.getGlobalDifficulty() == Difficulty.HARD;
		NpcCombat.equip(this, getRole(), random, NpcCombat.randomBanditColor(random), elite);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			setEquipmentDropChance(slot, 0.06F);
		}
		if (spawnReason == SpawnReason.STRUCTURE) {
			garrison = true;
			setPersistent();
			home = getBlockPos();
			NpcRole.Kind kind = getRole().kind();
			setPositionTarget(home, kind == NpcRole.Kind.BOW || kind == NpcRole.Kind.CROSSBOW ? 3 : 10);
		}
		setHealth(getMaxHealth());
		return data;
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

	@Override
	public void onDeath(DamageSource source) {
		if (!getWorld().isClient && raidUntil != 0L) {
			BanditRaids.onRaiderDeath(this);
		}
		super.onDeath(source);
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient && raidUntil != 0L && !isRaiding()) {
			endRaid();
		}
	}

	// ------------------------------------------------------------------ CombatNpc

	@Override
	@Nullable
	public Vec3d getLeashCenter() {
		return garrison && home != null ? Vec3d.ofBottomCenter(home) : null;
	}

	@Override
	public double getLeashRadius() {
		return GARRISON_RADIUS;
	}

	// ------------------------------------------------------------------ comportamiento de monstruo

	/** Los bandidos no buscan la oscuridad como los monstruos normales. */
	@Override
	public float getPathfindingFavor(BlockPos pos, WorldView world) {
		return 0.0F;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return !garrison && !isRaiding() && super.canImmediatelyDespawn(distanceSquared);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (!getWorld().isClient && NpcCombat.tryShieldBlock(this, source)) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	protected Text getDefaultName() {
		return Text.translatable("entity.evolvedwarfare.bandit." + getRole().id());
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
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Role")) {
			getDataTracker().set(ROLE, NpcRole.byId(nbt.getString("Role"), NpcRole.WARRIOR).ordinal());
			roleFromNbt = true;
		}
		getDataTracker().set(VARIANT, Math.floorMod(nbt.getInt("Variant"), VARIANTS));
		garrison = nbt.getBoolean("Garrison");
		home = readPos(nbt, "Home");
		marchTarget = readPos(nbt, "March");
		raidUntil = nbt.getLong("RaidUntil");
		if (garrison && home != null) {
			NpcRole.Kind kind = getRole().kind();
			setPositionTarget(home, kind == NpcRole.Kind.BOW || kind == NpcRole.Kind.CROSSBOW ? 3 : 10);
		}
	}
}

package com.evolvedwarfare.entity;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.ai.HealAlliesGoal;
import com.evolvedwarfare.entity.ai.NpcMeleeAttackGoal;
import com.evolvedwarfare.entity.ai.NpcRangedAttackGoal;
import com.evolvedwarfare.entity.ai.OwnerTargetGoal;
import com.evolvedwarfare.faction.Reputation;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.skill.Perk;
import com.evolvedwarfare.skill.Skills;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Soldado del jugador: un recluta entrenado. Lucha según su clase, defiende a su dueño, ataca a los
 * monstruos y bandidos cercanos (sin alejarse de su zona) y sube de nivel con las bajas.
 */
public class SoldierEntity extends AbstractNpcEntity implements CombatNpc {
	public static final int MAX_LEVEL = 5;
	/** Experiencia necesaria para cada nivel (índice = nivel). */
	private static final int[] XP_FOR_LEVEL = {0, 0, 5, 15, 30, 50};
	private static final Identifier LEVEL_HEALTH = EvolvedWarfare.id("level_health");
	private static final Identifier LEVEL_DAMAGE = EvolvedWarfare.id("level_damage");
	private static final Identifier BONUS_HEALTH = EvolvedWarfare.id("research_health");
	private static final Identifier BONUS_DAMAGE = EvolvedWarfare.id("research_damage");
	private static final NpcRole[] TRAINABLE = {NpcRole.WARRIOR, NpcRole.BARBARIAN, NpcRole.ARCHER, NpcRole.CROSSBOWMAN, NpcRole.MEDIC};

	private static final TrackedData<Integer> ROLE = DataTracker.registerData(SoldierEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> LEVEL = DataTracker.registerData(SoldierEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private int experience;
	private int kills;
	private boolean roleFromNbt;

	public SoldierEntity(EntityType<? extends SoldierEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE, NpcRole.WARRIOR.ordinal());
		builder.add(LEVEL, 1);
	}

	@Override
	protected void initGoals() {
		super.initGoals();
		goalSelector.add(1, new NpcRangedAttackGoal(this));
		goalSelector.add(1, new NpcMeleeAttackGoal(this, 1.15));
		goalSelector.add(1, new HealAlliesGoal(this));
		targetSelector.add(1, new OwnerTargetGoal(this, true));
		targetSelector.add(2, new OwnerTargetGoal(this, false));
		targetSelector.add(3, new RevengeGoal(this));
		targetSelector.add(4, new ActiveTargetGoal<>(this, MobEntity.class, 5, true, false, this::isHostileTarget));
	}

	// ------------------------------------------------------------------ clase y nivel

	@Override
	public NpcRole getRole() {
		return NpcRole.byOrdinal(getDataTracker().get(ROLE));
	}

	/** Cambia la clase y ajusta vida, daño base y velocidad. */
	/** Fija la clase antes de initialize (mercenarios). */
	public void presetRole(NpcRole role) {
		getDataTracker().set(ROLE, role.ordinal());
		roleFromNbt = true;
	}

	public void setRole(NpcRole role) {
		getDataTracker().set(ROLE, role.ordinal());
		setBase(EntityAttributes.GENERIC_MAX_HEALTH, role.health());
		setBase(EntityAttributes.GENERIC_ATTACK_DAMAGE, role.attackDamage());
		setBase(EntityAttributes.GENERIC_MOVEMENT_SPEED, role.speed());
	}

	private void setBase(RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute, double value) {
		EntityAttributeInstance instance = getAttributeInstance(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	@Override
	public int getCombatLevel() {
		return getDataTracker().get(LEVEL);
	}

	public int getKills() {
		return kills;
	}

	public int getExperience() {
		return experience;
	}

	/** Experiencia que le falta para el siguiente nivel (0 si ya es el máximo). */
	public int experienceForNextLevel() {
		int level = getCombatLevel();
		return level >= MAX_LEVEL ? 0 : XP_FOR_LEVEL[level + 1];
	}

	public void gainExperience(int amount) {
		// veteranos (Liderazgo de su dueño): el doble de experiencia
		experience += Skills.has(getWorld(), getOwnerUuid(), Perk.VETERANS) ? amount * 2 : amount;
		int level = getCombatLevel();
		int newLevel = level;
		while (newLevel < MAX_LEVEL && experience >= XP_FOR_LEVEL[newLevel + 1]) {
			newLevel++;
		}
		if (newLevel != level) {
			setCombatLevel(newLevel);
			heal(getMaxHealth());
			playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8F, 1.0F);
			if (getWorld() instanceof ServerWorld world) {
				world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getBodyY(0.8), getZ(), 12, 0.4, 0.6, 0.4, 0.0);
			}
			notifyOwner(Text.translatable("message.evolvedwarfare.level_up", getName(), Text.translatable(getRole().translationKey()), newLevel));
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient && age % 100 == 0) {
			applyBonuses();
		}
	}

	/**
	 * La configuración (vida y daño de los soldados) y las ventajas de Liderazgo de su dueño: instrucción (+20 % de
	 * vida), tácticas (+15 % de daño) y medicina de campaña (se cura 1 de vida cada 5 segundos fuera de combate).
	 */
	private void applyBonuses() {
		EwConfig config = EwConfig.get();
		UUID owner = getOwnerUuid();
		double health = config.soldierHealth * (1.0 + Perk.DRILL.value(Skills.rank(getWorld(), owner, Perk.DRILL)) / 100.0);
		double damage = config.soldierDamage * (1.0 + Perk.TACTICS.value(Skills.rank(getWorld(), owner, Perk.TACTICS)) / 100.0);
		EntityAttributeInstance maxHealth = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.overwritePersistentModifier(new EntityAttributeModifier(BONUS_HEALTH, health - 1.0,
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		EntityAttributeInstance attack = getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (attack != null) {
			attack.overwritePersistentModifier(new EntityAttributeModifier(BONUS_DAMAGE, damage - 1.0,
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (getHealth() > getMaxHealth()) {
			setHealth(getMaxHealth());
		}
		if (getTarget() == null && getHealth() < getMaxHealth() && Skills.has(getWorld(), owner, Perk.FIELD_MEDICINE)) {
			heal(1.0F);
		}
	}

	/** Cada nivel por encima del 1: +2 de vida máxima y +0,5 de daño cuerpo a cuerpo. */
	private void setCombatLevel(int level) {
		getDataTracker().set(LEVEL, level);
		int bonus = level - 1;
		EntityAttributeInstance health = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (health != null) {
			health.overwritePersistentModifier(new EntityAttributeModifier(LEVEL_HEALTH, 2.0 * bonus, EntityAttributeModifier.Operation.ADD_VALUE));
		}
		EntityAttributeInstance damage = getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (damage != null) {
			damage.overwritePersistentModifier(new EntityAttributeModifier(LEVEL_DAMAGE, 0.5 * bonus, EntityAttributeModifier.Operation.ADD_VALUE));
		}
	}

	@Override
	public boolean onKilledOther(ServerWorld world, LivingEntity other) {
		boolean result = super.onKilledOther(world, other);
		int gained = 0;
		if (other instanceof FactionNpcEntity enemy) {
			gained = enemy.getRole() == NpcRole.CAPTAIN ? 10 : 3;
		} else if (other instanceof PlayerEntity) {
			gained = 3;
		} else if (other instanceof Monster) {
			gained = 1;
		}
		if (gained > 0) {
			kills++;
			gainExperience(gained);
		}
		return result;
	}

	// ------------------------------------------------------------------ a quién ataca

	public boolean fights() {
		return getRole().kind() != NpcRole.Kind.HEALER;
	}

	/** Objetivo válido: no es aliado ni algo pacífico, y está dentro de su zona. */
	public boolean canFight(LivingEntity target) {
		if (!fights() || target == this || !target.isAlive() || NpcSides.allies(this, target)) {
			return false;
		}
		if (target instanceof AnimalEntity || target instanceof MerchantEntity || target instanceof ArmorStandEntity) {
			return false;
		}
		return withinLeash(target.getPos());
	}

	/**
	 * Enemigos que ataca por su cuenta: guerreros de facciones enemigas de su dueño (o que vayan a por él o su dueño) y
	 * monstruos (no los neutrales, y los cuerpo a cuerpo no van a por creepers).
	 */
	private boolean isHostileTarget(LivingEntity target) {
		if (!canFight(target)) {
			return false;
		}
		if (target instanceof FactionNpcEntity enemy) {
			LivingEntity enemyTarget = enemy.getTarget();
			return enemy.isHordeRaider() || Reputation.hostile(getWorld(), getOwnerUuid(), enemy.getFaction()) || enemyTarget == this
					|| (enemyTarget != null && NpcSides.allies(this, enemyTarget));
		}
		if (!(target instanceof Monster) || target instanceof Angerable) {
			return false;
		}
		return !(target instanceof CreeperEntity) || getRole().kind() == NpcRole.Kind.BOW || getRole().kind() == NpcRole.Kind.CROSSBOW;
	}

	@Override
	@Nullable
	public Vec3d getLeashCenter() {
		switch (getEffectiveOrder()) {
			case FOLLOW -> {
				PlayerEntity owner = getOwner();
				return owner != null && owner.getWorld() == getWorld() ? owner.getPos() : getPos();
			}
			case STAY -> {
				BlockPos post = getStayPos();
				return post != null ? Vec3d.ofBottomCenter(post) : getPos();
			}
			default -> {
				BlockPos home = getHome();
				return home != null ? Vec3d.ofBottomCenter(home) : getPos();
			}
		}
	}

	@Override
	public double getLeashRadius() {
		return switch (getEffectiveOrder()) {
			case FOLLOW -> 20.0;
			case STAY -> 12.0;
			default -> 16.0;
		};
	}

	// ------------------------------------------------------------------ varios

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (!getWorld().isClient && NpcCombat.tryShieldBlock(this, source)) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	protected void initAppearance(Random random) {
		NpcNames.randomize(this, random);
	}

	/** Soldados que no salen de un recluta (p. ej. /summon): clase y equipo al azar. */
	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			@Nullable EntityData entityData) {
		EntityData data = super.initialize(world, difficulty, spawnReason, entityData);
		NpcRole role = roleFromNbt ? getRole() : TRAINABLE[world.getRandom().nextInt(TRAINABLE.length)];
		setRole(role);
		if (getMainHandStack().isEmpty()) {
			NpcCombat.equip(this, role, world.getRandom(), NpcCombat.SOLDIER_COLOR, false);
		}
		setHealth(getMaxHealth());
		return data;
	}

	@Override
	protected Text getDefaultName() {
		return Text.translatable(getRole().translationKey());
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("Role", getRole().id());
		nbt.putInt("Level", getCombatLevel());
		nbt.putInt("Experience", experience);
		nbt.putInt("Kills", kills);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Role")) {
			getDataTracker().set(ROLE, NpcRole.byId(nbt.getString("Role"), NpcRole.WARRIOR).ordinal());
			roleFromNbt = true;
		}
		getDataTracker().set(LEVEL, Math.max(1, Math.min(MAX_LEVEL, nbt.contains("Level") ? nbt.getInt("Level") : 1)));
		experience = nbt.getInt("Experience");
		kills = nbt.getInt("Kills");
	}
}

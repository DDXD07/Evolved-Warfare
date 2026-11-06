package com.evolvedwarfare.hell;

import com.evolvedwarfare.night.ZombieSiegeGoal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Nephilim: un gigante de piedra y carne quemada de las ruinas. Lento, pero pega muy fuerte, rompe los bloques que se le
 * ponen delante y, de vez en cuando, golpea el suelo y lanza por los aires a todo lo de alrededor. Algunos llevan restos de
 * armadura antigua (más duros).
 */
public class NephilimEntity extends DamnedBase {
	private static final TrackedData<Boolean> ARMORED = DataTracker.registerData(NephilimEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private int slam = 100;

	public NephilimEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 40;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 140.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 16.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5)
				.add(EntityAttributes.GENERIC_ARMOR, 4.0)
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ARMORED, false);
	}

	public boolean armored() {
		return dataTracker.get(ARMORED);
	}

	public void setArmored(boolean armored) {
		dataTracker.set(ARMORED, armored);
		var armor = getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (armor != null) {
			armor.setBaseValue(armored ? 14.0 : 4.0);
		}
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		setArmored(random.nextFloat() < 0.3F);
		return super.initialize(world, difficulty, reason, data);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Armored", armored());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		setArmored(nbt.getBoolean("Armored"));
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new ZombieSiegeGoal(this, false));
		goalSelector.add(2, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.6));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		LivingEntity t = getTarget();
		if (slam > 0) {
			slam--;
		} else if (t != null && squaredDistanceTo(t) < 25 && isOnGround()) {
			slam = 120 + random.nextInt(60);
			groundSlam();
		}
	}

	/** El golpe al suelo: daña y lanza por los aires a lo de alrededor (5 bloques). */
	public int groundSlam() {
		if (!(getWorld() instanceof ServerWorld world)) {
			return 0;
		}
		swingHand(net.minecraft.util.Hand.MAIN_HAND);
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 0.8F, 0.5F);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, getSteppingBlockState()), getX(), getY() + 0.1, getZ(), 60, 2.0, 0.1, 2.0,
				0.2);
		int hit = 0;
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(5, 2, 5), e -> e != this && !(e instanceof NephilimEntity))) {
			if (e.damage(getDamageSources().mobAttack(this), 7.0F)) {
				hit++;
			}
			e.addVelocity((e.getX() - getX()) * 0.25, 0.8, (e.getZ() - getZ()) * 0.25);
			e.velocityModified = true;
		}
		return hit;
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit) {
			getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.0F, 0.5F);
		}
		return hit;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected float getSoundVolume() {
		return 1.6F;
	}
}

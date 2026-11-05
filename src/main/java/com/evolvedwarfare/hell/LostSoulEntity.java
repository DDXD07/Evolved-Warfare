package com.evolvedwarfare.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.FlyGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Un alma en pena: un fantasma que vaga flotando por las llanuras de ceniza y el valle de las almas, gimiendo. Si te
 * acercas, va a por ti con los brazos por delante: su roce te quita vida y te deja débil y lento. Atraviesa el aire (no
 * cae) y no le hace nada el fuego.
 */
public class LostSoulEntity extends HostileEntity {
	public static final RegistryKey<DamageType> SOUL_TOUCH = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("soul_touch"));

	public LostSoulEntity(EntityType<? extends LostSoulEntity> type, World world) {
		super(type, world);
		this.moveControl = new FlightMoveControl(this, 10, true);
		this.experiencePoints = 6;
		setNoGravity(true);
	}

	public static DefaultAttributeContainer.Builder createSoulAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 14.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 14.0);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation nav = new BirdNavigation(this, world);
		nav.setCanPathThroughDoors(false);
		nav.setCanSwim(false);
		nav.setCanEnterOpenDoors(true);
		return nav;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(2, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.add(6, new FlyGoal(this, 0.7));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	public boolean tryAttack(Entity target) {
		if (!(target instanceof LivingEntity living) || !(getWorld() instanceof net.minecraft.server.world.ServerWorld world)) {
			return false;
		}
		DamageSource source = new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SOUL_TOUCH), this);
		boolean hit = living.damage(source, (float) getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
		if (hit) {
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 0), this);
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 0), this);
			world.spawnParticles(ParticleTypes.SOUL, living.getX(), living.getBodyY(0.6), living.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
		}
		return hit;
	}

	@Override
	public void tickMovement() {
		super.tickMovement();
		if (getWorld().isClient && random.nextInt(4) == 0) {
			getWorld().addParticle(ParticleTypes.SOUL, getParticleX(0.4), getRandomBodyY(), getParticleZ(0.4), 0.0, 0.02, 0.0);
		}
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.LOST_SOUL_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.LOST_SOUL_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.LOST_SOUL_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.7F;
	}

	@Override
	public int getMinAmbientSoundDelay() {
		return 160;
	}
}

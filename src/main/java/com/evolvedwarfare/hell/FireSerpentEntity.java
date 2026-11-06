package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.SpiderNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.World;

/**
 * Serpiente de fuego: rápida, repta por el suelo y sube por las paredes como una araña. Su mordisco prende fuego y deja un
 * veneno leve (abrasado) que hace más daño mientras ardes.
 */
public class FireSerpentEntity extends DamnedBase {
	private static final TrackedData<Boolean> CLIMBING = DataTracker.registerData(FireSerpentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	public FireSerpentEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 6;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.4)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(CLIMBING, false);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		return new SpiderNavigation(this, world);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(2, new MeleeAttackGoal(this, 1.3, false));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.8));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient) {
			dataTracker.set(CLIMBING, horizontalCollision);
		} else if (random.nextInt(3) == 0) {
			getWorld().addParticle(ParticleTypes.SMALL_FLAME, getParticleX(0.4), getRandomBodyY(), getParticleZ(0.4), 0, 0.01, 0);
		}
	}

	@Override
	public boolean isClimbing() {
		return dataTracker.get(CLIMBING);
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit && target instanceof LivingEntity living) {
			living.setOnFireFor(4.0F);
			living.addStatusEffect(new StatusEffectInstance(ModEffects.SCORCHED, 200, 0), this);
		}
		return hit;
	}
}

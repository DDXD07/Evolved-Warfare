package com.evolvedwarfare.legendary;

import java.util.EnumSet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * El diablito (fase 110): sale de los pozos de lava de 2x2 o más cuando estás cerca. Pequeño, rápido y risueño; anda por
 * encima de la lava, no le hace nada el fuego y el agua le quema. No se deja coger: guarda las distancias y te lanza
 * <b>ráfagas de tres bolas de fuego</b>.
 */
public class LavaImpEntity extends HostileEntity {
	private int burst;
	private int cooldown = 40;

	public LavaImpEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 15;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
				.add(EntityAttributes.GENERIC_ARMOR, 2.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(2, new FireballGoal(this));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.8));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	/** Guarda las distancias (entre 5 y 14 bloques) y dispara ráfagas de tres bolas de fuego. */
	static class FireballGoal extends Goal {
		private final LavaImpEntity imp;

		FireballGoal(LavaImpEntity imp) {
			this.imp = imp;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity t = imp.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = imp.getTarget();
			if (target == null) {
				return;
			}
			imp.getLookControl().lookAt(target, 30, 30);
			double d2 = imp.squaredDistanceTo(target);
			if (d2 < 5 * 5 && imp.getNavigation().isIdle()) {
				Vec3d away = NoPenaltyTargeting.findFrom(imp, 8, 4, target.getPos());
				if (away != null) {
					imp.getNavigation().startMovingTo(away.x, away.y, away.z, 1.3);
				}
			} else if (d2 > 14 * 14) {
				imp.getNavigation().startMovingTo(target, 1.1);
			} else if (d2 > 7 * 7) {
				imp.getNavigation().stop();
			}
			if (imp.burst > 0) {
				if (imp.age % 6 == 0) {
					imp.shoot(target);
					imp.burst--;
				}
			} else if (--imp.cooldown <= 0 && imp.canSee(target) && d2 < 20 * 20) {
				imp.burst = 3;
				imp.cooldown = 60 + imp.random.nextInt(30);
				imp.playSound(SoundEvents.ENTITY_VEX_CHARGE, 1.0F, 0.8F);
			}
		}
	}

	/** Lanza una bola de fuego a la presa (pública para las pruebas). */
	public SmallFireballEntity shoot(LivingEntity target) {
		Vec3d from = new Vec3d(getX(), getBodyY(0.7), getZ());
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(from);
		double spread = Math.sqrt(to.length()) * 0.25;
		Vec3d dir = new Vec3d(to.x + random.nextGaussian() * spread, to.y, to.z + random.nextGaussian() * spread).normalize();
		SmallFireballEntity ball = new SmallFireballEntity(getWorld(), this, dir);
		ball.setPosition(from.add(dir.multiply(0.8)));
		getWorld().spawnEntity(ball);
		playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.0F, 1.3F);
		return ball;
	}

	@Override
	public boolean canWalkOnFluid(FluidState state) {
		return state.isIn(FluidTags.LAVA);
	}

	@Override
	public boolean hurtByWater() {
		return true;
	}

	@Override
	public void tickMovement() {
		super.tickMovement();
		if (getWorld().isClient && random.nextInt(3) == 0) {
			getWorld().addParticle(random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMOKE, getParticleX(0.4), getRandomBodyY(),
					getParticleZ(0.4), 0, 0.02, 0);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_VEX_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_VEX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_BLAZE_DEATH;
	}

	@Override
	public float getSoundPitch() {
		return 0.7F;
	}
}

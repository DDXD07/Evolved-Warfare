package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModSounds;
import java.util.EnumSet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Un diablillo: pequeño, rápido y chillón; de lejos te tira bolas de fuego, de cerca araña. No le hace nada el fuego. */
public class ImpEntity extends HostileEntity {
	public ImpEntity(EntityType<? extends ImpEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 8;
	}

	public static DefaultAttributeContainer.Builder createImpAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.33)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(2, new FireballGoal(this));
		goalSelector.add(3, new MeleeAttackGoal(this, 1.2, false));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.9));
		goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	/** Tira una bola de fuego a la presa (desde los ojos). */
	public SmallFireballEntity shoot(LivingEntity target) {
		Vec3d from = getEyePos();
		Vec3d dir = target.getEyePos().subtract(from).normalize();
		SmallFireballEntity fireball = new SmallFireballEntity(getWorld(), this, dir);
		fireball.setPosition(from.x + dir.x * 0.6, from.y - 0.1, from.z + dir.z * 0.6);
		getWorld().spawnEntity(fireball);
		getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0F, 1.4F);
		return fireball;
	}

	/** De 5 a 18 bloques y viéndote: una bola de fuego cada 2,5 segundos (y se queda quieto apuntando). */
	static class FireballGoal extends Goal {
		private final ImpEntity imp;
		private int cooldown = 20;

		FireballGoal(ImpEntity imp) {
			this.imp = imp;
			setControls(EnumSet.of(Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity t = imp.getTarget();
			if (t == null || !t.isAlive()) {
				return false;
			}
			double d = imp.squaredDistanceTo(t);
			return d > 25.0 && d < 18.0 * 18.0 && imp.canSee(t);
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity t = imp.getTarget();
			if (t == null) {
				return;
			}
			imp.getLookControl().lookAt(t, 30.0F, 30.0F);
			if (--cooldown <= 0) {
				cooldown = 50;
				imp.shoot(t);
			}
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.IMP_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.IMP_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.IMP_DEATH;
	}
}

package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * Estatua de sal: una persona petrificada. Mientras alguien la mira, no se mueve ni un pelo; cuando nadie la mira, se gira
 * hacia ti y se acerca, y si te alcanza te araña y te da sed de sal.
 */
public class SaltStatueEntity extends DamnedBase {
	private float frozenYaw;
	private boolean watched;
	private int attackCooldown;

	public SaltStatueEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 8;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
				.add(EntityAttributes.GENERIC_ARMOR, 10.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void initGoals() {
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
	}

	/** ¿La mira alguien? */
	public boolean observed() {
		for (PlayerEntity p : getWorld().getPlayers()) {
			if (!p.isSpectator() && p.squaredDistanceTo(this) < 64 * 64 && FallenEntity.looksAt(p, this, 0.5)) {
				return true;
			}
		}
		return false;
	}

	public boolean watched() {
		return watched;
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		watched = observed();
		if (watched) {
			getNavigation().stop();
			setVelocity(0, getVelocity().y, 0);
			setYaw(frozenYaw);
			setBodyYaw(frozenYaw);
			setHeadYaw(frozenYaw);
			return;
		}
		if (attackCooldown > 0) {
			attackCooldown--;
		}
		if (getTarget() instanceof PlayerEntity p && p.isAlive()) {
			getLookControl().lookAt(p, 60, 60);
			getNavigation().startMovingTo(p, 1.0);
			if (squaredDistanceTo(p) < 2.2 && attackCooldown == 0) {
				attackCooldown = 30;
				if (tryAttack(p)) {
					Curses.curse(p, ModEffects.SALT_THIRST);
				}
			}
			if (age % 25 == 0) {
				getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.HOSTILE, 0.25F, 0.5F);
			}
		}
		frozenYaw = getYaw();
	}

	@Override
	public void tickMovement() {
		if (watched && !getWorld().isClient) {
			setVelocity(0, getVelocity().y, 0);
		}
		super.tickMovement();
	}

	@Override
	public boolean tryAttack(Entity target) {
		return !watched && super.tryAttack(target);
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (hurt && getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK,
					com.evolvedwarfare.registry.ModBlocks.SALT_BLOCK.getDefaultState()), getX(), getBodyY(0.6), getZ(), 10, 0.3, 0.4, 0.3, 0.1);
		}
		return hurt;
	}
}

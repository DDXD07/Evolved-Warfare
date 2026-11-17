package com.evolvedwarfare.legendary;

import com.evolvedwarfare.hell.MiniBossEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * El Coco (fase 106): a veces, al despertar, te está esperando junto a la cama. Alto, flaco y negro como la sombra, con el
 * saco a la espalda. Solo puede haber uno. Lejos de ti es invisible; se teletransporta a tu espalda, sus zarpazos dan
 * oscuridad y náuseas y, de vez en cuando, grita y apaga la luz. Si en tres minutos no lo vences, se esfuma.
 */
public class CocoEntity extends MiniBossEntity {
	/** Lo que tarda en irse si no lo vences (ticks). */
	public static final int LIFETIME = 20 * 60 * 3;
	private int life;

	public CocoEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world, BossBar.Color.PURPLE);
		this.experiencePoints = 120;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return attributes(140.0, 9.0, 4.0, 0.34).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(2, new MeleeAttackGoal(this, 1.2, true));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
	}

	/** Aparece a la espalda de la presa (en un hueco libre a 2-3 bloques). */
	public boolean behind(LivingEntity target) {
		float yaw = target.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d back = new Vec3d(MathHelper.sin(yaw), 0, -MathHelper.cos(yaw));
		for (double d = 2.5; d >= 1.5; d -= 0.5) {
			Vec3d at = target.getPos().add(back.multiply(d));
			if (teleport(at.x, at.y, at.z, true)) {
				getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.6F, 0.5F);
				lookAtEntity(target, 180, 180);
				return true;
			}
		}
		return false;
	}

	@Override
	protected int ability(ServerWorld world, LivingEntity target) {
		if (squaredDistanceTo(target) > 6 * 6 || random.nextBoolean()) {
			behind(target);
			return 80;
		}
		// el grito: todo se oscurece
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 2.0F, 0.4F);
		for (PlayerEntity p : players(16)) {
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 140, 0), this);
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0), this);
			p.sendMessage(Text.translatable("message.evolvedwarfare.coco.scream").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), true);
		}
		return 140;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hit = super.damage(source, amount);
		if (hit && !getWorld().isClient && isAlive() && random.nextInt(3) == 0 && source.getAttacker() instanceof LivingEntity attacker) {
			behind(attacker);
		}
		return hit;
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit && target instanceof LivingEntity l) {
			l.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0), this);
			l.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0), this);
			l.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0), this);
			playSound(SoundEvents.ENTITY_PHANTOM_BITE, 1.5F, 0.5F);
		}
		return hit;
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		LivingEntity target = getTarget();
		// lejos, invisible; cerca, se deja ver
		boolean far = target == null || squaredDistanceTo(target) > 8 * 8;
		if (far) {
			addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 25, 0, false, false));
		} else if (hasStatusEffect(StatusEffects.INVISIBILITY)) {
			removeStatusEffect(StatusEffects.INVISIBILITY);
		}
		if (age % 10 == 0) {
			world.spawnParticles(ParticleTypes.SMOKE, getX(), getBodyY(0.5), getZ(), 3, 0.3, 0.6, 0.3, 0.01);
		}
		if (++life > LIFETIME) {
			for (PlayerEntity p : players(48)) {
				p.sendMessage(Text.translatable("message.evolvedwarfare.coco.gone").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), false);
			}
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getBodyY(0.5), getZ(), 40, 0.4, 1.0, 0.4, 0.02);
			discard();
		}
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (getWorld() instanceof ServerWorld world) {
			for (PlayerEntity p : players(64)) {
				p.sendMessage(Text.translatable("message.evolvedwarfare.legendary.slain", getDisplayName()).formatted(Formatting.GOLD), false);
			}
			world.spawnParticles(ParticleTypes.SOUL, getX(), getBodyY(0.5), getZ(), 40, 0.4, 1.0, 0.4, 0.02);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.AMBIENT_CAVE.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_ENDERMAN_SCREAM;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_ENDERMAN_DEATH;
	}

	@Override
	public float getSoundPitch() {
		return 0.5F;
	}
}

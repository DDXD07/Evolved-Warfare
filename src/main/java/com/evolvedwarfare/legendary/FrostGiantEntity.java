package com.evolvedwarfare.legendary;

import com.evolvedwarfare.hell.MiniBossEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * El gigante de hielo (fase 103): ocho bloques de alto, lento y terrible. Pega con su garrote de hielo (empuja lejos y
 * hiela), <b>pisotea</b> el suelo (una onda que hace daño, lanza por los aires y ralentiza a los que están cerca),
 * <b>arranca y lanza bloques de hielo</b> a los que se alejan y, herido, <b>ruge</b> una ventisca que congela. Si nadie
 * se le acerca en mucho rato, se va.
 */
public class FrostGiantEntity extends MiniBossEntity implements Legendary {
	private int lonely;

	public FrostGiantEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world, BossBar.Color.WHITE);
		this.experiencePoints = 350;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return attributes(600.0, 20.0, 10.0, 0.27).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 2.0).add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 2.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(2, new MeleeAttackGoal(this, 1.1, true));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.6));
		goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 24.0F));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	protected int ability(ServerWorld world, LivingEntity target) {
		int phase = phase();
		double d2 = squaredDistanceTo(target);
		if (phase >= 2 && random.nextInt(3) == 0) {
			blizzard(world);
			return 120;
		}
		if (d2 < 8 * 8) {
			stomp(world);
			return phase == 3 ? 70 : 110;
		}
		throwBoulder(world, target);
		return phase == 3 ? 60 : 90;
	}

	/** El pisotón: daña, lanza por los aires y ralentiza a los que están cerca (en el suelo). */
	public int stomp(ServerWorld world) {
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 3.0F, 0.5F);
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.5F, 0.6F);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.getDefaultState()), getX(), getY() + 0.2, getZ(), 120,
				3.5, 0.2, 3.5, 0.2);
		int hit = 0;
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(7.0, 2.0, 7.0), e -> e != this && e.isAlive())) {
			if (e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) {
				continue;
			}
			if (e.damage(getDamageSources().mobAttack(this), 12.0F)) {
				Vec3d push = e.getPos().subtract(getPos()).multiply(1, 0, 1);
				push = push.lengthSquared() < 1.0E-3 ? new Vec3d(1, 0, 0) : push.normalize();
				e.addVelocity(push.x * 0.8, 0.9, push.z * 0.8);
				e.velocityModified = true;
				e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2), this);
				hit++;
			}
		}
		return hit;
	}

	/** Arranca un bloque de hielo y se lo tira a la presa (en arco). */
	public boolean throwBoulder(ServerWorld world, LivingEntity target) {
		BlockPos at = BlockPos.ofFloored(getX(), getY() + getHeight() + 1, getZ());
		if (!world.getBlockState(at).isAir()) {
			return false;
		}
		world.setBlockState(at, Blocks.PACKED_ICE.getDefaultState(), 2);
		FallingBlockEntity boulder = FallingBlockEntity.spawnFromBlock(world, at, Blocks.PACKED_ICE.getDefaultState());
		Vec3d to = target.getPos().subtract(boulder.getPos());
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		double time = Math.max(10.0, flat / 0.9);
		boulder.setVelocity(to.x / time, (to.y + 0.5 * 0.04 * time * time) / time, to.z / time);
		boulder.setHurtEntities(3.0F, 24);
		boulder.dropItem = false;
		if (!Legendaries.griefing(world)) {
			boulder.setDestroyedOnLanding();
		}
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 2.0F, 0.5F);
		swingHand(net.minecraft.util.Hand.MAIN_HAND);
		return true;
	}

	/** La ventisca: congela y ralentiza a todos los que tiene a 14 bloques. */
	public void blizzard(ServerWorld world) {
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.HOSTILE, 4.0F, 0.4F);
		world.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getBodyY(0.6), getZ(), 200, 7, 3, 7, 0.05);
		for (PlayerEntity p : players(14)) {
			p.setFrozenTicks(Math.min(p.getMinFreezeDamageTicks() + 160, p.getFrozenTicks() + 160));
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 1), this);
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 120, 1), this);
			p.sendMessage(Text.translatable("message.evolvedwarfare.legendary.blizzard").formatted(Formatting.AQUA, Formatting.ITALIC), true);
		}
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit) {
			playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 2.0F, 0.5F);
			if (target instanceof LivingEntity l) {
				l.setFrozenTicks(Math.min(l.getMinFreezeDamageTicks() + 60, l.getFrozenTicks() + 80));
			}
		}
		return hit;
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (getWorld() instanceof ServerWorld world && age % 20 == 0) {
			boolean near = world.getClosestPlayer(this, 160.0) != null;
			lonely = near ? 0 : lonely + 20;
			if (lonely > 20 * 60 * 5) {
				discard();
			}
			if (getVelocity().horizontalLengthSquared() > 0.002 && isOnGround()) {
				world.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 2.0F, 0.5F);
			}
		}
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (getWorld() instanceof ServerWorld world) {
			for (PlayerEntity p : world.getPlayers(p -> p.squaredDistanceTo(this) < 200 * 200)) {
				p.sendMessage(Text.translatable("message.evolvedwarfare.legendary.slain", getDisplayName()).formatted(Formatting.GOLD), false);
			}
		}
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_POLAR_BEAR_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_RAVAGER_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}

	@Override
	public float getSoundPitch() {
		return 0.45F;
	}
}

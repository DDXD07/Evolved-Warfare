package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.FlyGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Belcebú, Señor de las Moscas (mini jefe II): una mosca gigante que vuela. Cada 7 segundos suelta un enjambre: a los de
 * alrededor (7 bloques) les da veneno, hambre y náuseas.
 */
public class BeelzebubEntity extends HellBossEntity {
	private static final DustParticleEffect FLY = new DustParticleEffect(new Vector3f(0.05F, 0.05F, 0.04F), 0.8F);

	public BeelzebubEntity(EntityType<? extends BeelzebubEntity> type, World world) {
		super(type, world, 2, ModSounds.BEELZEBUB, BossBar.Color.GREEN);
		this.moveControl = new FlightMoveControl(this, 10, true);
		setNoGravity(true);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return bossAttributes(200.0, 10.0, 8.0, 0.25).add(EntityAttributes.GENERIC_FLYING_SPEED, 0.45);
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
		super.initGoals();
		goalSelector.add(5, new FlyGoal(this, 0.8));
	}

	/** El enjambre: devuelve a cuántos alcanza. */
	public int swarm(ServerWorld world) {
		int n = 0;
		for (LivingEntity e : around(7.0)) {
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 120, 1), this);
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 200, 1), this);
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0), this);
			n++;
		}
		world.spawnParticles(FLY, getX(), getBodyY(0.5), getZ(), 160, 3.5, 2.0, 3.5, 0.2);
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_BEE_LOOP_AGGRESSIVE, SoundCategory.HOSTILE, 3.0F, 0.5F);
		return n;
	}

	@Override
	protected int ability(ServerWorld world, LivingEntity target) {
		if (squaredDistanceTo(target) > 12 * 12) {
			return 20;
		}
		swarm(world);
		return 140;
	}

	@Override
	public void tickMovement() {
		super.tickMovement();
		if (getWorld().isClient && random.nextInt(2) == 0) {
			getWorld().addParticle(FLY, getParticleX(1.2), getRandomBodyY(), getParticleZ(1.2), 0.0, 0.0, 0.0);
		}
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}
}

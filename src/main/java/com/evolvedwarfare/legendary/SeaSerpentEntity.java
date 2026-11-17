package com.evolvedwarfare.legendary;

import com.evolvedwarfare.hell.MiniBossEntity;
import java.util.EnumSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.control.YawAdjustingLookControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.SwimNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
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
 * La serpiente marina (fase 104): catorce bloques de escamas que ondulan bajo el agua. Caza a los que están en el agua o
 * en barca: nada hacia ellos y muerde; <b>parte las barcas</b>, abre un <b>remolino</b> que arrastra al fondo y, muy herida,
 * llama a los <b>rayos</b>. Fuera del agua se seca y se debilita. Si nadie se le acerca en mucho rato, se va.
 */
public class SeaSerpentEntity extends MiniBossEntity implements Legendary {
	private int whirlpool;
	private int lonely;

	public SeaSerpentEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world, BossBar.Color.GREEN);
		this.moveControl = new AquaticMoveControl(this, 85, 10, 0.06F, 0.02F, true);
		this.lookControl = new YawAdjustingLookControl(this, 10);
		this.experiencePoints = 350;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return attributes(450.0, 18.0, 10.0, 1.2).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		return new SwimNavigation(this, world);
	}

	@Override
	public boolean isPushedByFluids() {
		return false;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new HuntGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false,
				p -> p.isTouchingWater() || p.getVehicle() instanceof BoatEntity));
	}

	/** Nada hacia su presa y muerde; si no tiene, ronda por el agua. */
	static class HuntGoal extends Goal {
		private final SeaSerpentEntity serpent;

		HuntGoal(SeaSerpentEntity serpent) {
			this.serpent = serpent;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return true;
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			SeaSerpentEntity s = serpent;
			LivingEntity target = s.getTarget();
			if (target == null || !target.isAlive()) {
				if (s.age % 60 == 0 || s.getNavigation().isIdle()) {
					BlockPos at = s.getBlockPos().add(s.random.nextInt(25) - 12, s.random.nextInt(7) - 3, s.random.nextInt(25) - 12);
					if (s.getWorld().getFluidState(at).isIn(FluidTags.WATER)) {
						s.getNavigation().startMovingTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 0.6);
					}
				}
				return;
			}
			s.getLookControl().lookAt(target, 30, 30);
			Vec3d aim = target.getPos().add(0, target.getVehicle() instanceof BoatEntity ? -0.5 : 0.3, 0);
			s.getMoveControl().moveTo(aim.x, aim.y, aim.z, 1.0);
			if (s.squaredDistanceTo(target) < 16.0 && s.age % 15 == 0) {
				if (target.getVehicle() instanceof BoatEntity boat) {
					s.capsize(boat);
				}
				s.tryAttack(target);
			}
		}
	}

	@Override
	public void travel(Vec3d movementInput) {
		if (canMoveVoluntarily() && isTouchingWater()) {
			updateVelocity(getMovementSpeed() * 0.08F, movementInput);
			move(MovementType.SELF, getVelocity());
			setVelocity(getVelocity().multiply(0.9));
		} else {
			super.travel(movementInput);
		}
	}

	@Override
	protected int ability(ServerWorld world, LivingEntity target) {
		int phase = phase();
		if (target.getVehicle() instanceof BoatEntity boat && squaredDistanceTo(boat) < 10 * 10) {
			capsize(boat);
			return 80;
		}
		if (phase == 3 && random.nextBoolean()) {
			lightning(world, target);
			return 90;
		}
		whirlpool = 80;
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 2.0F, 0.5F);
		return phase == 1 ? 200 : 140;
	}

	/** Parte la barca (y los que van en ella caen al agua). */
	public boolean capsize(BoatEntity boat) {
		if (!(getWorld() instanceof ServerWorld world) || boat.isRemoved()) {
			return false;
		}
		for (Entity p : boat.getPassengerList()) {
			if (p instanceof PlayerEntity player) {
				player.sendMessage(Text.translatable("message.evolvedwarfare.legendary.capsize").formatted(Formatting.DARK_AQUA, Formatting.ITALIC), true);
			}
		}
		boat.removeAllPassengers();
		world.spawnParticles(ParticleTypes.SPLASH, boat.getX(), boat.getY() + 0.5, boat.getZ(), 60, 1.2, 0.4, 1.2, 0.2);
		world.playSound(null, boat.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.HOSTILE, 2.0F, 0.7F);
		boat.kill();
		return true;
	}

	/** El rayo: cae sobre la presa. */
	public void lightning(ServerWorld world, LivingEntity target) {
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
			boolean cosmetic = !Legendaries.griefing(world);
			bolt.setCosmetic(cosmetic);
			world.spawnEntity(bolt);
			if (cosmetic) {
				target.damage(world.getDamageSources().lightningBolt(), 8.0F);
			}
		}
	}

	/** Un tick de remolino: arrastra hacia ella y hacia abajo a los que están en el agua cerca (público para las pruebas). */
	public int whirlpoolTick(ServerWorld world) {
		int pulled = 0;
		for (PlayerEntity p : players(14)) {
			if (!p.isTouchingWater()) {
				continue;
			}
			Vec3d to = getPos().subtract(p.getPos());
			Vec3d pull = to.lengthSquared() < 1.0E-3 ? Vec3d.ZERO : to.normalize().multiply(0.12);
			p.addVelocity(pull.x, -0.08, pull.z);
			p.velocityModified = true;
			if (whirlpool % 20 == 0) {
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1), this);
				p.sendMessage(Text.translatable("message.evolvedwarfare.legendary.whirlpool").formatted(Formatting.DARK_AQUA, Formatting.ITALIC), true);
			}
			pulled++;
		}
		world.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 1, getZ(), 30, 4, 2, 4, 0.1);
		return pulled;
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (whirlpool > 0) {
			whirlpool--;
			whirlpoolTick(world);
		}
		if (age % 20 == 0) {
			if (!isTouchingWater()) {
				// fuera del agua se seca
				damage(getDamageSources().dryOut(), 4.0F);
				addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3));
			}
			boolean near = world.getClosestPlayer(this, 160.0) != null;
			lonely = near ? 0 : lonely + 20;
			if (lonely > 20 * 60 * 5) {
				discard();
			}
		}
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit) {
			playSound(SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, 2.0F, 0.5F);
			if (target instanceof LivingEntity l) {
				l.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 100, 1), this);
			}
		}
		return hit;
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
	protected SoundEvent getAmbientSound() {
		return isTouchingWater() ? SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT : SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT_LAND;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_ELDER_GUARDIAN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_ELDER_GUARDIAN_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}

	@Override
	public float getSoundPitch() {
		return 0.6F;
	}
}

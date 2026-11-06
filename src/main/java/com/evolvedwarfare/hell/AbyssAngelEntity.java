package com.evolvedwarfare.hell;

import java.util.EnumSet;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * El Ángel del Abismo: lo despierta la Llave del Abismo en el trono. Vuela en círculos sobre su presa y cae en picado con
 * su espada de fuego. Desde el 60 % de vida llama a las langostas y apaga la luz (oscuridad); desde el 30 %, abre el pozo:
 * columnas de fuego bajo los pies de los que pelean.
 */
public class AbyssAngelEntity extends MiniBossEntity {
	int dive;

	public AbyssAngelEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world, BossBar.Color.PURPLE);
		this.moveControl = new FlightMoveControl(this, 15, true);
		setNoGravity(true);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return attributes(380.0, 13.0, 12.0, 0.3).add(EntityAttributes.GENERIC_FLYING_SPEED, 0.7);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation nav = new BirdNavigation(this, world);
		nav.setCanPathThroughDoors(false);
		nav.setCanSwim(false);
		return nav;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new HuntGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
	}

	/** Vuela en corro sobre la presa y, de vez en cuando, cae en picado. */
	static class HuntGoal extends Goal {
		private final AbyssAngelEntity angel;

		HuntGoal(AbyssAngelEntity angel) {
			this.angel = angel;
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
			LivingEntity target = angel.getTarget();
			if (target == null || !target.isAlive()) {
				if (angel.age % 40 == 0) {
					angel.getMoveControl().moveTo(angel.getX() + angel.random.nextInt(9) - 4, angel.getY() + angel.random.nextInt(5) - 2,
							angel.getZ() + angel.random.nextInt(9) - 4, 0.6);
				}
				return;
			}
			angel.getLookControl().lookAt(target, 30, 30);
			if (angel.dive > 0) {
				angel.dive--;
				angel.getMoveControl().moveTo(target.getX(), target.getY() + 0.5, target.getZ(), 1.8);
				if (angel.squaredDistanceTo(target) < 6.0) {
					angel.tryAttack(target);
					angel.dive = 0;
				}
				return;
			}
			double a = angel.age * 0.035;
			double r = 7.0;
			double y = target.getY() + 6.0 + MathHelper.sin(angel.age * 0.05F) * 1.5;
			angel.getMoveControl().moveTo(target.getX() + Math.cos(a) * r, y, target.getZ() + Math.sin(a) * r, 1.0);
			if (angel.random.nextInt(angel.phase() == 1 ? 70 : 50) == 0) {
				angel.dive = 40;
				angel.playSound(SoundEvents.ENTITY_PHANTOM_SWOOP, 2.0F, 0.5F);
			}
		}
	}

	@Override
	protected int ability(ServerWorld world, LivingEntity target) {
		int phase = phase();
		if (phase >= 2 && random.nextBoolean()) {
			// las langostas del humo (hasta 10 a la vez) y la oscuridad
			int near = world.getEntitiesByClass(AbyssLocustEntity.class, getBoundingBox().expand(32), e -> true).size();
			for (int i = 0; i < Math.min(4, 10 - near); i++) {
				AbyssLocustEntity l = (i == 0 && phase == 3 ? DamnedEntities.GREATER_LOCUST : DamnedEntities.ABYSS_LOCUST).create(world);
				if (l != null) {
					l.refreshPositionAndAngles(getX() + random.nextInt(5) - 2, getY(), getZ() + random.nextInt(5) - 2, random.nextFloat() * 360, 0);
					l.initialize(world, world.getLocalDifficulty(l.getBlockPos()), SpawnReason.MOB_SUMMONED, null);
					l.setTarget(target);
					world.spawnEntity(l);
				}
			}
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 80, 2, 1, 2, 0.05);
			for (PlayerEntity p : players(28)) {
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 160, 0), this);
				p.sendMessage(Text.translatable("message.evolvedwarfare.angel.locusts").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), true);
			}
			world.playSound(null, getBlockPos(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 2.0F, 0.6F);
			return 200;
		}
		if (phase == 3) {
			// el pozo se abre: fuego del cielo (y del suelo) bajo cada uno
			for (PlayerEntity p : players(32)) {
				for (int i = 0; i < 3; i++) {
					BlockPos at = p.getBlockPos().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
					Apocalypse.fireFromHeaven(world, at, this, 8.0F);
				}
				p.sendMessage(Text.translatable("message.evolvedwarfare.angel.pit").formatted(Formatting.DARK_RED, Formatting.ITALIC), true);
			}
			return 140;
		}
		// la espada de luz: una columna de fuego justo donde está la presa
		Apocalypse.fireFromHeaven(world, target.getBlockPos(), this, 7.0F);
		return 120;
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit) {
			target.setOnFireFor(4.0F);
			if (target instanceof LivingEntity l) {
				Vec3d push = l.getPos().subtract(getPos()).normalize();
				l.addVelocity(push.x * 0.8, 0.5, push.z * 0.8);
				l.velocityModified = true;
			}
			getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 0.6F, 1.4F);
		}
		return hit;
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient && random.nextInt(3) == 0) {
			getWorld().addParticle(ParticleTypes.SMOKE, getParticleX(0.8), getRandomBodyY(), getParticleZ(0.8), 0, -0.02, 0);
		}
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_PHANTOM_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_WITHER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_WITHER_DEATH;
	}

	@Override
	public float getSoundPitch() {
		return 0.6F;
	}
}

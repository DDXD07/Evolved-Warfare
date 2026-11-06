package com.evolvedwarfare.hell;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Langosta del Abismo: vuelan en enjambres de 8 a 15, en corro alrededor de su presa, y se lanzan en picado por turnos.
 * Su picadura nubla la vista (oscuridad) y debilita. Las mayores, más grandes y duras, sueltan metal del Abismo.
 */
public class AbyssLocustEntity extends DamnedBase {
	private int dive;

	public AbyssLocustEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.moveControl = new FlightMoveControl(this, 20, true);
		this.experiencePoints = greater() ? 8 : 2;
		setNoGravity(true);
	}

	public boolean greater() {
		return getType() == DamnedEntities.GREATER_LOCUST;
	}

	public static DefaultAttributeContainer.Builder createAttributes(boolean greater) {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, greater ? 22.0 : 6.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, greater ? 5.0 : 2.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, greater ? 0.55 : 0.7)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28.0);
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
		goalSelector.add(1, new SwarmGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	/** El enjambre: en corro sobre la presa y en picado, o, sin presa, juntas. */
	static class SwarmGoal extends Goal {
		private final AbyssLocustEntity locust;

		SwarmGoal(AbyssLocustEntity locust) {
			this.locust = locust;
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
			LivingEntity target = locust.getTarget();
			World world = locust.getWorld();
			if (target == null || !target.isAlive()) {
				if (locust.age % 20 == 0) {
					// juntas: hacia el centro de las de alrededor, con un poco de baile
					List<AbyssLocustEntity> near = world.getEntitiesByClass(AbyssLocustEntity.class, locust.getBoundingBox().expand(16), e -> e != locust);
					Vec3d c = locust.getPos();
					for (AbyssLocustEntity o : near) {
						c = c.add(o.getPos());
					}
					c = c.multiply(1.0 / (near.size() + 1));
					c = c.add((locust.random.nextDouble() - 0.5) * 6, (locust.random.nextDouble() - 0.4) * 3, (locust.random.nextDouble() - 0.5) * 6);
					locust.getMoveControl().moveTo(c.x, c.y, c.z, 0.8);
				}
				return;
			}
			locust.getLookControl().lookAt(target, 30, 30);
			if (locust.dive > 0) {
				locust.dive--;
				locust.getMoveControl().moveTo(target.getX(), target.getEyeY() - 0.3, target.getZ(), 1.6);
				if (locust.squaredDistanceTo(target) < 2.2) {
					locust.tryAttack(target);
					locust.dive = 0;
				}
				return;
			}
			// el corro: cada una en su sitio, girando
			double a = locust.getId() * 0.9 + locust.age * 0.06;
			double r = 3.0 + (locust.getId() % 3);
			double y = target.getY() + 3.0 + MathHelper.sin((locust.age + locust.getId() * 7) * 0.1F) * 0.8;
			locust.getMoveControl().moveTo(target.getX() + Math.cos(a) * r, y, target.getZ() + Math.sin(a) * r, 1.0);
			if (locust.random.nextInt(locust.greater() ? 45 : 60) == 0) {
				locust.dive = 30;
			}
		}
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit && target instanceof LivingEntity living) {
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, greater() ? 1 : 0), this);
			if (!living.hasStatusEffect(StatusEffects.DARKNESS)) {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0), this);
			}
		}
		return hit;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}

	/** En enjambre: hasta 16 juntas. */
	@Override
	public int getLimitPerChunk() {
		return 16;
	}

	@Override
	protected float getSoundVolume() {
		return 0.5F;
	}

	@Override
	public int getMinAmbientSoundDelay() {
		return 60;
	}
}

package com.evolvedwarfare.hell;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Sapo de lava: vive en las ciénagas, anda (y salta) sobre la lava como sobre tierra; se cría con sangrecascos. */
public class LavaToadEntity extends AnimalEntity {
	public LavaToadEntity(EntityType<? extends AnimalEntity> type, World world) {
		super(type, world);
		setPathfindingPenalty(PathNodeType.LAVA, 0.0F);
		setPathfindingPenalty(PathNodeType.DANGER_FIRE, 0.0F);
		setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 0.0F);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 8.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new EscapeDangerGoal(this, 1.6));
		goalSelector.add(2, new AnimalMateGoal(this, 1.0));
		goalSelector.add(3, new TemptGoal(this, 1.1, Ingredient.ofItems(HellFlora.BLOODCAP), false));
		goalSelector.add(4, new FollowParentGoal(this, 1.1));
		goalSelector.add(5, new WanderAroundGoal(this, 0.9));
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
		goalSelector.add(7, new LookAroundGoal(this));
	}

	@Override
	public boolean canWalkOnFluid(FluidState state) {
		return state.isIn(FluidTags.LAVA);
	}

	@Override
	public void tick() {
		super.tick();
		// salta de vez en cuando (y, en la lava, echa chispas)
		if (!getWorld().isClient && isOnGround() && getNavigation().isFollowingPath() && random.nextInt(12) == 0) {
			jump();
		}
		if (getWorld().isClient && random.nextInt(20) == 0) {
			getWorld().addParticle(ParticleTypes.LAVA, getX(), getY() + 0.3, getZ(), 0, 0, 0);
		}
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return stack.isOf(HellFlora.BLOODCAP.asItem());
	}

	@Nullable
	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
		return HellFlora.LAVA_TOAD.create(world);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_FROG_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_FROG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_FROG_DEATH;
	}

	@Override
	public float getSoundPitch() {
		return 0.5F;
	}
}

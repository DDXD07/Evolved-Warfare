package com.evolvedwarfare.hell;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
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
 * La Bestia mayor: sale del lago de fuego con siete cabezas y diez cuernos. Anda sobre la lava, escupe fuego con cada
 * cabeza que le queda, golpea el suelo y ruge (llama a las serpientes de fuego). Cada séptimo de vida que pierde le cae una
 * cabeza; cuando solo le queda una, su herida mortal sana (una vez): recupera vida y le vuelven a crecer dos cabezas.
 */
public class GreaterBeastEntity extends MiniBossEntity {
	public static final int HEADS = 7;
	private static final TrackedData<Integer> HEADS_LEFT = DataTracker.registerData(GreaterBeastEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private boolean healed;

	public GreaterBeastEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world, BossBar.Color.RED);
		setPathfindingPenalty(PathNodeType.LAVA, 0.0F);
		setPathfindingPenalty(PathNodeType.DANGER_FIRE, 0.0F);
		setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 0.0F);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return attributes(560.0, 16.0, 14.0, 0.24);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(HEADS_LEFT, HEADS);
	}

	public int heads() {
		return dataTracker.get(HEADS_LEFT);
	}

	public boolean healed() {
		return healed;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(2, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.6));
		goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 20.0F));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
	}

	/** Anda sobre la lava como sobre piedra. */
	@Override
	public boolean canWalkOnFluid(FluidState state) {
		return state.isIn(FluidTags.LAVA);
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		updateHeads();
	}

	/** Le caen las cabezas según la vida; con una sola, sana (una vez). */
	public void updateHeads() {
		int heads = heads();
		float f = getHealth() / getMaxHealth();
		while (heads > 1 && f < (heads - 1) / (float) HEADS) {
			heads--;
			dataTracker.set(HEADS_LEFT, heads);
			if (getWorld() instanceof ServerWorld world) {
				world.spawnParticles(ParticleTypes.LAVA, getX(), getY() + 3, getZ(), 30, 1.0, 0.5, 1.0, 0.1);
				world.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_STUNNED, SoundCategory.HOSTILE, 2.0F, 0.5F);
				for (PlayerEntity p : players(40)) {
					p.sendMessage(Text.translatable("message.evolvedwarfare.beast.head", heads).formatted(Formatting.RED), true);
				}
			}
		}
		if (heads == 1 && !healed) {
			healed = true;
			setHealth(getHealth() + getMaxHealth() * 0.3F);
			dataTracker.set(HEADS_LEFT, 3);
			if (getWorld() instanceof ServerWorld world) {
				world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 2, getZ(), 120, 1.5, 1.5, 1.5, 0.1);
				world.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.5F, 0.6F);
				for (PlayerEntity p : players(48)) {
					p.sendMessage(Text.translatable("message.evolvedwarfare.beast.healed").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
				}
			}
		}
	}

	@Override
	protected int ability(ServerWorld world, LivingEntity target) {
		int r = random.nextInt(phase() == 1 ? 2 : 3);
		if (r == 0 && squaredDistanceTo(target) < 36) {
			groundSlam(world);
			return 80;
		}
		if (r == 2) {
			roar(world, target);
			return 140;
		}
		breathe(world, target);
		return 60;
	}

	/** Una bola de fuego por cabeza, en abanico hacia la presa; devuelve cuántas. */
	public int breathe(ServerWorld world, LivingEntity target) {
		Vec3d from = new Vec3d(getX(), getY() + 2.6, getZ()).add(getRotationVector().multiply(1.8));
		Vec3d to = target.getEyePos().subtract(from);
		int n = heads();
		for (int i = 0; i < n; i++) {
			double spread = (i - (n - 1) / 2.0) * 0.12;
			Vec3d dir = to.normalize().rotateY((float) spread).add(0, (random.nextDouble() - 0.5) * 0.08, 0);
			SmallFireballEntity ball = new SmallFireballEntity(world, this, dir);
			ball.setPosition(from.x, from.y, from.z);
			world.spawnEntity(ball);
		}
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.0F, 0.5F);
		return n;
	}

	/** Golpe al suelo: daña y lanza a lo de alrededor (7 bloques). */
	public int groundSlam(ServerWorld world) {
		swingHand(net.minecraft.util.Hand.MAIN_HAND);
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.2F, 0.4F);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, getSteppingBlockState()), getX(), getY() + 0.1, getZ(), 100, 3.0, 0.1, 3.0,
				0.2);
		int hit = 0;
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(7, 2, 7), e -> e != this && !(e instanceof MiniBossEntity))) {
			if (e.damage(getDamageSources().mobAttack(this), 9.0F)) {
				hit++;
			}
			e.addVelocity((e.getX() - getX()) * 0.2, 0.9, (e.getZ() - getZ()) * 0.2);
			e.velocityModified = true;
		}
		return hit;
	}

	/** El rugido: lentitud a los de cerca y serpientes de fuego que salen de la lava. */
	public void roar(ServerWorld world, LivingEntity target) {
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 3.0F, 0.4F);
		for (PlayerEntity p : players(20)) {
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1), this);
		}
		int near = world.getEntitiesByClass(FireSerpentEntity.class, getBoundingBox().expand(24), e -> true).size();
		for (int i = 0; i < Math.min(3, 8 - near); i++) {
			FireSerpentEntity s = DamnedEntities.FIRE_SERPENT.create(world);
			if (s != null) {
				double a = random.nextDouble() * Math.PI * 2;
				s.refreshPositionAndAngles(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, random.nextFloat() * 360, 0);
				s.initialize(world, world.getLocalDifficulty(s.getBlockPos()), SpawnReason.MOB_SUMMONED, null);
				s.setTarget(target);
				world.spawnEntity(s);
			}
		}
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit) {
			target.setOnFireFor(5.0F);
			getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_ATTACK, SoundCategory.HOSTILE, 1.5F, 0.5F);
		}
		return hit;
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient && random.nextInt(2) == 0) {
			float yaw = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
			getWorld().addParticle(ParticleTypes.SMOKE, getX() - MathHelper.sin(yaw) * 2, getY() + 3.2, getZ() + MathHelper.cos(yaw) * 2, 0, 0.05, 0);
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Heads", heads());
		nbt.putBoolean("Healed", healed);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Heads")) {
			dataTracker.set(HEADS_LEFT, MathHelper.clamp(nbt.getInt("Heads"), 1, HEADS));
		}
		healed = nbt.getBoolean("Healed");
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, net.minecraft.util.math.BlockPos landedPosition) {
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_RAVAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_ENDER_DRAGON_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0F;
	}

	@Override
	public float getSoundPitch() {
		return 0.5F;
	}
}

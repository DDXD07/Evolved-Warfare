package com.evolvedwarfare.hell;

import java.util.EnumSet;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Cabra del desierto: no muerde. Busca dónde está el peligro junto a ti (lava o un barranco), se pone al otro lado, rasca el
 * suelo y embiste para tirarte. Las de cuernos de azufre empujan más y queman.
 */
public class DesertGoatEntity extends DamnedBase {
	private static final TrackedData<Boolean> SULFUR = DataTracker.registerData(DesertGoatEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	public DesertGoatEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 5;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(SULFUR, false);
	}

	public boolean sulfur() {
		return dataTracker.get(SULFUR);
	}

	public void setSulfur(boolean sulfur) {
		dataTracker.set(SULFUR, sulfur);
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		boolean mountains = world.getBiome(getBlockPos()).matchesKey(NetherClimate.MOUNTAINS);
		setSulfur(random.nextFloat() < (mountains ? 0.5F : 0.15F));
		return super.initialize(world, difficulty, reason, data);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Sulfur", sulfur());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		setSulfur(nbt.getBoolean("Sulfur"));
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new RamGoal(this));
		goalSelector.add(6, new WanderAroundFarGoal(this, 0.8));
		goalSelector.add(8, new LookAroundGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	/** Hacia dónde está el peligro junto a {@code pos} (lava o un barranco de 4), o null. */
	@Nullable
	public static Vec3d danger(World world, BlockPos pos) {
		for (int r = 1; r <= 6; r++) {
			for (int k = 0; k < 8; k++) {
				double a = k * Math.PI / 4;
				BlockPos p = pos.add((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r));
				boolean lava = false;
				int drop = 0;
				for (int dy = 0; dy >= -5; dy--) {
					BlockPos q = p.up(dy);
					if (world.getFluidState(q).isIn(FluidTags.LAVA)) {
						lava = true;
						break;
					}
					if (!world.getBlockState(q).getCollisionShape(world, q).isEmpty()) {
						break;
					}
					drop++;
				}
				if (lava || drop >= 5) {
					return new Vec3d(Math.cos(a), 0, Math.sin(a));
				}
			}
		}
		return null;
	}

	/** Embestir: colocarse al otro lado del peligro, rascar el suelo y cargar. */
	static class RamGoal extends Goal {
		private final DesertGoatEntity goat;
		private Vec3d push;
		private int phase;
		private int timer;
		private int cooldown;

		RamGoal(DesertGoatEntity goat) {
			this.goat = goat;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
		}

		@Override
		public boolean canStart() {
			if (cooldown > 0) {
				cooldown--;
				return false;
			}
			LivingEntity t = goat.getTarget();
			return t != null && t.isAlive() && goat.squaredDistanceTo(t) < 16 * 16;
		}

		@Override
		public boolean shouldContinue() {
			LivingEntity t = goat.getTarget();
			return t != null && t.isAlive() && phase < 3 && timer < 200;
		}

		@Override
		public void start() {
			phase = 0;
			timer = 0;
			LivingEntity t = goat.getTarget();
			push = t == null ? null : danger(goat.getWorld(), t.getBlockPos());
		}

		@Override
		public void stop() {
			cooldown = push != null ? 60 : 120;
			goat.getNavigation().stop();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity t = goat.getTarget();
			if (t == null) {
				return;
			}
			timer++;
			goat.getLookControl().lookAt(t, 30, 30);
			Vec3d dir = push != null ? push : t.getPos().subtract(goat.getPos()).multiply(1, 0, 1).normalize();
			switch (phase) {
				case 0 -> {
					// al otro lado del peligro, a unos 5 bloques
					Vec3d spot = t.getPos().subtract(dir.multiply(5));
					goat.getNavigation().startMovingTo(spot.x, spot.y, spot.z, 1.2);
					if (goat.getPos().squaredDistanceTo(spot) < 4 || timer > 120) {
						phase = 1;
						timer = 0;
						goat.getNavigation().stop();
					}
				}
				case 1 -> {
					// rasca el suelo
					if (timer % 6 == 0 && goat.getWorld() instanceof ServerWorld sw) {
						sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, goat.getSteppingBlockState()), goat.getX(), goat.getY() + 0.1,
								goat.getZ(), 6, 0.2, 0.05, 0.2, 0.05);
					}
					if (timer == 1) {
						goat.playSound(SoundEvents.ENTITY_GOAT_PREPARE_RAM, 1.0F, 0.8F);
					}
					if (timer >= 24) {
						phase = 2;
						timer = 0;
					}
				}
				default -> {
					// carga
					Vec3d to = t.getPos().subtract(goat.getPos()).multiply(1, 0, 1).normalize();
					goat.setVelocity(to.x * 0.75, goat.getVelocity().y, to.z * 0.75);
					goat.velocityModified = true;
					if (goat.squaredDistanceTo(t) < 2.6) {
						float strength = goat.sulfur() ? 2.2F : 1.6F;
						Vec3d k = (push != null ? push : to).multiply(strength);
						t.addVelocity(k.x, 0.45, k.z);
						t.velocityModified = true;
						t.damage(goat.getDamageSources().mobAttack(goat), (float) goat.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
						if (goat.sulfur()) {
							t.setOnFireFor(2.0F);
						}
						goat.getWorld().playSound(null, goat.getBlockPos(), SoundEvents.ENTITY_GOAT_RAM_IMPACT, SoundCategory.HOSTILE, 1.0F, 0.7F);
						phase = 3;
					} else if (timer > 30) {
						phase = 3;
					}
				}
			}
		}
	}
}

package com.evolvedwarfare.siege;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.entity.NpcSides;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModItems;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Proyectil de una máquina de asedio. La piedra y el pedrusco del trebuchet golpean en un radio y rompen los bloques
 * más blandos que su fuerza (más cuanto más cerca del impacto); la vasija incendiaria prende fuego alrededor; el virote
 * hace mucho daño a lo primero que toca.
 */
public class SiegeShotEntity extends ThrownEntity {
	private static final TrackedData<Integer> KIND = DataTracker.registerData(SiegeShotEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public enum Kind {
		/** Piedra de catapulta. */
		STONE(14.0F, 1.7F, 4.0F, 0.05),
		/** Pedrusco del trebuchet. */
		BOULDER(22.0F, 2.6F, 7.0F, 0.05),
		/** Vasija con fuego: quema en un radio, no rompe muros. */
		FIRE_POT(5.0F, 2.8F, 0.0F, 0.05),
		/** Virote de balista. */
		BOLT(20.0F, 0.0F, 0.0F, 0.025);

		public final float damage;
		public final float radius;
		public final float power;
		public final double gravity;

		Kind(float damage, float radius, float power, double gravity) {
			this.damage = damage;
			this.radius = radius;
			this.power = power;
			this.gravity = gravity;
		}

		public static Kind byOrdinal(int ordinal) {
			Kind[] values = values();
			return values[MathHelper.clamp(ordinal, 0, values.length - 1)];
		}
	}

	public SiegeShotEntity(EntityType<? extends SiegeShotEntity> type, World world) {
		super(type, world);
	}

	public SiegeShotEntity(World world, Kind kind, Vec3d pos, @Nullable Entity owner) {
		super(ModEntities.SIEGE_SHOT, pos.x, pos.y, pos.z, world);
		getDataTracker().set(KIND, kind.ordinal());
		setOwner(owner);
	}

	public Kind getKind() {
		return Kind.byOrdinal(getDataTracker().get(KIND));
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(KIND, 0);
	}

	@Override
	protected double getGravity() {
		return getKind().gravity;
	}

	@Override
	public void tick() {
		super.tick();
		World world = getWorld();
		if (world.isClient) {
			Kind kind = getKind();
			if (kind == Kind.FIRE_POT) {
				world.addParticle(ParticleTypes.FLAME, getX(), getY() + 0.2, getZ(), 0.0, 0.02, 0.0);
				world.addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 0.0, 0.02, 0.0);
			} else if (kind != Kind.BOLT && age % 2 == 0) {
				world.addParticle(ParticleTypes.POOF, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
			}
		} else if (age > 600) {
			discard();
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		super.onEntityHit(hit);
		if (getWorld().isClient || getKind() != Kind.BOLT) {
			return;
		}
		Entity target = hit.getEntity();
		Entity owner = getOwner();
		if (owner != null && NpcSides.allies(owner, target)) {
			return;
		}
		target.damage(source(), Kind.BOLT.damage * (float) EwConfig.get().siegeDamage);
		Vec3d push = getVelocity().normalize().multiply(0.8);
		target.addVelocity(push.x, 0.25, push.z);
		target.velocityModified = true;
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 1.2F, 0.8F);
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		super.onBlockHit(hit);
		if (getWorld().isClient || getKind() != Kind.BOLT) {
			return;
		}
		World world = getWorld();
		BlockPos pos = hit.getBlockPos();
		BlockState state = world.getBlockState(pos);
		// el virote atraviesa cristales y hojas; si no, se clava (y a veces se puede recoger)
		if (SiegeRules.blockDamage(world) && (state.getSoundGroup() == BlockSoundGroup.GLASS || state.isIn(BlockTags.LEAVES))) {
			world.breakBlock(pos, false, getOwner());
		} else if (random.nextFloat() < 0.4F) {
			dropStack(new ItemStack(ModItems.BALLISTA_BOLT));
		}
		world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 1.2F, 0.6F);
	}

	@Override
	protected void onCollision(HitResult hit) {
		super.onCollision(hit);
		if (getWorld() instanceof ServerWorld world) {
			Kind kind = getKind();
			if (kind != Kind.BOLT) {
				impact(world, hit.getPos(), kind);
			}
			discard();
		}
	}

	private DamageSource source() {
		return getDamageSources().thrown(this, getOwner());
	}

	private void impact(ServerWorld world, Vec3d at, Kind kind) {
		Entity owner = getOwner();
		double reach = kind.radius + 1.0;
		for (Entity entity : world.getOtherEntities(this, Box.of(at, reach * 2.0, reach * 2.0, reach * 2.0))) {
			if (!(entity instanceof LivingEntity) && !(entity instanceof SiegeEngineEntity)) {
				continue;
			}
			if (owner != null && (entity == owner || entity == owner.getVehicle() || NpcSides.allies(owner, entity))) {
				continue;
			}
			double distance = entity.getBoundingBox().getCenter().distanceTo(at);
			if (distance > reach) {
				continue;
			}
			float falloff = (float) ((1.0 - distance / (reach + 0.5)) * EwConfig.get().siegeDamage);
			if (kind == Kind.FIRE_POT) {
				entity.setOnFireFor(8.0F);
				entity.damage(source(), kind.damage * falloff);
			} else {
				entity.damage(source(), kind.damage * falloff);
				Vec3d push = entity.getPos().subtract(at).normalize().multiply(0.7 * falloff);
				entity.addVelocity(push.x, 0.3 * falloff, push.z);
				entity.velocityModified = true;
			}
		}
		if (kind == Kind.FIRE_POT) {
			world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_DECORATED_POT_SHATTER, SoundCategory.BLOCKS, 1.5F, 0.9F);
			world.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 1.5F, 1.0F);
			world.spawnParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 40, kind.radius * 0.5, 0.4, kind.radius * 0.5, 0.05);
			world.spawnParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 8, 0.5, 0.3, 0.5, 0.0);
			if (SiegeRules.blockDamage(world) && world.getGameRules().getBoolean(GameRules.DO_FIRE_TICK)) {
				spreadFire(world, at, kind.radius);
			}
			return;
		}
		world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS,
				kind == Kind.BOULDER ? 2.0F : 1.2F, kind == Kind.BOULDER ? 0.8F : 1.3F);
		world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, kind == Kind.BOULDER ? 4 : 2, 0.6, 0.4, 0.6, 0.0);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBBLESTONE.getDefaultState()),
				at.x, at.y + 0.2, at.z, 30, 0.6, 0.4, 0.6, 0.15);
		if (SiegeRules.blockDamage(world)) {
			breakBlocks(world, at, kind.radius, kind.power * (float) EwConfig.get().siegeBlockPower);
		}
	}

	/** Rompe los bloques del radio cuya dureza no pase de la fuerza del golpe en ese punto (ni cofres ni nada con dentro). */
	private void breakBlocks(ServerWorld world, Vec3d at, float radius, float power) {
		BlockPos center = BlockPos.ofFloored(at);
		int r = MathHelper.ceil(radius);
		Entity breaker = getOwner();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos pos = center.add(dx, dy, dz);
					double distance = Vec3d.ofCenter(pos).distanceTo(at);
					if (distance > radius) {
						continue;
					}
					BlockState state = world.getBlockState(pos);
					if (state.isAir() || state.isLiquid() || world.getBlockEntity(pos) != null) {
						continue;
					}
					float hardness = state.getHardness(world, pos);
					double strength = power * (1.0 - distance / (radius + 0.75));
					if (hardness < 0.0F || hardness > strength || random.nextFloat() > 0.85F) {
						continue;
					}
					world.breakBlock(pos, random.nextFloat() < 0.35F, breaker);
				}
			}
		}
	}

	private void spreadFire(ServerWorld world, Vec3d at, float radius) {
		BlockPos center = BlockPos.ofFloored(at);
		int r = MathHelper.ceil(radius);
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -1; dy <= 2; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos pos = center.add(dx, dy, dz);
					if (Vec3d.ofCenter(pos).distanceTo(at) > radius || random.nextFloat() > 0.55F || !world.isAir(pos)) {
						continue;
					}
					BlockState fire = AbstractFireBlock.getState(world, pos);
					if (fire.canPlaceAt(world, pos)) {
						world.setBlockState(pos, fire);
					}
				}
			}
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("ShotKind", getKind().name());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		try {
			getDataTracker().set(KIND, Kind.valueOf(nbt.getString("ShotKind")).ordinal());
		} catch (IllegalArgumentException ignored) {
			// valor desconocido: se queda en piedra
		}
	}
}

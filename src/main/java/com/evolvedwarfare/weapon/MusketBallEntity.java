package com.evolvedwarfare.weapon;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** La bala de un mosquete: vuela muy rápido y casi recta, hace 5 corazones de daño y empuja un bloque. */
public class MusketBallEntity extends ThrownItemEntity {
	/** Tipo de daño propio (mensaje de muerte "cayó de un disparo de..."). */
	public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, EvolvedWarfare.id("musket_ball"));
	/** Ticks que vive (a 5 bloques por tick, unos 300 bloques). */
	private static final int LIFETIME = 60;

	public MusketBallEntity(EntityType<? extends MusketBallEntity> type, World world) {
		super(type, world);
	}

	public MusketBallEntity(World world, LivingEntity owner) {
		super(ModEntities.MUSKET_BALL, owner, world);
		setItem(new ItemStack(ModItems.MUSKET_BALL));
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.MUSKET_BALL;
	}

	@Override
	protected double getGravity() {
		return 0.01;
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient && age > LIFETIME) {
			discard();
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		super.onEntityHit(hit);
		Entity target = hit.getEntity();
		DamageSource source = new DamageSource(getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE), this,
				getOwner());
		Vec3d direction = getVelocity().multiply(1.0, 0.0, 1.0);
		if (target.damage(source, MusketItem.DAMAGE) && target instanceof LivingEntity living && direction.lengthSquared() > 1.0E-6) {
			// empuja hacia donde iba la bala (el daño de bala no empuja por sí solo: está en minecraft:no_knockback)
			direction = direction.normalize();
			living.takeKnockback(MusketItem.KNOCKBACK, -direction.x, -direction.z);
		}
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		super.onBlockHit(hit);
		if (getWorld() instanceof ServerWorld server) {
			BlockState state = server.getBlockState(hit.getBlockPos());
			Vec3d pos = hit.getPos();
			if (!state.isAir()) {
				server.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z, 8, 0.05, 0.05, 0.05,
						0.1);
				server.playSound(null, pos.x, pos.y, pos.z, state.getSoundGroup().getHitSound(), SoundCategory.BLOCKS, 0.9F, 1.3F);
			}
		}
	}

	@Override
	protected void onCollision(HitResult hit) {
		super.onCollision(hit);
		if (getWorld() instanceof ServerWorld server) {
			Vec3d pos = hit.getPos();
			server.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 3, 0.04, 0.04, 0.04, 0.01);
			discard();
		}
	}
}

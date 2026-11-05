package com.evolvedwarfare.hell;

import com.evolvedwarfare.registry.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Moloc (mini jefe III): un gigante con cabeza de toro y un horno en el pecho. De lejos <b>escupe fuego</b> (un abanico de
 * bolas de fuego); de media distancia <b>embiste</b> (sale disparado hacia ti y te lanza por los aires).
 */
public class MolochEntity extends HellBossEntity {
	public MolochEntity(EntityType<? extends MolochEntity> type, World world) {
		super(type, world, 3, ModSounds.MOLOCH, BossBar.Color.YELLOW);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return bossAttributes(320.0, 14.0, 10.0, 0.24);
	}

	/** El aliento de fuego: 5 bolas en abanico. Devuelve cuántas. */
	public int breathe(ServerWorld world, LivingEntity target) {
		Vec3d from = getEyePos();
		Vec3d dir = target.getEyePos().subtract(from).normalize();
		for (int i = -2; i <= 2; i++) {
			Vec3d d = dir.rotateY(i * 0.12F);
			SmallFireballEntity fb = new SmallFireballEntity(world, this, d);
			fb.setPosition(from.x + d.x * 1.5, from.y - 0.4, from.z + d.z * 1.5);
			world.spawnEntity(fb);
		}
		world.spawnParticles(ParticleTypes.FLAME, from.x + dir.x * 1.5, from.y - 0.4, from.z + dir.z * 1.5, 30, 0.3, 0.3, 0.3, 0.1);
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.5F, 0.5F);
		return 5;
	}

	/** La embestida: sale disparado hacia la presa. */
	public void charge(ServerWorld world, LivingEntity target) {
		Vec3d d = target.getPos().subtract(getPos()).multiply(1, 0, 1).normalize();
		setVelocity(d.x * 1.6, 0.35, d.z * 1.6);
		velocityDirty = true;
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 3.0F, 0.6F);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 20, 0.8, 0.2, 0.8, 0.02);
	}

	@Override
	protected int ability(ServerWorld world, LivingEntity target) {
		double d = squaredDistanceTo(target);
		if (d > 9 * 9 && d < 24 * 24 && canSee(target)) {
			breathe(world, target);
			return 90;
		}
		if (d > 4 * 4 && d <= 9 * 9) {
			charge(world, target);
			return 120;
		}
		return 20;
	}

	@Override
	public void tick() {
		super.tick();
		// la embestida: al chocar con alguien, lo lanza
		if (!getWorld().isClient && getVelocity().horizontalLengthSquared() > 0.6) {
			for (LivingEntity e : around(1.8)) {
				e.damage(getDamageSources().mobAttack(this), 10.0F);
				e.takeKnockback(2.5, getX() - e.getX(), getZ() - e.getZ());
				e.addVelocity(0, 0.6, 0);
			}
		}
	}
}

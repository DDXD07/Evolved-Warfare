package com.evolvedwarfare.hell;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/** Polilla de brasa: revolotea en la oscuridad (y cuelga del techo de roca para descansar), soltando chispas. */
public class EmberMothEntity extends BatEntity {
	public EmberMothEntity(EntityType<? extends BatEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return BatEntity.createBatAttributes();
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient && random.nextInt(4) == 0) {
			getWorld().addParticle(random.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.ASH, getX(), getY() + 0.2, getZ(), 0, -0.02, 0);
		}
	}

	@Override
	public SoundEvent getAmbientSound() {
		return random.nextInt(4) == 0 ? SoundEvents.BLOCK_FIRE_AMBIENT : null;
	}

	@Override
	public SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_BAT_HURT;
	}

	@Override
	public SoundEvent getDeathSound() {
		return SoundEvents.BLOCK_FIRE_EXTINGUISH;
	}

	@Override
	public float getSoundVolume() {
		return 0.3F;
	}
}

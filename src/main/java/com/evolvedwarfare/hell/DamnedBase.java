package com.evolvedwarfare.hell;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

/** Lo común de las criaturas del Nether bíblico: sus sonidos. */
public abstract class DamnedBase extends HostileEntity {
	protected DamnedBase(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return DamnedEntities.sound(getType(), 0);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return DamnedEntities.sound(getType(), 1);
	}

	@Override
	protected SoundEvent getDeathSound() {
		return DamnedEntities.sound(getType(), 2);
	}
}

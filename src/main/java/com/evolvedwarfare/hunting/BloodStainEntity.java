package com.evolvedwarfare.hunting;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/** La mancha de sangre que queda al despellejar: se va apagando y desaparece a los 10 segundos. */
public class BloodStainEntity extends Entity {
	public static final int LIFE = 200;

	public BloodStainEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient && age >= LIFE) {
			discard();
		}
	}

	/** Lo que se ve (1 entera, 0 ya no está). */
	public float alpha(float tickDelta) {
		float left = LIFE - (age + tickDelta);
		return Math.max(0.0F, Math.min(1.0F, left / 60.0F));
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}

	@Override
	public boolean canHit() {
		return false;
	}
}

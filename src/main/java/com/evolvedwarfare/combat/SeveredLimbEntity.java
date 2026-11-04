package com.evolvedwarfare.combat;

import com.evolvedwarfare.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Un brazo, una pierna o una cabeza cortados: salen volando, ruedan y se quedan en el suelo medio minuto. Se dibujan con
 * el aspecto del NPC al que pertenecían (el cliente lo busca por su número mientras aún existe). No se guardan.
 */
public class SeveredLimbEntity extends Entity {
	private static final TrackedData<Integer> PART = DataTracker.registerData(SeveredLimbEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> OWNER = DataTracker.registerData(SeveredLimbEntity.class, TrackedDataHandlerRegistry.INTEGER);
	public static final int LIFETIME = 600;

	/** Cómo gira al volar y cómo queda tumbado (en el cliente). */
	public float spin;
	public float prevSpin;
	public final float restYaw;

	public SeveredLimbEntity(EntityType<? extends SeveredLimbEntity> type, World world) {
		super(type, world);
		this.restYaw = world.random.nextFloat() * 360.0F;
	}

	public static SeveredLimbEntity create(LivingEntity owner, int part, Vec3d at) {
		SeveredLimbEntity limb = new SeveredLimbEntity(ModEntities.SEVERED_LIMB, owner.getWorld());
		limb.refreshPositionAndAngles(at.x, at.y, at.z, owner.getBodyYaw(), 0.0F);
		limb.getDataTracker().set(PART, part);
		limb.getDataTracker().set(OWNER, owner.getId());
		return limb;
	}

	public int part() {
		return getDataTracker().get(PART);
	}

	public int ownerId() {
		return getDataTracker().get(OWNER);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(PART, Dismemberment.RIGHT_ARM);
		builder.add(OWNER, -1);
	}

	@Override
	public void tick() {
		super.tick();
		prevSpin = spin;
		if (!isOnGround()) {
			spin += 24.0F;
		}
		Vec3d v = getVelocity();
		if (!hasNoGravity()) {
			v = v.add(0.0, -0.04, 0.0);
		}
		setVelocity(v);
		move(MovementType.SELF, getVelocity());
		if (isOnGround()) {
			setVelocity(getVelocity().multiply(0.6, -0.3, 0.6));
		} else {
			setVelocity(getVelocity().multiply(0.98));
		}
		if (!getWorld().isClient && age > LIFETIME) {
			discard();
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}

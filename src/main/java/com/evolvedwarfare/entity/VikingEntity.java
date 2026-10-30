package com.evolvedwarfare.entity;

import com.evolvedwarfare.faction.Faction;
import java.util.List;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Vikingo: guerrero del norte. Clases: huscarl (hacha o espada con escudo), berserker (hacha, sin armadura; con poca
 * vida entra en furia), arquero, völva (curandera) y jarl (el jefe: con el cuerno da fuerza a los suyos).
 */
public class VikingEntity extends FactionNpcEntity {
	public static final int VARIANTS = 4;
	private static final int HORN_COOLDOWN = 600;
	private static final int RAGE_COOLDOWN = 600;

	private int hornCooldown;
	private int rageCooldown;

	public VikingEntity(EntityType<? extends VikingEntity> type, World world) {
		super(type, world);
	}

	@Override
	public Faction getFaction() {
		return Faction.VIKINGS;
	}

	/** Huscarles 40 %, berserkers 25 %, arqueros 25 %, völvas 10 %. */
	@Override
	protected NpcRole randomRole(Random random) {
		int r = random.nextInt(100);
		if (r < 40) return NpcRole.WARRIOR;
		if (r < 65) return NpcRole.BARBARIAN;
		if (r < 90) return NpcRole.ARCHER;
		return NpcRole.MEDIC;
	}

	@Override
	protected double healthFor(NpcRole role) {
		// más duros que los bandidos
		return role.health() + 4.0;
	}

	@Override
	protected double speedFor(NpcRole role) {
		return role == NpcRole.BARBARIAN ? role.speed() + 0.03 : role.speed();
	}

	@Override
	protected void equipFor(NpcRole role, Random random, boolean elite) {
		FactionGear.equipViking(this, role, random, elite);
	}

	@Override
	public int variantCount() {
		return VARIANTS;
	}

	@Override
	protected String nameKey() {
		return "viking";
	}

	@Override
	protected void tickAbilities() {
		if (hornCooldown > 0) {
			hornCooldown -= 20;
		}
		if (rageCooldown > 0) {
			rageCooldown -= 20;
		}
		NpcRole role = getRole();
		if (role == NpcRole.CAPTAIN && getTarget() != null && hornCooldown <= 0) {
			// el jarl toca el cuerno: los suyos cercanos pegan más fuerte un rato
			hornCooldown = HORN_COOLDOWN;
			getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE, 4.0F, 1.2F);
			List<VikingEntity> crew = getWorld().getEntitiesByClass(VikingEntity.class, getBoundingBox().expand(16.0), v -> v.isAlive());
			for (VikingEntity viking : crew) {
				viking.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 0));
			}
		}
		if (role == NpcRole.BARBARIAN && rageCooldown <= 0 && getHealth() < getMaxHealth() * 0.5F && getTarget() != null) {
			// furia del berserker
			rageCooldown = RAGE_COOLDOWN;
			addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 0));
			addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0));
			addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0));
			playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 0.8F, 1.4F);
		}
	}
}

package com.evolvedwarfare.entity;

import com.evolvedwarfare.faction.Faction;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Pirata: saqueador de las costas. Clases: pirata de abordaje (alfanje), tirador (ballesta, a veces con flechas
 * incendiarias), matón (hacha), cirujano de a bordo y capitán (bebe ron cuando está herido). Nadan sin ahogarse.
 */
public class PirateEntity extends FactionNpcEntity {
	public static final int VARIANTS = 4;
	private static final int RUM_COOLDOWN = 1200;

	private int rumCooldown;

	public PirateEntity(EntityType<? extends PirateEntity> type, World world) {
		super(type, world);
	}

	@Override
	public Faction getFaction() {
		return Faction.PIRATES;
	}

	/** Piratas 40 %, tiradores 30 %, matones 20 %, cirujanos 10 %. */
	@Override
	protected NpcRole randomRole(Random random) {
		int r = random.nextInt(100);
		if (r < 40) return NpcRole.WARRIOR;
		if (r < 70) return NpcRole.CROSSBOWMAN;
		if (r < 90) return NpcRole.BARBARIAN;
		return NpcRole.MEDIC;
	}

	@Override
	protected double speedFor(NpcRole role) {
		return role.speed() + 0.01;
	}

	@Override
	protected void equipFor(NpcRole role, Random random, boolean elite) {
		FactionGear.equipPirate(this, role, random, elite);
	}

	@Override
	public int variantCount() {
		return VARIANTS;
	}

	@Override
	protected String nameKey() {
		return "pirate";
	}

	/** Gente de mar: no se ahogan. */
	@Override
	public boolean canBreatheInWater() {
		return true;
	}

	@Override
	public void prepareShot(PersistentProjectileEntity arrow, boolean crossbow) {
		if (crossbow && getRandom().nextFloat() < 0.35F) {
			arrow.setOnFireFor(5.0F);
		}
	}

	@Override
	protected void tickAbilities() {
		if (rumCooldown > 0) {
			rumCooldown -= 20;
		}
		if (getRole() == NpcRole.CAPTAIN && rumCooldown <= 0 && getHealth() < getMaxHealth() * 0.4F) {
			// un trago de ron
			rumCooldown = RUM_COOLDOWN;
			heal(10.0F);
			playSound(SoundEvents.ENTITY_GENERIC_DRINK, 1.0F, 0.9F);
		}
	}
}

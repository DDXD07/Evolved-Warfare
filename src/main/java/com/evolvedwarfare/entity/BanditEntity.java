package com.evolvedwarfare.entity;

import com.evolvedwarfare.faction.Faction;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Bandido: forajido de llanuras y bosques. Viene en varias clases (guerrero, bárbaro, arquero, ballestero, médico y
 * capitán). No respeta la bandera blanca y nunca será tu aliado, pero se le puede sobornar.
 */
public class BanditEntity extends FactionNpcEntity {
	public static final int VARIANTS = 4;

	public BanditEntity(EntityType<? extends BanditEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createBanditAttributes() {
		return createFactionAttributes();
	}

	@Override
	public Faction getFaction() {
		return Faction.BANDITS;
	}

	/** Reparto al azar: guerreros 30 %, arqueros 25 %, bárbaros 15 %, ballesteros 20 %, médicos 10 %. */
	public static NpcRole randomBanditRole(Random random) {
		int r = random.nextInt(100);
		if (r < 30) return NpcRole.WARRIOR;
		if (r < 55) return NpcRole.ARCHER;
		if (r < 70) return NpcRole.BARBARIAN;
		if (r < 90) return NpcRole.CROSSBOWMAN;
		return NpcRole.MEDIC;
	}

	@Override
	protected NpcRole randomRole(Random random) {
		return randomBanditRole(random);
	}

	@Override
	protected void equipFor(NpcRole role, Random random, boolean elite) {
		NpcCombat.equip(this, role, random, NpcCombat.randomBanditColor(random), elite);
		FactionGear.paintShield(this, getFaction());
	}

	@Override
	public int variantCount() {
		return VARIANTS;
	}

	@Override
	protected String nameKey() {
		return "bandit";
	}
}

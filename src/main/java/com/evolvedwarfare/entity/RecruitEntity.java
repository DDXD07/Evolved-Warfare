package com.evolvedwarfare.entity;

import com.evolvedwarfare.registry.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * El recluta: un NPC sin oficio todavía. Vive en las ciudades, campamentos y torres que genera el mod
 * hasta que alguien lo contrata. Se puede entrenar como soldado dándole un arma (ver {@link NpcRole#fromWeapon}).
 */
public class RecruitEntity extends AbstractNpcEntity {
	/** Aspectos disponibles: 0-3 hombres, 4-5 mujeres. */
	public static final int VARIANTS = NpcNames.VARIANTS;

	public RecruitEntity(EntityType<? extends RecruitEntity> type, World world) {
		super(type, world);
	}

	/** Aspecto y nombre al azar (al generarse en una ciudad, campamento o torre). */
	@Override
	protected void initAppearance(Random random) {
		NpcNames.randomize(this, random);
	}

	/** Lo convierte en soldado de la clase indicada, con su mismo nombre, aspecto, equipo, dueño y paga. */
	@Nullable
	public SoldierEntity trainAs(NpcRole role) {
		SoldierEntity soldier = convertTo(ModEntities.SOLDIER, true);
		if (soldier == null) {
			return null;
		}
		copyNpcStateTo(soldier);
		soldier.setRole(role);
		soldier.setHealth(soldier.getMaxHealth());
		return soldier;
	}
}

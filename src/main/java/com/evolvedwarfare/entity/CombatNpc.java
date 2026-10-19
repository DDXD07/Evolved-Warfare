package com.evolvedwarfare.entity;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Lo que necesitan los objetivos de combate, sea un soldado del jugador o un bandido. */
public interface CombatNpc {
	NpcRole getRole();

	/** Nivel (1-5); sube el daño de las flechas. */
	int getCombatLevel();

	/** Centro de la zona que defiende (su dueño, su puesto o su casa), o null si puede perseguir libremente. */
	@Nullable
	Vec3d getLeashCenter();

	/** Distancia máxima a la que se aleja del centro para perseguir. */
	double getLeashRadius();

	/** ¿Puede perseguir a este objetivo sin abandonar su puesto? */
	default boolean withinLeash(Vec3d target) {
		Vec3d center = getLeashCenter();
		double r = getLeashRadius();
		return center == null || center.squaredDistanceTo(target) <= r * r;
	}
}

package com.evolvedwarfare.combat;

import com.evolvedwarfare.config.EwConfig;
import java.util.Locale;

/** Las cuatro partes del cuerpo en las que se divide a jugadores y NPCs para el daño locacional. */
public enum HitZone {
	HEAD,
	TORSO,
	ARMS,
	LEGS;

	/** Cuánto daño recibe esta parte (x), según la configuración: cabeza 2,5, torso 1,0, brazos 0,5, piernas 0,7. */
	public float multiplier() {
		EwConfig config = EwConfig.get();
		return (float) switch (this) {
			case HEAD -> config.headDamage;
			case TORSO -> config.torsoDamage;
			case ARMS -> config.armsDamage;
			case LEGS -> config.legsDamage;
		};
	}

	public String translationKey() {
		return "combat.evolvedwarfare.zone." + name().toLowerCase(Locale.ROOT);
	}
}

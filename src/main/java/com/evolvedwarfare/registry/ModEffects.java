package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.skill.BleedingEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

/** Efectos de estado propios. */
public final class ModEffects {
	/** Sangrado: lo causan las hachas con la ventaja "Sangrado". */
	public static final RegistryEntry<StatusEffect> BLEEDING = Registry.registerReference(Registries.STATUS_EFFECT,
			EvolvedWarfare.id("bleeding"), new BleedingEffect());

	/** Parásitos: los da el agua sin hervir ({@link com.evolvedwarfare.climate.Thirst}). */
	public static final RegistryEntry<StatusEffect> PARASITES = Registry.registerReference(Registries.STATUS_EFFECT,
			EvolvedWarfare.id("parasites"), new com.evolvedwarfare.climate.ParasitesEffect());

	private ModEffects() {
	}

	public static void init() {
	}
}

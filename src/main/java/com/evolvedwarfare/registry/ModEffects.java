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

	/** Infección: la da el mordisco de un zombi ({@link com.evolvedwarfare.night.NightThreat}). */
	public static final RegistryEntry<StatusEffect> INFECTION = Registry.registerReference(Registries.STATUS_EFFECT,
			EvolvedWarfare.id("infection"), new com.evolvedwarfare.night.InfectionEffect());

	/** Las maldiciones del Nether ({@link com.evolvedwarfare.hell.Curses}): duran hasta salir de él. */
	public static final RegistryEntry<StatusEffect> FEVER = Registry.registerReference(Registries.STATUS_EFFECT, EvolvedWarfare.id("fever"),
			new com.evolvedwarfare.hell.CurseEffect(com.evolvedwarfare.hell.CurseEffect.Kind.FEVER, 0xC83C1E)
					.addAttributeModifier(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED, EvolvedWarfare.id("effect.fever"), -0.2,
							net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	public static final RegistryEntry<StatusEffect> SALT_THIRST = Registry.registerReference(Registries.STATUS_EFFECT, EvolvedWarfare.id("salt_thirst"),
			new com.evolvedwarfare.hell.CurseEffect(com.evolvedwarfare.hell.CurseEffect.Kind.SALT_THIRST, 0xECE8DE));
	public static final RegistryEntry<StatusEffect> IDOL_EYES = Registry.registerReference(Registries.STATUS_EFFECT, EvolvedWarfare.id("idol_eyes"),
			new com.evolvedwarfare.hell.CurseEffect(com.evolvedwarfare.hell.CurseEffect.Kind.IDOL_EYES, 0xB48C28));
	public static final RegistryEntry<StatusEffect> JUDGEMENT_HEAT = Registry.registerReference(Registries.STATUS_EFFECT,
			EvolvedWarfare.id("judgement_heat"), new com.evolvedwarfare.hell.CurseEffect(com.evolvedwarfare.hell.CurseEffect.Kind.JUDGEMENT_HEAT, 0xFF8C1E));

	private ModEffects() {
	}

	public static void init() {
	}
}

package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Sonidos propios (los .ogg los hace tools/ew_sounds.py). */
public final class ModSounds {
	/** Disparo de mosquete. */
	public static final SoundEvent MUSKET_FIRE = register("musket_fire");
	/** La bala cae por el cañón. */
	public static final SoundEvent MUSKET_BALL_IN = register("musket_ball_in");
	/** La baqueta aprieta la carga. */
	public static final SoundEvent MUSKET_RAMROD = register("musket_ramrod");
	/** Se sube el rastrillo (o se cierra la cazoleta). */
	public static final SoundEvent MUSKET_COCK = register("musket_cock");
	/** Sin balas. */
	public static final SoundEvent MUSKET_EMPTY = register("musket_empty");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = EvolvedWarfare.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void init() {
	}
}

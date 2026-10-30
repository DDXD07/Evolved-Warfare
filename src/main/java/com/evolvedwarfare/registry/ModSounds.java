package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/** Sonidos propios (los .ogg los hacen tools/ew_sounds.py, los del mosquete, y tools/ew_voices.py, las voces). */
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

	/** Voces de los NPCs, una por facción: bandidos, vikingos, piratas, tu gente (reclutas, soldados, trabajadores) y comerciantes. */
	public static final Voice BANDIT = voice("bandit");
	public static final Voice VIKING = voice("viking");
	public static final Voice PIRATE = voice("pirate");
	public static final Voice RECRUIT = voice("recruit");
	public static final Voice TRADER = voice("trader");
	/** El «sí» de los reclutas al contratarlos y el de los comerciantes al cerrar un trato; el «no» de los comerciantes. */
	public static final SoundEvent RECRUIT_YES = register("npc.recruit.yes");
	public static final SoundEvent TRADER_YES = register("npc.trader.yes");
	public static final SoundEvent TRADER_NO = register("npc.trader.no");

	/** Lo que dice un NPC: su voz de siempre, su queja al recibir un golpe y al morir. */
	public record Voice(SoundEvent ambient, SoundEvent hurt, SoundEvent death) {
	}

	/**
	 * El tono de cada NPC: cada uno tiene su voz, un poco más grave o más aguda (sale de su UUID, así no cambia), y
	 * cada vez varía un pelín.
	 */
	public static float pitch(Entity entity) {
		long bits = entity.getUuid().getLeastSignificantBits() ^ entity.getUuid().getMostSignificantBits();
		float own = 0.88F + (Math.floorMod(bits, 1000L) / 1000.0F) * 0.24F;
		Random random = entity.getRandom();
		return own * (1.0F + (random.nextFloat() - random.nextFloat()) * 0.05F);
	}

	private static Voice voice(String name) {
		return new Voice(register("npc." + name + ".ambient"), register("npc." + name + ".hurt"), register("npc." + name + ".death"));
	}

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = EvolvedWarfare.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void init() {
	}
}

package com.evolvedwarfare.hell;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModEffects;
import java.util.List;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Poner y quitar las maldiciones del Nether: duran (sin fin) hasta que sales de él. */
public final class Curses {
	public static final RegistryKey<net.minecraft.entity.damage.DamageType> JUDGEMENT = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
			EvolvedWarfare.id("judgement"));

	private Curses() {
	}

	public static List<RegistryEntry<StatusEffect>> all() {
		return List.of(ModEffects.FEVER, ModEffects.SALT_THIRST, ModEffects.IDOL_EYES, ModEffects.JUDGEMENT_HEAT);
	}

	public static RegistryEntry<StatusEffect> random(Random random) {
		List<RegistryEntry<StatusEffect>> all = all();
		return all.get(random.nextInt(all.size()));
	}

	public static boolean inNether(PlayerEntity player) {
		return player.getWorld().getRegistryKey() == World.NETHER;
	}

	/** ¿En creativo? (según su modo de juego en el servidor). */
	public static boolean creative(PlayerEntity player) {
		if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
			return sp.interactionManager.getGameMode().isCreative();
		}
		return player.isCreative();
	}

	/** Le echa la maldición (si no la tenía), sin fin. */
	public static boolean curse(PlayerEntity player, RegistryEntry<StatusEffect> curse) {
		if (player.hasStatusEffect(curse) || creative(player) || player.isSpectator() || player.hasStatusEffect(Apocalypse.BLESSED_WARD)) {
			return false;
		}
		return player.addStatusEffect(new StatusEffectInstance(curse, StatusEffectInstance.INFINITE, 0, false, true, true));
	}

	/** Se las quita todas (al salir del Nether o con un ritual); devuelve si tenía alguna. */
	public static boolean lift(PlayerEntity player) {
		boolean any = false;
		for (RegistryEntry<StatusEffect> c : all()) {
			any |= player.removeStatusEffect(c);
		}
		return any;
	}

	public static DamageSource judgement(ServerWorld world) {
		return world.getDamageSources().create(JUDGEMENT);
	}
}

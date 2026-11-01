package com.evolvedwarfare.client;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.registry.ModSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Ruidos de ambiente del Nether: mientras estás allí, cada cierto tiempo (al azar entre la mitad y vez y media de lo
 * que diga la configuración, 2 minutos de normal) suena uno de los cinco, en la categoría Ambiente y sin posición (se oye
 * igual por los dos lados). Nunca dos a la vez. Al entrar en el Nether, el primero tarda un poco.
 */
public final class NetherAmbience {
	private static final Random RANDOM = Random.create();
	/** Tics que faltan para el siguiente; -1: no estás en el Nether. */
	private static int countdown = -1;
	private static SoundInstance playing;

	private NetherAmbience() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(NetherAmbience::tick);
	}

	private static void tick(MinecraftClient client) {
		int seconds = EwConfig.get().netherAmbienceSeconds;
		if (client.world == null || client.player == null || seconds <= 0 || !client.world.getRegistryKey().equals(World.NETHER)) {
			countdown = -1;
			return;
		}
		if (client.isPaused()) {
			return;
		}
		if (countdown < 0) {
			// recién llegado: el primero, entre un cuarto y tres cuartos del tiempo
			countdown = delay(seconds, 0.25, 0.75);
			return;
		}
		if (playing != null && client.getSoundManager().isPlaying(playing)) {
			return;
		}
		playing = null;
		if (--countdown > 0) {
			return;
		}
		float volume = (float) Math.max(0.0, Math.min(1.0, EwConfig.get().netherAmbienceVolume));
		if (volume > 0.0F) {
			float pitch = 0.92F + RANDOM.nextFloat() * 0.16F;
			playing = PositionedSoundInstance.ambient(ModSounds.NETHER_AMBIENCE, pitch, volume);
			client.getSoundManager().play(playing);
		}
		countdown = delay(seconds, 0.5, 1.5);
	}

	private static int delay(int seconds, double min, double max) {
		double factor = min + RANDOM.nextDouble() * (max - min);
		return Math.max(20, (int) Math.round(seconds * 20 * factor));
	}
}

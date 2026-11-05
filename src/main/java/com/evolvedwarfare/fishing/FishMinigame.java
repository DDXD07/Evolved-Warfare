package com.evolvedwarfare.fishing;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * El minijuego de pesca (como en Stardew Valley), tic a tic: una barra vertical (0 abajo, 1 arriba) con el pez, que se
 * mueve según su forma de nadar y su dificultad, y la zona verde, que sube mientras mantienes pulsado y baja al soltar.
 * Con el pez dentro de la zona se llena la barra de captura; fuera, se vacía, y si llega a cero se escapa. A veces sale un
 * cofre: si lo mantienes en la zona un rato, es tuyo (pero mientras tanto el pez se puede ir). Si el pez no sale ni una
 * vez de la zona, la captura es perfecta.
 */
public class FishMinigame {
	public final int difficulty;
	public final FishSpecies.Behavior behavior;
	public final float zoneSize;
	private final Random random;

	public float zone;
	public float zoneVel;
	public float fish = 0.5F;
	public float fishVel;
	private float target = 0.5F;
	public float progress = 0.3F;
	public boolean perfect = true;
	public int ticks;

	public final boolean hasTreasure;
	private final int treasureAt;
	public boolean treasureVisible;
	public float treasure;
	public float treasureProgress;
	public boolean treasureCaught;

	public float prevZone;
	public float prevFish;

	public FishMinigame(int difficulty, FishSpecies.Behavior behavior, float zoneSize, boolean treasure, Random random) {
		this.difficulty = difficulty;
		this.behavior = behavior;
		this.zoneSize = MathHelper.clamp(zoneSize, 0.1F, 0.6F);
		this.random = random;
		this.hasTreasure = treasure;
		this.treasureAt = 25 + random.nextInt(40);
		this.zone = 0.0F;
		this.fish = 0.3F + random.nextFloat() * 0.4F;
	}

	/** ¿Está el pez dentro de la zona verde? */
	public boolean inside() {
		return fish >= zone && fish <= zone + zoneSize;
	}

	public boolean done() {
		return progress >= 1.0F || progress <= 0.0F;
	}

	public boolean caught() {
		return progress >= 1.0F;
	}

	/** Un tic: {@code holding} es si se mantiene pulsado. */
	public void tick(boolean holding) {
		if (done()) {
			return;
		}
		ticks++;
		prevZone = zone;
		prevFish = fish;
		// la zona verde: sube al pulsar, cae al soltar, rebota en el fondo
		zoneVel += holding ? 0.0105F : -0.0085F;
		zoneVel = MathHelper.clamp(zoneVel, -0.07F, 0.07F);
		zone += zoneVel;
		if (zone < 0.0F) {
			zone = 0.0F;
			zoneVel = -zoneVel * 0.35F;
		}
		if (zone > 1.0F - zoneSize) {
			zone = 1.0F - zoneSize;
			zoneVel = 0.0F;
		}
		// el pez: elige a dónde ir según cómo nada, y va (suave o a lo loco)
		float d = difficulty / 100.0F;
		float change = switch (behavior) {
			case SMOOTH -> 0.025F + d * 0.04F;
			case DART -> 0.05F + d * 0.12F;
			default -> 0.04F + d * 0.08F;
		};
		if (random.nextFloat() < change) {
			float t = random.nextFloat();
			switch (behavior) {
				case SINKER -> t = t * t;
				case FLOATER -> t = 1.0F - t * t;
				case DART -> t = random.nextBoolean() ? t * 0.3F : 0.7F + t * 0.3F;
				default -> {
				}
			}
			target = MathHelper.clamp(t, 0.0F, 1.0F);
			if (behavior == FishSpecies.Behavior.DART && random.nextFloat() < d) {
				fishVel += (target - fish) * 0.25F; // un tirón
			}
		}
		float accel = (behavior == FishSpecies.Behavior.SMOOTH ? 0.0025F : 0.004F) + d * 0.006F;
		fishVel += (target - fish) * accel * 4.0F;
		fishVel += (random.nextFloat() - 0.5F) * d * 0.02F;
		fishVel *= behavior == FishSpecies.Behavior.SMOOTH ? 0.86F : 0.9F;
		fishVel = MathHelper.clamp(fishVel, -0.09F, 0.09F);
		fish = MathHelper.clamp(fish + fishVel, 0.0F, 1.0F);
		// la captura
		boolean in = inside();
		if (in) {
			progress += 0.0125F;
		} else {
			progress -= 0.0065F + d * 0.004F;
			if (ticks > 20) {
				perfect = false;
			}
		}
		// el cofre
		if (hasTreasure && !treasureCaught) {
			if (!treasureVisible && ticks >= treasureAt) {
				treasureVisible = true;
				treasure = 0.1F + random.nextFloat() * 0.8F;
			}
			if (treasureVisible) {
				boolean over = treasure >= zone && treasure <= zone + zoneSize;
				treasureProgress = MathHelper.clamp(treasureProgress + (over ? 0.022F : -0.008F), 0.0F, 1.0F);
				if (treasureProgress >= 1.0F) {
					treasureCaught = true;
					treasureVisible = false;
				}
			}
		}
		progress = MathHelper.clamp(progress, 0.0F, 1.0F);
	}

	/** El tamaño de la zona verde (de la barra entera) según el nivel de pesca, el encantamiento Atracción y el cebo. */
	public static float zoneSize(int level, int lure, int bait) {
		return Math.min(0.55F, 0.2F + level * 0.025F + lure * 0.025F + bait * 0.04F);
	}
}

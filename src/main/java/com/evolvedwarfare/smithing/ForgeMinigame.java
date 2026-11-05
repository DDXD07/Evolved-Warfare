package com.evolvedwarfare.smithing;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * El minijuego de forja: el martillo va y viene por una barra; hay que golpear (clic o espacio) cuando está en la zona
 * naranja (en el centro, más brillante, el golpe perfecto). Cada pieza lleva sus golpes; tras cada uno la zona se mueve y
 * el martillo va más rápido. El lingote se va enfriando: si se enfría, los golpes que faltan cuentan como fallos. La
 * puntería media da la calidad.
 */
public class ForgeMinigame {
	public static final float PERFECT = 1.0F;
	public static final float GOOD = 0.6F;
	public static final float POOR = 0.25F;

	public final int strikes;
	public final float halfWidth;
	private final float baseSpeed;
	private final Random random;
	public float hammer;
	public float prevHammer;
	private float dir = 1.0F;
	public float target;
	public float heat = 1.0F;
	public final float[] scores;
	public int struck;
	public int ticks;

	public ForgeMinigame(int strikes, int level, float materialSpeed, Random random) {
		this.strikes = strikes;
		this.scores = new float[strikes];
		this.halfWidth = 0.08F + level * 0.007F;
		this.baseSpeed = 0.028F * materialSpeed;
		this.random = random;
		this.target = 0.25F + random.nextFloat() * 0.5F;
	}

	public boolean done() {
		return struck >= strikes;
	}

	public float speed() {
		return baseSpeed * (1.0F + struck * 0.08F);
	}

	public void tick() {
		if (done()) {
			return;
		}
		ticks++;
		prevHammer = hammer;
		hammer += dir * speed();
		if (hammer > 1.0F) {
			hammer = 2.0F - hammer;
			dir = -1.0F;
		} else if (hammer < 0.0F) {
			hammer = -hammer;
			dir = 1.0F;
		}
		heat -= 0.0045F;
		if (heat <= 0.0F) {
			heat = 0.0F;
			while (struck < strikes) {
				scores[struck++] = 0.0F;
			}
		}
	}

	/** Un golpe: devuelve lo que vale (1 perfecto, 0,6 bien, 0,25 regular, 0 fallo). */
	public float strike() {
		if (done()) {
			return 0.0F;
		}
		float d = Math.abs(hammer - target);
		float score = d <= halfWidth * 0.35F ? PERFECT : d <= halfWidth ? GOOD : d <= halfWidth * 1.8F ? POOR : 0.0F;
		scores[struck++] = score;
		target = MathHelper.clamp(0.15F + random.nextFloat() * 0.7F, halfWidth, 1.0F - halfWidth);
		return score;
	}

	public float score() {
		float sum = 0.0F;
		for (int i = 0; i < struck; i++) {
			sum += scores[i];
		}
		return strikes == 0 ? 0.0F : sum / strikes;
	}

	/** La calidad que sale de la puntería media: 1 tosca, 2 común, 3 buena, 4 obra maestra. */
	public static int quality(float score) {
		return score < 0.35F ? 1 : score < 0.6F ? 2 : score < 0.85F ? 3 : 4;
	}
}

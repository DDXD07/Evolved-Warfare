package com.evolvedwarfare.client.render.melee;

import com.evolvedwarfare.weapon.WeaponType;
import net.minecraft.util.math.MathHelper;

/**
 * Las animaciones de ataque de las armas cuerpo a cuerpo (generado por tools/ew_melee.py: no lo edites a mano).
 * Fotogramas clave {progreso, valores...} sobre el progreso del golpe (0..1), con una curva suave entre ellos.
 * <ul>
 *   <li>Primera persona: dx, dy, dz, pitch, yaw, roll, chain (movimiento en bloques en los ejes de la vista, giros en
 *   grados alrededor de la empuñadura; chain: 0 = la cadena cuelga, 1 = estirada).</li>
 *   <li>Tercera persona: arm_pitch, arm_yaw, arm_roll, off_pitch, off_yaw, off_roll, body_yaw, tilt, chain (grados, como ModelPart; off_* es el otro brazo,
 *   solo en las de dos manos y con esa mano libre; tilt: inclinación del arma en la mano, -90 = en la línea del
 *   brazo).</li>
 * </ul>
 */
public final class MeleeAnimations {
	public static final int FP_DX = 0;
	public static final int FP_DY = 1;
	public static final int FP_DZ = 2;
	public static final int FP_PITCH = 3;
	public static final int FP_YAW = 4;
	public static final int FP_ROLL = 5;
	public static final int FP_CHAIN = 6;
	public static final int TP_ARM_PITCH = 0;
	public static final int TP_ARM_YAW = 1;
	public static final int TP_ARM_ROLL = 2;
	public static final int TP_OFF_PITCH = 3;
	public static final int TP_OFF_YAW = 4;
	public static final int TP_OFF_ROLL = 5;
	public static final int TP_BODY_YAW = 6;
	public static final int TP_TILT = 7;
	public static final int TP_CHAIN = 8;

	private static final float[][] DAGGER_FP = {
			{0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.18F, -0.1F, 0.06F, 0.06F, -68F, 10F, 0F, 0F},
			{0.4F, -0.2F, 0.1F, -0.4F, -82F, 12F, 0F, 0F},
			{0.62F, -0.14F, 0.06F, -0.18F, -70F, 8F, 0F, 0F},
			{1F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] DAGGER_TP = {
			{0F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.18F, -40F, 8F, 0F, 0F, 0F, 0F, 12F, -30F, 0F},
			{0.4F, -95F, -6F, 0F, 0F, 0F, 0F, -18F, -88F, 0F},
			{0.62F, -82F, -4F, 0F, 0F, 0F, 0F, -10F, -80F, 0F},
			{1F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] SPEAR_FP = {
			{0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.18F, -0.18F, 0.1F, 0.18F, -80F, 6F, 0F, 0F},
			{0.4F, -0.3F, 0.14F, -0.62F, -86F, 8F, 0F, 0F},
			{0.62F, -0.26F, 0.12F, -0.36F, -84F, 6F, 0F, 0F},
			{1F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] SPEAR_TP = {
			{0F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.18F, -48F, 14F, 0F, -58F, 38F, 0F, 18F, -40F, 0F},
			{0.4F, -88F, -4F, 0F, -84F, 30F, 0F, -22F, -80F, 0F},
			{0.62F, -80F, 0F, 0F, -76F, 30F, 0F, -12F, -78F, 0F},
			{1F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] LONGSWORD_FP = {
			{0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.12F, 0.08F, 0.14F, 0.04F, 0F, -25F, -80F, 0F},
			{0.42F, -0.55F, 0.04F, -0.16F, 0F, 150F, -95F, 0F},
			{0.58F, -0.62F, -0.02F, -0.06F, 0F, 168F, -100F, 0F},
			{1F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] LONGSWORD_TP = {
			{0F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.12F, -88F, 55F, 0F, -80F, 62F, 0F, 28F, -80F, 0F},
			{0.42F, -80F, -55F, 0F, -78F, -40F, 0F, -30F, -82F, 0F},
			{0.58F, -62F, -48F, 0F, -60F, -34F, 0F, -24F, -78F, 0F},
			{1F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] HALBERD_FP = {
			{0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.16F, 0.04F, 0.3F, 0.04F, 5F, 0F, -15F, 0F},
			{0.4F, -0.25F, -0.08F, -0.35F, -115F, 0F, 5F, 0F},
			{0.56F, -0.25F, -0.12F, -0.3F, -118F, 0F, 4F, 0F},
			{1F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] HALBERD_TP = {
			{0F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.16F, -170F, 8F, 0F, -160F, -18F, 0F, 6F, 30F, 0F},
			{0.4F, -72F, -6F, 0F, -66F, 24F, 0F, -8F, -95F, 0F},
			{0.56F, -62F, -6F, 0F, -56F, 24F, 0F, -6F, -90F, 0F},
			{1F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] WARHAMMER_FP = {
			{0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.2F, 0.05F, 0.34F, 0.06F, 8F, 0F, -12F, 0F},
			{0.36F, -0.28F, -0.12F, -0.28F, -108F, 0F, 0F, 0F},
			{0.44F, -0.28F, -0.06F, -0.28F, -98F, 0F, 0F, 0F},
			{0.52F, -0.28F, -0.12F, -0.28F, -106F, 0F, 0F, 0F},
			{0.72F, -0.2F, -0.08F, -0.18F, -75F, 0F, 0F, 0F},
			{1F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] WARHAMMER_TP = {
			{0F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.2F, -178F, 6F, 0F, -172F, -14F, 0F, 0F, 35F, 0F},
			{0.36F, -70F, -4F, 0F, -66F, 22F, 0F, -6F, -95F, 0F},
			{0.44F, -78F, -4F, 0F, -74F, 22F, 0F, -6F, -88F, 0F},
			{0.52F, -70F, -4F, 0F, -66F, 22F, 0F, -6F, -95F, 0F},
			{0.72F, -50F, -2F, 0F, -46F, 16F, 0F, -4F, -70F, 0F},
			{1F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] FLAIL_FP = {
			{0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.15F, 0.06F, 0.25F, 0.06F, 10F, 0F, -25F, 0.7F},
			{0.4F, -0.35F, -0.1F, -0.25F, -90F, 0F, 35F, 1F},
			{0.6F, -0.3F, -0.12F, -0.2F, -80F, 0F, 30F, 0.6F},
			{1F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] FLAIL_TP = {
			{0F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.15F, -150F, 18F, -20F, 0F, 0F, 0F, 14F, 0F, 0.7F},
			{0.4F, -60F, -32F, 0F, 0F, 0F, 0F, -16F, -60F, 1F},
			{0.6F, -50F, -28F, 0F, 0F, 0F, 0F, -12F, -55F, 0.6F},
			{1F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] BATTLE_AXE_FP = {
			{0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.18F, 0.08F, 0.28F, 0.06F, 10F, 0F, -35F, 0F},
			{0.4F, -0.32F, -0.08F, -0.3F, -105F, 0F, 30F, 0F},
			{0.56F, -0.32F, -0.1F, -0.28F, -110F, 0F, 32F, 0F},
			{1F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};
	private static final float[][] BATTLE_AXE_TP = {
			{0F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0.18F, -165F, 28F, 0F, 0F, 0F, 0F, 16F, 25F, 0F},
			{0.4F, -62F, -36F, 0F, 0F, 0F, 0F, -16F, -85F, 0F},
			{0.56F, -55F, -32F, 0F, 0F, 0F, 0F, -14F, -80F, 0F},
			{1F, -18F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F},
	};

	private MeleeAnimations() {
	}

	public static float[] fp(WeaponType type, float progress) {
		return sample(switch (type) {
			case DAGGER -> DAGGER_FP;
			case SPEAR -> SPEAR_FP;
			case LONGSWORD -> LONGSWORD_FP;
			case HALBERD -> HALBERD_FP;
			case WARHAMMER -> WARHAMMER_FP;
			case FLAIL -> FLAIL_FP;
			case BATTLE_AXE -> BATTLE_AXE_FP;
		}, progress);
	}

	public static float[] tp(WeaponType type, float progress) {
		return sample(switch (type) {
			case DAGGER -> DAGGER_TP;
			case SPEAR -> SPEAR_TP;
			case LONGSWORD -> LONGSWORD_TP;
			case HALBERD -> HALBERD_TP;
			case WARHAMMER -> WARHAMMER_TP;
			case FLAIL -> FLAIL_TP;
			case BATTLE_AXE -> BATTLE_AXE_TP;
		}, progress);
	}

	private static float[] sample(float[][] keys, float progress) {
		float p = MathHelper.clamp(progress, 0.0F, 1.0F);
		int n = keys[0].length - 1;
		float[] out = new float[n];
		for (int i = 0; i < keys.length - 1; i++) {
			float[] a = keys[i];
			float[] b = keys[i + 1];
			if (p <= b[0] || i == keys.length - 2) {
				float t = MathHelper.clamp((p - a[0]) / Math.max(1.0E-6F, b[0] - a[0]), 0.0F, 1.0F);
				t = t * t * (3.0F - 2.0F * t);
				for (int j = 0; j < n; j++) {
					out[j] = MathHelper.lerp(t, a[j + 1], b[j + 1]);
				}
				return out;
			}
		}
		return out;
	}
}

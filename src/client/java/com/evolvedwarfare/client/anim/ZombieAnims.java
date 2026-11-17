package com.evolvedwarfare.client.anim;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

/**
 * Los zombis (fase 86): zombi, zombi del desierto, ahogado y aldeano zombi.
 * <ul>
 * <li><b>Andando</b>: se arrastran: una pierna arrastra, el cuerpo cae hacia delante y se balancea de lado a cada paso,
 * la cabeza va ladeada y colgando, con algún tirón de cuello de vez en cuando, y los brazos tendidos al frente, caídos y
 * descompasados, que bajan algo con cada paso.</li>
 * <li><b>Persiguiendo</b> (cuando te han visto): más echados adelante, con los brazos más altos, buscándote.</li>
 * <li><b>Atacando con las manos</b>: echan los brazos atrás y arriba, abiertos, y se te tiran encima cerrándolos, con el
 * cuerpo lanzado adelante y un mordisco al final.</li>
 * <li><b>Con un arma o herramienta en la mano</b>: la llevan baja, colgando, con el otro brazo tendido; y al atacarte con
 * ella, un golpe torpe: con una espada, un tajo en diagonal; con lo demás, un mazazo de arriba abajo.</li>
 * </ul>
 * Todo pasa poco a poco de una postura a otra, y cada zombi va algo distinto (su propio ritmo).
 */
public final class ZombieAnims {
	private static final class State {
		float lastProgress = -1.0F;
		float chase;
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	private static final float PI = (float) Math.PI;

	// {progreso, brazo pitch, brazo yaw, cuerpo yaw, cuerpo pitch} (radianes): el golpe con arma
	private static final float[][] SMASH = {
			{0F, -0.5F, 0F, 0F, 0F},
			{0.3F, -2.9F, 0.2F, 0.25F, -0.1F},
			{0.55F, -0.4F, -0.3F, -0.3F, 0.32F},
			{0.75F, -0.5F, -0.2F, -0.2F, 0.22F},
			{1F, -0.5F, 0F, 0F, 0F},
	};
	private static final float[][] SLASH = {
			{0F, -0.5F, 0F, 0F, 0F},
			{0.3F, -2.4F, 0.9F, 0.35F, -0.05F},
			{0.55F, -0.9F, -0.7F, -0.35F, 0.25F},
			{0.75F, -0.8F, -0.5F, -0.25F, 0.15F},
			{1F, -0.5F, 0F, 0F, 0F},
	};
	// {progreso, brazos pitch, brazos abiertos (yaw hacia fuera), cuerpo pitch, cabeza pitch}: el ataque con las manos
	private static final float[][] GRAB = {
			{0F, -1.55F, 0F, 0F, 0F},
			{0.32F, -2.35F, 0.38F, -0.12F, -0.15F},
			{0.55F, -1.0F, -0.22F, 0.38F, 0.2F},
			{0.7F, -1.05F, -0.2F, 0.32F, 0.45F},
			{1F, -1.55F, 0F, 0F, 0F},
	};

	private ZombieAnims() {
	}

	private static float approach(float w, float target, float dt, float rate) {
		return w + (target - w) * (1.0F - (float) Math.exp(-dt * rate));
	}

	private static float[] sample(float[][] keys, float t) {
		int n = keys[0].length - 1;
		float[] out = new float[n];
		for (int i = 0; i < keys.length - 1; i++) {
			float[] a = keys[i], b = keys[i + 1];
			if (t <= b[0]) {
				float u = MathHelper.clamp((t - a[0]) / (b[0] - a[0]), 0, 1);
				u = u * u * (3 - 2 * u);
				for (int k = 0; k < n; k++) {
					out[k] = MathHelper.lerp(u, a[k + 1], b[k + 1]);
				}
				return out;
			}
		}
		System.arraycopy(keys[keys.length - 1], 1, out, 0, n);
		return out;
	}

	private static boolean armed(ItemStack stack) {
		return !stack.isEmpty() && (stack.isDamageable() || stack.getItem() instanceof SwordItem);
	}

	public static void apply(BipedEntityModel<?> model, LivingEntity zombie, float limbAngle, float limbDistance, float progress) {
		if (zombie.hasVehicle() || zombie.isDead()) {
			return;
		}
		if (zombie instanceof DrownedEntity && (zombie.isTouchingWater() || zombie.getMainHandStack().getItem() instanceof TridentItem)) {
			return;
		}
		if (STATES.size() > 512) {
			STATES.clear();
		}
		State s = STATES.computeIfAbsent(zombie.getId(), id -> new State());
		float dt = progress - s.lastProgress;
		if (s.lastProgress < 0 || dt < 0 || dt > 20) {
			dt = 20;
		}
		s.lastProgress = progress;
		boolean chasing = zombie instanceof MobEntity mob && mob.isAttacking();
		s.chase = approach(s.chase, chasing ? 1 : 0, Math.min(dt, 20), 0.12F);

		// cada zombi con su ritmo
		float seed = (zombie.getUuid().getLeastSignificantBits() & 1023) / 1023.0F * 2.0F * PI;
		float phase = limbAngle * 0.6662F;
		float amp = Math.min(limbDistance, 1.0F);
		float c = s.chase;

		// --- piernas: una normal (dobla la rodilla al dar el paso, tiesa al pisar) y la otra arrastrando, casi sin doblar
		model.rightLeg.pitch = MathHelper.cos(phase) * 0.9F * amp - 0.05F;
		model.leftLeg.pitch = MathHelper.cos(phase + PI) * 0.45F * amp + 0.12F * amp;
		model.leftLeg.roll = 0.05F * amp;
		model.rightLeg.roll = -0.03F;
		model.leftLeg.yaw = 0.1F * amp;
		Joints.knee(model.rightLeg, Math.max(0.0F, MathHelper.sin(phase)) * 0.95F * amp + 0.1F);
		Joints.knee(model.leftLeg, 0.08F + Math.max(0.0F, MathHelper.sin(phase + PI)) * 0.15F * amp);

		// --- cuerpo: caído hacia delante y balanceándose a cada paso
		float sway = MathHelper.sin(phase) * 0.11F * amp + MathHelper.sin(progress * 0.045F + seed) * 0.04F;
		model.body.pitch = 0.0F;
		model.body.roll = sway;
		model.body.yaw = MathHelper.sin(phase) * 0.08F * amp;
		float lurch = -Math.abs(MathHelper.cos(phase)) * amp * 0.8F;
		model.body.pivotY += lurch;
		model.head.pivotY += lurch + 0.6F * c;
		model.rightArm.pivotY += lurch;
		model.leftArm.pivotY += lurch;
		// caído hacia delante desde la cadera (antes giraba desde el cuello y el tronco se salía de las piernas)
		Joints.leanAtHips(model, 0.14F + 0.16F * c);

		// --- cabeza: ladeada, colgando, con tirones de cuello de vez en cuando
		model.head.roll = 0.14F + MathHelper.sin(progress * 0.03F + seed) * 0.1F + sway * 0.5F;
		model.head.pitch += 0.12F * (1 - c) - 0.1F * c;
		float twitch = (progress + seed * 40.0F) % 90.0F;
		if (twitch < 4.0F) {
			float k = MathHelper.sin(twitch / 4.0F * PI);
			model.head.yaw += k * 0.4F;
			model.head.roll -= k * 0.25F;
		}

		// --- brazos: tendidos, caídos y descompasados (o bajos con el arma)
		Hand swingHand = zombie.preferredHand == null ? Hand.MAIN_HAND : zombie.preferredHand;
		ItemStack main = zombie.getMainHandStack();
		boolean hasWeapon = armed(main);
		Arm mainArm = zombie.getMainArm();
		ModelPart mainPart = mainArm == Arm.RIGHT ? model.rightArm : model.leftArm;
		ModelPart offPart = mainArm == Arm.RIGHT ? model.leftArm : model.rightArm;
		float side = mainArm == Arm.RIGHT ? 1.0F : -1.0F;
		float reach = -1.2F - 0.4F * c;
		for (ModelPart arm : new ModelPart[] {model.rightArm, model.leftArm}) {
			boolean right = arm == model.rightArm;
			float off = right ? 0.0F : 1.7F;
			arm.pitch = reach + MathHelper.sin(progress * 0.06F + seed + off) * 0.12F + MathHelper.sin(phase + (right ? 0 : PI)) * 0.16F * amp;
			arm.yaw = (right ? -1 : 1) * (0.08F + 0.05F * MathHelper.sin(progress * 0.05F + off));
			arm.roll = (right ? 1 : -1) * (0.05F + MathHelper.cos(progress * 0.07F + seed + off) * 0.04F);
			// los antebrazos cuelgan, muertos, y se mecen
			ModelPart fore = Joints.lower(arm);
			if (fore != null) {
				fore.pitch = 0.32F + MathHelper.sin(progress * 0.08F + seed + off) * 0.09F + MathHelper.sin(phase + (right ? 0.6F : PI + 0.6F)) * 0.1F * amp
						- 0.15F * c;
			}
		}
		if (hasWeapon) {
			// el arma colgando de la mano
			mainPart.pitch = -0.5F + MathHelper.sin(phase) * 0.18F * amp + MathHelper.sin(progress * 0.05F + seed) * 0.06F;
			mainPart.yaw = 0.0F;
			mainPart.roll = side * 0.08F;
			Joints.elbow(mainPart, 0.25F);
		}

		// --- el ataque
		float p = model.handSwingProgress;
		if (p > 0.0F) {
			if (hasWeapon && swingHand == Hand.MAIN_HAND) {
				float[] k = sample(main.getItem() instanceof SwordItem || com.evolvedwarfare.weapon.MedievalWeapons.typeOf(main) != null ? SLASH : SMASH, p);
				mainPart.pitch = k[0];
				mainPart.yaw = k[1] * side;
				mainPart.roll = side * 0.05F;
				model.body.yaw += k[2] * side;
				model.body.pitch += k[3];
				float by = model.body.yaw;
				model.rightArm.pivotZ = MathHelper.sin(by) * 5.0F;
				model.rightArm.pivotX = -MathHelper.cos(by) * 5.0F;
				model.leftArm.pivotZ = -MathHelper.sin(by) * 5.0F;
				model.leftArm.pivotX = MathHelper.cos(by) * 5.0F;
				offPart.pitch = MathHelper.lerp(0.5F, offPart.pitch, -0.6F);
				// el codo: doblado atrás al levantar el arma, estirado al dar
				Joints.elbow(mainPart, p < 0.3F ? p / 0.3F * 1.5F : Math.max(0.0F, 1.5F - (p - 0.3F) / 0.22F * 1.5F) + 0.1F);
			} else {
				float[] k = sample(GRAB, p);
				model.rightArm.pitch = k[0];
				model.leftArm.pitch = k[0] + 0.08F;
				model.rightArm.yaw = -k[1];
				model.leftArm.yaw = k[1];
				model.rightArm.roll = 0.05F;
				model.leftArm.roll = -0.05F;
				// las garras: codos doblados arriba al echarse atrás, estirados al tirarse encima
				float claw = p < 0.32F ? p / 0.32F : Math.max(0.0F, 1.0F - (p - 0.32F) / 0.2F);
				for (ModelPart arm : new ModelPart[] {model.rightArm, model.leftArm}) {
					ModelPart fore = Joints.lower(arm);
					if (fore != null) {
						fore.pitch = MathHelper.lerp(claw, 0.2F, -1.1F);
					}
				}
				model.body.pitch += k[2];
				model.head.pitch += k[3];
				model.head.pivotY += k[2] * 2.0F;
				model.rightArm.pivotY += k[2] * 1.5F;
				model.leftArm.pivotY += k[2] * 1.5F;
			}
		}
		model.hat.copyTransform(model.head);
	}
}

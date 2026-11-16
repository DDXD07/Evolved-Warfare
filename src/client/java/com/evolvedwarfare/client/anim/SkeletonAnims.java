package com.evolvedwarfare.client.anim;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;

/**
 * Los esqueletos (fase 87): esqueleto, de las nieves (stray), wither y del pantano (bogged). Se mueven rígidos, como
 * huesos sin músculos, pero sin tirones:
 * <ul>
 * <li><b>Andar</b>: las piernas van tiesas y se paran un instante en cada extremo del paso (como palancas), el cuerpo no se
 * balancea (solo un traqueteo al pisar), los brazos cuelgan rectos y apenas se mueven, y la cabeza gira a saltitos de
 * vez en cuando y se queda mirando. Todo tiembla un poquito, como huesos sueltos.</li>
 * <li><b>Atacar</b> (sin arco): el brazo, recto como un palo, sube hasta arriba y cae de golpe, con el tronco girando en
 * bloque.</li>
 * <li><b>Cargar el arco</b>: el brazo que tira de la cuerda se va llevando la mano a la cara poco a poco mientras tensa, el
 * tronco se pone de lado y la cabeza se ladea para apuntar; al soltar, vuelve.</li>
 * </ul>
 */
public final class SkeletonAnims {
	private static final float PI = (float) Math.PI;
	// {progreso, brazo pitch, cuerpo yaw}: el golpe (radianes)
	private static final float[][] STRIKE = {
			{0F, -0.4F, 0F},
			{0.35F, -2.95F, 0.22F},
			{0.55F, -0.3F, -0.26F},
			{0.7F, -0.5F, -0.18F},
			{1F, -0.4F, 0F},
	};

	private SkeletonAnims() {
	}

	/** Una onda que se queda un rato arriba y abajo: como una palanca (rígida), pero sin saltos. */
	private static float stiff(float s) {
		return s / MathHelper.sqrt(s * s + 0.12F) * 0.945F;
	}

	private static float hash(int k) {
		int h = k * 0x9E3779B1;
		h ^= h >>> 15;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		return (h & 0xFFFF) / 65535.0F;
	}

	private static float[] sample(float[][] keys, float t) {
		for (int i = 0; i < keys.length - 1; i++) {
			float[] a = keys[i], b = keys[i + 1];
			if (t <= b[0]) {
				float u = MathHelper.clamp((t - a[0]) / (b[0] - a[0]), 0, 1);
				u = u * u * (3 - 2 * u);
				return new float[] {MathHelper.lerp(u, a[1], b[1]), MathHelper.lerp(u, a[2], b[2])};
			}
		}
		return new float[] {keys[keys.length - 1][1], keys[keys.length - 1][2]};
	}

	public static void apply(BipedEntityModel<?> model, MobEntity mob, float limbAngle, float limbDistance, float progress) {
		if (mob.hasVehicle() || mob.isDead()) {
			return;
		}
		int seedInt = (int) (mob.getUuid().getLeastSignificantBits() & 0x7FFFFFFF);
		float seed = (seedInt & 1023) / 1023.0F * 2.0F * PI;
		float phase = limbAngle * 0.6662F;
		float amp = Math.min(limbDistance, 1.0F);
		boolean aiming = model.rightArmPose == BipedEntityModel.ArmPose.BOW_AND_ARROW || model.leftArmPose == BipedEntityModel.ArmPose.BOW_AND_ARROW;
		ItemStack main = mob.getMainHandStack();
		boolean melee = !main.isEmpty() && !(main.getItem() instanceof BowItem) && !aiming;
		float swing = model.handSwingProgress;

		// --- piernas tiesas, como palancas
		model.rightLeg.pitch = stiff(MathHelper.cos(phase)) * 0.85F * amp;
		model.leftLeg.pitch = stiff(MathHelper.cos(phase + PI)) * 0.85F * amp;
		model.rightLeg.roll = 0.0F;
		model.leftLeg.roll = 0.0F;

		// --- el tronco no se balancea: solo el traqueteo al pisar
		float clack = -(float) Math.pow(Math.abs(MathHelper.cos(phase)), 6) * 0.6F * amp;
		model.body.pivotY += clack;
		model.head.pivotY += clack;
		model.rightArm.pivotY += clack;
		model.leftArm.pivotY += clack;
		model.body.yaw = stiff(MathHelper.sin(phase)) * 0.035F * amp;
		model.body.roll = 0.0F;

		// --- la cabeza: un cabeceo seco al andar y giros a saltitos de vez en cuando
		model.head.pitch += stiff(MathHelper.sin(phase * 2.0F)) * 0.04F * amp;
		if (!aiming && swing <= 0.0F) {
			float period = 70.0F + (seedInt % 40);
			float t = progress + seed * 20.0F;
			int k = (int) Math.floor(t / period);
			float local = t - k * period;
			float from = (hash(k - 1 + seedInt) - 0.5F) * 0.7F;
			float to = (hash(k + seedInt) - 0.5F) * 0.7F;
			float u = MathHelper.clamp(local / 5.0F, 0.0F, 1.0F);
			u = u * u * (3 - 2 * u);
			model.head.yaw += MathHelper.lerp(u, from, to) * (1.0F - 0.6F * amp);
			model.head.roll = MathHelper.lerp(u, from, to) * 0.15F;
		}

		// --- brazos rectos que cuelgan y apenas se mueven
		if (!aiming) {
			for (ModelPart arm : new ModelPart[] {model.rightArm, model.leftArm}) {
				boolean right = arm == model.rightArm;
				if (melee && arm == (mob.getMainArm() == Arm.RIGHT ? model.rightArm : model.leftArm)) {
					// el arma por delante, recta
					arm.pitch = -0.4F + stiff(MathHelper.cos(phase + PI)) * 0.12F * amp;
					arm.yaw = 0.0F;
					arm.roll = right ? 0.04F : -0.04F;
					continue;
				}
				arm.pitch = stiff(MathHelper.cos(phase + (right ? PI : 0))) * 0.32F * amp;
				arm.yaw = 0.0F;
				arm.roll = right ? 0.05F : -0.05F;
			}
		}

		// --- el golpe: el brazo recto sube y cae de golpe, con el tronco en bloque
		if (swing > 0.0F && !aiming) {
			Arm arm = mob.getMainArm();
			float side = arm == Arm.RIGHT ? 1.0F : -1.0F;
			ModelPart a = arm == Arm.RIGHT ? model.rightArm : model.leftArm;
			float[] k = sample(STRIKE, swing);
			a.pitch = k[0];
			a.yaw = 0.0F;
			model.body.yaw = k[1] * side;
			float by = model.body.yaw;
			model.rightArm.pivotZ = MathHelper.sin(by) * 5.0F;
			model.rightArm.pivotX = -MathHelper.cos(by) * 5.0F;
			model.leftArm.pivotZ = -MathHelper.sin(by) * 5.0F;
			model.leftArm.pivotX = MathHelper.cos(by) * 5.0F;
		}

		// --- cargando el arco: la mano de la cuerda a la cara, de lado, la cabeza ladeada
		if (aiming) {
			float draw = mob.isUsingItem() ? MathHelper.clamp((mob.getItemUseTime() + (progress - (int) progress)) / 20.0F, 0.0F, 1.0F) : 0.0F;
			draw = draw * draw * (3 - 2 * draw);
			boolean bowRight = model.rightArmPose == BipedEntityModel.ArmPose.BOW_AND_ARROW;
			float side = bowRight ? 1.0F : -1.0F;
			ModelPart bowArm = bowRight ? model.rightArm : model.leftArm;
			ModelPart pull = bowRight ? model.leftArm : model.rightArm;
			pull.yaw += side * 0.38F * draw;
			pull.pitch += 0.12F * draw;
			bowArm.yaw -= side * 0.06F * draw;
			model.body.yaw += side * 0.22F * draw;
			model.head.roll = -side * 0.14F * draw;
		}

		// el traqueteo de los huesos
		float rattle = 0.008F;
		model.head.roll += MathHelper.sin(progress * 7.3F + seed) * rattle;
		model.rightArm.roll += MathHelper.sin(progress * 6.1F + seed * 2) * rattle;
		model.leftArm.roll += MathHelper.sin(progress * 6.7F + seed * 3) * rattle;
		model.hat.copyTransform(model.head);
	}
}

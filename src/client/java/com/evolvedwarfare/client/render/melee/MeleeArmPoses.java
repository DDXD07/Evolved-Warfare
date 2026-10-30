package com.evolvedwarfare.client.render.melee;

import com.evolvedwarfare.weapon.MedievalWeapons;
import com.evolvedwarfare.weapon.MeleeSwing;
import com.evolvedwarfare.weapon.WeaponType;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

/**
 * Tercera persona: los brazos al golpear con un arma cuerpo a cuerpo ({@link MeleeAnimations}). Sirve para jugadores,
 * soldados, bandidos y cualquiera con cuerpo de jugador (lo llama {@code BipedEntityModelMixin} al final de setAngles).
 * Las de dos manos meten también el otro brazo si esa mano va vacía.
 */
public final class MeleeArmPoses {
	private MeleeArmPoses() {
	}

	public static void apply(BipedEntityModel<?> model, LivingEntity entity) {
		float progress = model.handSwingProgress;
		if (progress <= 0.0F) {
			return;
		}
		Hand hand = entity.preferredHand == null ? Hand.MAIN_HAND : entity.preferredHand;
		WeaponType type = MedievalWeapons.typeOf(entity.getStackInHand(hand));
		if (type == null) {
			return;
		}
		Arm arm = hand == Hand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
		float side = arm == Arm.RIGHT ? 1.0F : -1.0F;
		ModelPart main = arm == Arm.RIGHT ? model.rightArm : model.leftArm;
		ModelPart off = arm == Arm.RIGHT ? model.leftArm : model.rightArm;
		float[] tp = MeleeAnimations.tp(type, progress);

		float bodyYaw = rad(tp[MeleeAnimations.TP_BODY_YAW]) * side;
		model.body.yaw = bodyYaw;
		model.rightArm.pivotZ = MathHelper.sin(bodyYaw) * 5.0F;
		model.rightArm.pivotX = -MathHelper.cos(bodyYaw) * 5.0F;
		model.leftArm.pivotZ = -MathHelper.sin(bodyYaw) * 5.0F;
		model.leftArm.pivotX = MathHelper.cos(bodyYaw) * 5.0F;
		// el golpe va hacia donde mira (más abajo si mira al suelo)
		float look = model.head.pitch * MathHelper.sin(progress * MathHelper.PI) * 0.5F;
		float sneak = model.sneaking ? 0.4F : 0.0F;

		main.pitch = rad(tp[MeleeAnimations.TP_ARM_PITCH]) + look + sneak;
		main.yaw = rad(tp[MeleeAnimations.TP_ARM_YAW]) * side + bodyYaw;
		main.roll = rad(tp[MeleeAnimations.TP_ARM_ROLL]) * side;
		Hand other = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
		if (MeleeSwing.twoHanded(type) && entity.getStackInHand(other).isEmpty() && !entity.isUsingItem()) {
			off.pitch = rad(tp[MeleeAnimations.TP_OFF_PITCH]) + look + sneak;
			off.yaw = rad(tp[MeleeAnimations.TP_OFF_YAW]) * side + bodyYaw;
			off.roll = rad(tp[MeleeAnimations.TP_OFF_ROLL]) * side;
		} else {
			off.yaw += bodyYaw;
		}
	}

	private static float rad(float degrees) {
		return degrees * MathHelper.RADIANS_PER_DEGREE;
	}
}

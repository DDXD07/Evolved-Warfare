package com.evolvedwarfare.client.anim;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Cómo se mueve el cuerpo del jugador (fase 82), en tercera persona y para todos los jugadores que se ven:
 * <ul>
 * <li><b>Andar</b>: el cuerpo sube y baja un poco a cada paso, el tronco gira contra las piernas (y la cabeza no) y los
 * brazos se abren algo al balancearse.</li>
 * <li><b>Correr</b>: se inclina hacia delante, con zancadas y braceo más largos y la cabeza al frente.</li>
 * <li><b>Saltar</b>: al subir, los brazos hacia delante y arriba, una rodilla adelante y la otra atrás.</li>
 * <li><b>Caer</b> (de verdad, no un saltito): brazos arriba y abiertos agitándose, piernas sueltas.</li>
 * <li><b>Trepar</b> (escaleras, enredaderas): las manos van subiendo una tras otra y los pies pisan peldaños, al ritmo de
 * lo que sube o baja (quieto en la escalera, quieto).</li>
 * <li><b>Nadar</b>: en el agua sin nadar a crol, se mantiene a flote moviendo los brazos a los lados y pataleando; a crol,
 * patada de piernas.</li>
 * </ul>
 * Todo pasa de una postura a otra poco a poco. Los brazos se dejan como están si sujetan o usan algo de forma especial
 * (arco, escudo...) o si están golpeando; las otras animaciones del mod (armas, cajas, beber...) van encima.
 */
public final class LocomotionPoses {
	private static final class State {
		float lastProgress = -1.0F;
		float run, jump, fall, climb, tread;
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	private static final float PI = (float) Math.PI;

	private LocomotionPoses() {
	}

	private static float approach(float w, float target, float dt, float rate) {
		return w + (target - w) * (1.0F - (float) Math.exp(-dt * rate));
	}

	private static boolean armFree(BipedEntityModel.ArmPose pose) {
		return pose == BipedEntityModel.ArmPose.EMPTY || pose == BipedEntityModel.ArmPose.ITEM;
	}

	private static void lerpPitch(ModelPart p, float target, float w) {
		p.pitch = MathHelper.lerp(w, p.pitch, target);
	}

	private static void lerpRoll(ModelPart p, float target, float w) {
		p.roll = MathHelper.lerp(w, p.roll, target);
	}

	public static void apply(BipedEntityModel<?> model, LivingEntity entity, float limbAngle, float limbDistance, float progress, float headYaw) {
		if (!(entity instanceof PlayerEntity player)) {
			return;
		}
		// la llamada de la mano en primera persona viene a cero: esa no se toca
		if (progress == 0.0F && limbDistance == 0.0F && limbAngle == 0.0F && headYaw == 0.0F) {
			return;
		}
		if (player.hasVehicle() || player.isFallFlying() || player.isSleeping() || player.isSpectator() || player.isUsingRiptide()
				|| player.getPose() == EntityPose.DYING) {
			return;
		}
		if (STATES.size() > 256) {
			STATES.clear();
		}
		State s = STATES.computeIfAbsent(player.getId(), id -> new State());
		float dt = progress - s.lastProgress;
		boolean reset = s.lastProgress < 0 || dt < 0 || dt > 20;
		s.lastProgress = progress;
		if (reset) {
			dt = 20;
		}
		dt = Math.min(dt, 20);

		boolean ground = player.isOnGround();
		double vy = player.getY() - player.prevY;
		boolean water = player.isTouchingWater();
		boolean swimming = player.isInSwimmingPose();
		boolean climbing = player.isClimbing() && !ground && !water;
		boolean sneaking = model.sneaking;

		s.run = approach(s.run, ground && player.isSprinting() && !sneaking ? 1 : 0, dt, 0.25F);
		s.jump = approach(s.jump, !ground && !climbing && !water && vy > 0.02 ? 1 : 0, dt, 0.45F);
		s.fall = approach(s.fall, !ground && !climbing && !water && vy < -0.45 ? 1 : 0, dt, 0.18F);
		s.climb = approach(s.climb, climbing ? 1 : 0, dt, 0.35F);
		s.tread = approach(s.tread, water && !ground && !swimming ? 1 : 0, dt, 0.25F);

		boolean armsFree = armFree(model.rightArmPose) && armFree(model.leftArmPose) && model.handSwingProgress <= 0.0F && !player.isUsingItem();
		float phase = limbAngle * 0.6662F;
		float amp = Math.min(limbDistance, 1.0F);
		float onFoot = (1 - s.jump) * (1 - s.fall) * (1 - s.climb) * (1 - s.tread);

		// --- andar y correr
		if (!swimming && onFoot > 0.01F) {
			float bob = -Math.abs(MathHelper.cos(phase)) * amp * (0.9F + 0.6F * s.run) * onFoot;
			for (ModelPart p : new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm}) {
				p.pivotY += bob;
			}
			float twist = MathHelper.sin(phase) * 0.14F * amp * onFoot;
			model.body.yaw += twist;
			model.head.yaw -= twist * 0.8F;
			if (armsFree) {
				model.rightArm.roll += (0.06F + 0.05F * amp) * onFoot;
				model.leftArm.roll -= (0.06F + 0.05F * amp) * onFoot;
			}
			if (s.run > 0.01F && !sneaking) {
				float r = s.run * onFoot;
				model.body.pitch += 0.32F * r;
				model.body.pivotY += 0.9F * r;
				model.head.pivotY += 1.1F * r;
				model.head.pitch -= 0.22F * r;
				model.rightArm.pivotY += 1.2F * r;
				model.leftArm.pivotY += 1.2F * r;
				model.rightLeg.pivotZ += 2.2F * r;
				model.leftLeg.pivotZ += 2.2F * r;
				// zancada y braceo más largos
				model.rightLeg.pitch += MathHelper.cos(phase) * 0.35F * amp * r;
				model.leftLeg.pitch += MathHelper.cos(phase + PI) * 0.35F * amp * r;
				if (armsFree) {
					model.rightArm.pitch += MathHelper.cos(phase + PI) * 0.45F * amp * r - 0.25F * r;
					model.leftArm.pitch += MathHelper.cos(phase) * 0.45F * amp * r - 0.25F * r;
				}
			}
		}

		// --- saltar
		if (s.jump > 0.01F) {
			float w = s.jump * (1 - s.fall);
			lerpPitch(model.rightLeg, -0.75F, w * 0.8F);
			lerpPitch(model.leftLeg, 0.35F, w * 0.8F);
			if (armsFree) {
				lerpPitch(model.rightArm, -0.9F, w * 0.7F);
				lerpPitch(model.leftArm, -0.6F, w * 0.7F);
				lerpRoll(model.rightArm, 0.25F, w * 0.6F);
				lerpRoll(model.leftArm, -0.25F, w * 0.6F);
			}
		}

		// --- caer
		if (s.fall > 0.01F) {
			float w = s.fall;
			float flail = progress * 0.9F;
			lerpPitch(model.rightLeg, MathHelper.sin(flail) * 0.45F - 0.2F, w);
			lerpPitch(model.leftLeg, MathHelper.sin(flail + PI) * 0.45F - 0.2F, w);
			lerpRoll(model.rightLeg, 0.22F, w);
			lerpRoll(model.leftLeg, -0.22F, w);
			lerpPitch(model.head, model.head.pitch + 0.35F, w * 0.6F);
			if (armsFree) {
				lerpPitch(model.rightArm, -2.5F + MathHelper.sin(flail * 1.3F) * 0.35F, w);
				lerpPitch(model.leftArm, -2.5F + MathHelper.sin(flail * 1.3F + 1.8F) * 0.35F, w);
				lerpRoll(model.rightArm, 0.75F + MathHelper.cos(flail) * 0.2F, w);
				lerpRoll(model.leftArm, -0.75F - MathHelper.cos(flail + 1.0F) * 0.2F, w);
			}
		}

		// --- trepar: el ciclo va con lo que sube (quieto en la escalera, quieto)
		if (s.climb > 0.01F) {
			float w = s.climb;
			float cyc = (float) (player.getY() * 5.0);
			float step = MathHelper.sin(cyc);
			lerpPitch(model.rightLeg, -0.55F + step * 0.5F, w);
			lerpPitch(model.leftLeg, -0.55F - step * 0.5F, w);
			lerpRoll(model.rightLeg, 0.05F, w);
			lerpRoll(model.leftLeg, -0.05F, w);
			model.body.pitch -= 0.08F * w;
			if (armsFree) {
				lerpPitch(model.rightArm, -2.75F - step * 0.45F, w);
				lerpPitch(model.leftArm, -2.75F + step * 0.45F, w);
				lerpRoll(model.rightArm, -0.12F, w);
				lerpRoll(model.leftArm, 0.12F, w);
			}
		}

		// --- mantenerse a flote
		if (s.tread > 0.01F) {
			float w = s.tread;
			float t = progress * 0.28F;
			float bobW = MathHelper.sin(t) * 0.8F * w;
			for (ModelPart p : new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
				p.pivotY += bobW;
			}
			lerpPitch(model.rightLeg, MathHelper.sin(t * 1.6F) * 0.45F, w);
			lerpPitch(model.leftLeg, MathHelper.sin(t * 1.6F + PI) * 0.45F, w);
			if (armsFree) {
				lerpPitch(model.rightArm, -0.35F + MathHelper.cos(t) * 0.2F, w);
				lerpPitch(model.leftArm, -0.35F + MathHelper.cos(t) * 0.2F, w);
				lerpRoll(model.rightArm, 1.0F + MathHelper.sin(t) * 0.4F, w);
				lerpRoll(model.leftArm, -1.0F - MathHelper.sin(t) * 0.4F, w);
			}
		}

		// --- nadar a crol: patada de piernas
		if (swimming && model.leaningPitch > 0.0F) {
			float kick = MathHelper.sin(progress * 0.9F) * 0.3F * model.leaningPitch;
			model.rightLeg.pitch += kick;
			model.leftLeg.pitch -= kick;
		}

		model.hat.copyTransform(model.head);
	}
}

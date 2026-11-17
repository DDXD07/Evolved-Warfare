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
 * <li><b>Agacharse</b>: baja y se levanta poco a poco (no de golpe); agachado anda con cuidado, a pasos cortos, con
 * los brazos un poco adelantados. Si pasa mucho rato agachado (unos 30 segundos), le empiezan a temblar las piernas, cada
 * vez algo más.</li>
 * <li><b>Nadar</b>: en el agua sin nadar a crol, se mantiene a flote moviendo los brazos a los lados y pataleando; a crol,
 * patada de piernas.</li>
 * </ul>
 * Con codos y rodillas (fase 89, {@link Joints}): la pierna que va por el aire dobla la rodilla y la que pisa casi no, el
 * peso pasa de un pie a otro, los codos se doblan al bracear (en ángulo al correr), se inclina desde la cadera, se agacha
 * en cuclillas, encoge las piernas al saltar, sube la escalera rodilla a rodilla y tirando con los codos, y patalea en el
 * agua. Todo pasa de una postura a otra poco a poco. Los brazos se dejan como están si sujetan o usan algo de forma especial
 * (arco, escudo...) o si están golpeando; las otras animaciones del mod (armas, cajas, beber...) van encima.
 */
public final class LocomotionPoses {
	private static final class State {
		float lastProgress = -1.0F;
		float run, jump, fall, climb, tread;
		/** Lo agachado (0 de pie, 1 agachado del todo) y desde cuándo está agachado (para que le tiemblen las piernas). */
		float crouch;
		float crouchSince = -1.0F;
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	private static final float PI = (float) Math.PI;
	/** A partir de cuántos tics agachado empiezan a temblar las piernas (30 segundos). */
	public static final float SHAKE_AFTER = 600.0F;

	private LocomotionPoses() {
	}

	private static float approach(float w, float target, float dt, float rate) {
		return w + (target - w) * (1.0F - (float) Math.exp(-dt * rate));
	}

	/** El temblor va a rachas: más fuerte a ratos. */
	private static float k(float held) {
		return 0.5F + 0.5F * MathHelper.sin(held * 0.05F);
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

		// --- agacharse: el juego pone la postura de golpe; aquí se va pasando poco a poco
		s.crouch = approach(s.crouch, sneaking ? 1 : 0, dt, 0.35F);
		float undo = sneaking ? 1.0F - s.crouch : -s.crouch;
		if (Math.abs(undo) > 0.005F) {
			model.body.pitch -= 0.5F * undo;
			model.body.pivotY -= 3.2F * undo;
			model.head.pivotY -= 4.2F * undo;
			model.rightArm.pivotY -= 3.2F * undo;
			model.leftArm.pivotY -= 3.2F * undo;
			model.rightLeg.pivotZ -= 4.0F * undo;
			model.leftLeg.pivotZ -= 4.0F * undo;
			model.rightLeg.pivotY -= 0.2F * undo;
			model.leftLeg.pivotY -= 0.2F * undo;
			if (armFree(model.rightArmPose) && model.handSwingProgress <= 0.0F) {
				model.rightArm.pitch -= 0.4F * undo;
			}
			if (armFree(model.leftArmPose) && model.handSwingProgress <= 0.0F) {
				model.leftArm.pitch -= 0.4F * undo;
			}
		}
		if (sneaking) {
			if (s.crouchSince < 0 || reset) {
				s.crouchSince = progress;
			}
		} else {
			s.crouchSince = -1.0F;
		}

		boolean armsFree = armFree(model.rightArmPose) && armFree(model.leftArmPose) && model.handSwingProgress <= 0.0F && !player.isUsingItem()
				&& !com.evolvedwarfare.client.render.CrateCarry.carrying(player) && !com.evolvedwarfare.client.thirst.DrinkAnimation.active(player)
				&& com.evolvedwarfare.client.anim.ActivityPoses.activity(player) == com.evolvedwarfare.network.ActivityPayload.NONE;
		float phase = limbAngle * 0.6662F;
		float amp = Math.min(limbDistance, 1.0F);
		float onFoot = (1 - s.jump) * (1 - s.fall) * (1 - s.climb) * (1 - s.tread);
		// rodillas y codos (radianes de doblez): se van mezclando según la postura y se ponen al final
		float kneeR = 0.0F, kneeL = 0.0F, elbowR = 0.0F, elbowL = 0.0F;

		// --- andar y correr
		if (!swimming && onFoot > 0.01F) {
			// la pierna que va por el aire dobla la rodilla; la que pisa, casi recta (y algo más al recibir el peso)
			float swingR = Math.max(0.0F, MathHelper.sin(phase)), swingL = Math.max(0.0F, MathHelper.sin(phase + PI));
			float plantR = Math.max(0.0F, MathHelper.cos(phase)), plantL = Math.max(0.0F, MathHelper.cos(phase + PI));
			float kneeAmp = (1.15F + 0.9F * s.run) * amp;
			kneeR = (swingR * kneeAmp + plantR * 0.12F * amp + 0.04F) * onFoot;
			kneeL = (swingL * kneeAmp + plantL * 0.12F * amp + 0.04F) * onFoot;
			// los codos: algo doblados siempre, más al ir el brazo adelante; corriendo, en ángulo
			elbowR = (0.12F + 0.18F * amp + Math.max(0.0F, -MathHelper.cos(phase + PI)) * 0.35F * amp) * onFoot;
			elbowL = (0.12F + 0.18F * amp + Math.max(0.0F, -MathHelper.cos(phase)) * 0.35F * amp) * onFoot;
			// el peso pasa de un pie al otro: la cadera se va un poco hacia la pierna que pisa
			float shift = MathHelper.sin(phase) * 0.35F * amp * onFoot;
			model.body.pivotX += shift;
			model.head.pivotX += shift;
			model.rightArm.pivotX += shift;
			model.leftArm.pivotX += shift;
			model.body.roll += -MathHelper.sin(phase) * 0.035F * amp * onFoot;
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
				// inclinado desde la cadera, con la cabeza al frente
				Joints.leanAtHips(model, 0.3F * r);
				model.head.pitch -= 0.26F * r;
				elbowR = MathHelper.lerp(r, elbowR, 1.35F + MathHelper.cos(phase + PI) * 0.25F * amp);
				elbowL = MathHelper.lerp(r, elbowL, 1.35F + MathHelper.cos(phase) * 0.25F * amp);
				// zancada y braceo más largos
				model.rightLeg.pitch += MathHelper.cos(phase) * 0.35F * amp * r;
				model.leftLeg.pitch += MathHelper.cos(phase + PI) * 0.35F * amp * r;
				if (armsFree) {
					model.rightArm.pitch += MathHelper.cos(phase + PI) * 0.45F * amp * r - 0.25F * r;
					model.leftArm.pitch += MathHelper.cos(phase) * 0.45F * amp * r - 0.25F * r;
				}
			}
		}

		// --- cojear con una pierna rota (fase 92): esa pierna casi no dobla ni se balancea y el cuerpo se va hacia la sana
		if (!swimming && onFoot > 0.01F && player == net.minecraft.client.MinecraftClient.getInstance().player) {
			boolean brokenR = com.evolvedwarfare.client.health.ClientHealth.legBroken(true);
			boolean brokenL = com.evolvedwarfare.client.health.ClientHealth.legBroken(false);
			if (brokenR != brokenL) {
				float w = onFoot;
				ModelPart bad = brokenR ? model.rightLeg : model.leftLeg;
				bad.pitch *= 1.0F - 0.6F * w;
				bad.roll += (brokenR ? 0.08F : -0.08F) * w;
				if (brokenR) {
					kneeR *= 1.0F - 0.85F * w;
				} else {
					kneeL *= 1.0F - 0.85F * w;
				}
				// al pisar con la mala, el cuerpo cae un poco y se ladea hacia la buena
				float onBad = Math.max(0.0F, MathHelper.cos(brokenR ? phase : phase + PI)) * amp * w;
				float lean = (brokenR ? -1.0F : 1.0F) * (0.06F + 0.1F * onBad);
				model.body.roll += lean;
				model.head.roll -= lean * 0.5F;
				for (ModelPart p : new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm}) {
					p.pivotY += 1.2F * onBad;
				}
			} else if (brokenR) {
				// las dos rotas: pasitos rígidos y arrastrados
				model.rightLeg.pitch *= 1.0F - 0.6F * onFoot;
				model.leftLeg.pitch *= 1.0F - 0.6F * onFoot;
				kneeR *= 0.3F;
				kneeL *= 0.3F;
				Joints.leanAtHips(model, 0.18F * onFoot);
			}
		}

		// --- agachado: pasos cortos y con cuidado; y, con el rato, el temblor de las piernas
		if (sneaking && !swimming && onFoot > 0.01F) {
			float c = s.crouch * onFoot;
			model.rightLeg.pitch *= 1.0F - 0.35F * c;
			model.leftLeg.pitch *= 1.0F - 0.35F * c;
			// en cuclillas: los muslos adelante y las rodillas dobladas
			model.rightLeg.pitch -= 0.3F * c;
			model.leftLeg.pitch -= 0.3F * c;
			kneeR = MathHelper.lerp(c, kneeR, 0.62F + kneeR * 0.5F);
			kneeL = MathHelper.lerp(c, kneeL, 0.62F + kneeL * 0.5F);
			elbowR = MathHelper.lerp(c, elbowR, 0.55F);
			elbowL = MathHelper.lerp(c, elbowL, 0.55F);
			if (armsFree) {
				model.rightArm.pitch -= 0.15F * c;
				model.leftArm.pitch -= 0.15F * c;
				model.rightArm.pitch *= 1.0F - 0.3F * c * amp;
				model.leftArm.pitch *= 1.0F - 0.3F * c * amp;
			}
			float held = s.crouchSince < 0 ? 0 : progress - s.crouchSince;
			if (held > SHAKE_AFTER) {
				float shake = Math.min((held - SHAKE_AFTER) / 400.0F, 1.0F) * (0.6F + 0.4F * k(held)) * c;
				float a = MathHelper.sin(progress * 2.9F) * 0.6F + MathHelper.sin(progress * 4.7F + 1.3F) * 0.4F;
				float b = MathHelper.sin(progress * 3.3F + 2.1F) * 0.6F + MathHelper.sin(progress * 5.1F) * 0.4F;
				model.rightLeg.pitch += a * 0.035F * shake;
				model.leftLeg.pitch += b * 0.035F * shake;
				model.rightLeg.roll += b * 0.02F * shake;
				model.leftLeg.roll -= a * 0.02F * shake;
				// las rodillas son lo que más tiembla
				kneeR += b * 0.06F * shake;
				kneeL += a * 0.06F * shake;
				float jitter = (a + b) * 0.12F * shake;
				for (ModelPart p : new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm}) {
					p.pivotY += jitter;
				}
			}
		}

		// --- saltar
		if (s.jump > 0.01F) {
			float w = s.jump * (1 - s.fall);
			lerpPitch(model.rightLeg, -0.75F, w * 0.8F);
			lerpPitch(model.leftLeg, 0.35F, w * 0.8F);
			kneeR = MathHelper.lerp(w, kneeR, 1.1F);
			kneeL = MathHelper.lerp(w, kneeL, 0.55F);
			elbowR = MathHelper.lerp(w, elbowR, 0.7F);
			elbowL = MathHelper.lerp(w, elbowL, 0.6F);
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
			kneeR = MathHelper.lerp(w, kneeR, 0.45F + MathHelper.sin(flail + 0.8F) * 0.3F);
			kneeL = MathHelper.lerp(w, kneeL, 0.45F + MathHelper.sin(flail + PI + 0.8F) * 0.3F);
			elbowR = MathHelper.lerp(w, elbowR, 0.45F + MathHelper.sin(flail * 1.3F + 0.6F) * 0.35F);
			elbowL = MathHelper.lerp(w, elbowL, 0.45F + MathHelper.sin(flail * 1.3F + 2.4F) * 0.35F);
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
			// el pie que sube dobla la rodilla; la mano de abajo tira con el codo doblado
			kneeR = MathHelper.lerp(w, kneeR, 0.5F + Math.max(0.0F, -step) * 1.0F);
			kneeL = MathHelper.lerp(w, kneeL, 0.5F + Math.max(0.0F, step) * 1.0F);
			elbowR = MathHelper.lerp(w, elbowR, 0.25F + Math.max(0.0F, step) * 1.1F);
			elbowL = MathHelper.lerp(w, elbowL, 0.25F + Math.max(0.0F, -step) * 1.1F);
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
			kneeR = MathHelper.lerp(w, kneeR, 0.5F + MathHelper.sin(t * 1.6F + 1.2F) * 0.4F);
			kneeL = MathHelper.lerp(w, kneeL, 0.5F + MathHelper.sin(t * 1.6F + PI + 1.2F) * 0.4F);
			elbowR = MathHelper.lerp(w, elbowR, 0.7F + MathHelper.cos(t) * 0.3F);
			elbowL = MathHelper.lerp(w, elbowL, 0.7F + MathHelper.cos(t) * 0.3F);
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
			kneeR = 0.15F + Math.max(0.0F, kick) * 0.8F;
			kneeL = 0.15F + Math.max(0.0F, -kick) * 0.8F;
		}

		if (!player.hasVehicle()) {
			Joints.knee(model.rightLeg, kneeR);
			Joints.knee(model.leftLeg, kneeL);
		}
		if (armsFree) {
			Joints.elbow(model.rightArm, elbowR);
			Joints.elbow(model.leftArm, elbowL);
		}
		model.hat.copyTransform(model.head);
	}
}

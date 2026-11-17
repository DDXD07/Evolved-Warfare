package com.evolvedwarfare.client.anim;

import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Los creepers (fase 88):
 * <ul>
 * <li><b>Andando</b>: un trotecillo a pasos cortos con las patas en diagonal, que se abren un poco al levantarse; el
 * cuerpo da botecitos y se mece de lado, y la cabeza va un pelín por detrás del cuerpo.</li>
 * <li><b>Quieto</b>: respira (se hincha y deshincha muy poco) y de vez en cuando gira la cabeza despacio a mirar a otro
 * lado, ladeándola con curiosidad.</li>
 * <li><b>A punto de explotar</b>: se agacha y se planta, con las patas de delante adelantadas y las de detrás atrás y
 * abiertas, baja la cabeza para mirarte, y tiembla cada vez más hasta reventar.</li>
 * </ul>
 */
public final class CreeperAnims {
	private static final float PI = (float) Math.PI;

	private CreeperAnims() {
	}

	private static float hash(int k) {
		int h = k * 0x9E3779B1;
		h ^= h >>> 15;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		return (h & 0xFFFF) / 65535.0F;
	}

	public static void apply(ModelPart root, ModelPart head, ModelPart rightHind, ModelPart leftHind, ModelPart rightFront, ModelPart leftFront,
			Entity entity, float limbAngle, float limbDistance, float progress) {
		if (!(entity instanceof CreeperEntity creeper) || !root.hasChild("body")) {
			return;
		}
		ModelPart body = root.getChild("body");
		int seedInt = (int) (creeper.getUuid().getLeastSignificantBits() & 0x7FFFFFFF);
		float seed = (seedInt & 1023) / 1023.0F * 2.0F * PI;
		float phase = limbAngle * 0.6662F * 1.25F;
		float amp = Math.min(limbDistance, 1.0F);
		float fuse = MathHelper.clamp(creeper.getClientFuseTime(progress - (int) progress), 0.0F, 1.0F);
		float idle = 1.0F - amp;

		// --- el cuerpo: botecitos y vaivén de lado al andar; respira quieto
		float bob = -Math.abs(MathHelper.sin(phase)) * 0.7F * amp;
		float sway = MathHelper.sin(phase) * 0.45F * amp;
		float breathe = MathHelper.sin(progress * 0.08F + seed) * 0.018F * idle;
		// a punto de explotar: se agacha y tiembla, cada vez más
		float shake = fuse * fuse;
		float jx = (MathHelper.sin(progress * 9.1F + seed) * 0.6F + MathHelper.sin(progress * 13.7F) * 0.4F) * 0.45F * shake;
		float jz = (MathHelper.sin(progress * 11.3F + seed * 2) * 0.6F + MathHelper.sin(progress * 7.9F) * 0.4F) * 0.45F * shake;
		float crouch = 1.2F * fuse;

		body.pivotX = sway + jx;
		body.pivotY = 6.0F + bob + crouch;
		body.pivotZ = jz;
		body.xScale = 1.0F + breathe;
		body.zScale = 1.0F + breathe;
		body.yScale = 1.0F - breathe * 0.5F;
		body.pitch = 0.0F;
		body.roll = 0.0F;

		// --- la cabeza: un pelín por detrás del cuerpo, curiosa, y mirando abajo antes de explotar
		head.pivotX = sway * 0.85F + jx * 1.2F;
		head.pivotY = 6.0F + bob * 0.8F + crouch;
		head.pivotZ = jz * 1.2F;
		head.pitch += MathHelper.sin(phase * 2.0F - 0.6F) * 0.06F * amp + 0.3F * fuse;
		head.roll = MathHelper.sin(progress * 0.04F + seed) * 0.08F * idle - sway * 0.06F
				+ MathHelper.sin(progress * 15.0F) * 0.05F * shake;
		// mirar a otro lado de vez en cuando, despacio
		float period = 90.0F + (seedInt % 50);
		float t = progress + seed * 25.0F;
		int k = (int) Math.floor(t / period);
		float local = t - k * period;
		float from = (hash(k - 1 + seedInt) - 0.5F) * 0.9F;
		float to = (hash(k + seedInt) - 0.5F) * 0.9F;
		float u = MathHelper.clamp(local / 18.0F, 0.0F, 1.0F);
		u = u * u * (3 - 2 * u);
		head.yaw += MathHelper.lerp(u, from, to) * idle * (1.0F - fuse);

		// --- las patas: trote en diagonal (se abren un poco al levantarse); plantadas antes de explotar
		float a = 1.1F * amp;
		float rh = MathHelper.cos(phase) * a, lh = MathHelper.cos(phase + PI) * a;
		float rf = MathHelper.cos(phase + PI) * a, lf = MathHelper.cos(phase) * a;
		rightHind.pitch = MathHelper.lerp(fuse, rh, 0.35F);
		leftHind.pitch = MathHelper.lerp(fuse, lh, 0.35F);
		rightFront.pitch = MathHelper.lerp(fuse, rf, -0.35F);
		leftFront.pitch = MathHelper.lerp(fuse, lf, -0.35F);
		rightHind.roll = Math.max(0, -MathHelper.sin(phase)) * 0.12F * amp + 0.22F * fuse;
		leftHind.roll = -Math.max(0, MathHelper.sin(phase)) * 0.12F * amp - 0.22F * fuse;
		rightFront.roll = Math.max(0, MathHelper.sin(phase)) * 0.12F * amp + 0.22F * fuse;
		leftFront.roll = -Math.max(0, -MathHelper.sin(phase)) * 0.12F * amp - 0.22F * fuse;
		for (ModelPart leg : new ModelPart[] {rightHind, leftHind, rightFront, leftFront}) {
			leg.pivotY = 18.0F + bob * 0.3F;
		}
		rightHind.pivotX = -2.0F + jx * 0.3F;
		leftHind.pivotX = 2.0F + jx * 0.3F;
		rightFront.pivotX = -2.0F + jx * 0.3F;
		leftFront.pivotX = 2.0F + jx * 0.3F;
	}
}

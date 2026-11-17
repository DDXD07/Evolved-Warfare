package com.evolvedwarfare.client.anim;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;

/**
 * Codos y rodillas (fase 89): los brazos y las piernas de los cuerpos de jugador (jugadores, zombis, esqueletos, piglins,
 * las armaduras...) se parten en dos mitades: la de arriba (brazo, muslo) y una hija que cuelga del codo o de la rodilla
 * (antebrazo, espinilla) y se puede doblar. La textura se reparte igual que antes (cada mitad toma su trozo), así que se
 * ven igual que siempre con el brazo o la pierna rectos.
 * <p>
 * Para doblar: {@code forearm.pitch} negativo dobla el codo hacia delante (con el brazo colgando); {@code shin.pitch}
 * positivo dobla la rodilla hacia atrás.
 */
public final class Joints {
	public static final String RIGHT_FOREARM = "right_forearm", LEFT_FOREARM = "left_forearm", RIGHT_SHIN = "right_shin", LEFT_SHIN = "left_shin";

	private Joints() {
	}

	/** Parte un brazo o una pierna (con su textura de caja en u, v) en dos mitades unidas por la articulación. */
	public static void split(ModelPartData root, String name, String lower, int u, int v, boolean mirror, float x, float y, float z, float sx, float sy,
			float sz, Dilation d, ModelTransform pivot) {
		int h = (int) (sy / 2);
		ModelPartData part = root.addChild(name, ModelPartBuilder.create().uv(u, v).mirrored(mirror).cuboid(x, y, z, sx, h, sz, d), pivot);
		part.addChild(lower, ModelPartBuilder.create().uv(u, v + h).mirrored(mirror).cuboid(x, 0.0F, z, sx, sy - h, sz, d),
				ModelTransform.pivot(0.0F, y + h, 0.0F));
	}

	/** Los cuatro miembros de un cuerpo de jugador corriente (como BipedEntityModel.getModelData). */
	public static void splitBiped(ModelData data, Dilation d, float offsetY) {
		ModelPartData root = data.getRoot();
		split(root, "right_arm", RIGHT_FOREARM, 40, 16, false, -3, -2, -2, 4, 12, 4, d, ModelTransform.pivot(-5.0F, 2.0F + offsetY, 0.0F));
		split(root, "left_arm", LEFT_FOREARM, 40, 16, true, -1, -2, -2, 4, 12, 4, d, ModelTransform.pivot(5.0F, 2.0F + offsetY, 0.0F));
		split(root, "right_leg", RIGHT_SHIN, 0, 16, false, -2, 0, -2, 4, 12, 4, d, ModelTransform.pivot(-1.9F, 12.0F + offsetY, 0.0F));
		split(root, "left_leg", LEFT_SHIN, 0, 16, true, -2, 0, -2, 4, 12, 4, d, ModelTransform.pivot(1.9F, 12.0F + offsetY, 0.0F));
	}

	@Nullable
	public static ModelPart lower(ModelPart limb) {
		if (limb.hasChild(RIGHT_FOREARM)) {
			return limb.getChild(RIGHT_FOREARM);
		}
		if (limb.hasChild(LEFT_FOREARM)) {
			return limb.getChild(LEFT_FOREARM);
		}
		if (limb.hasChild(RIGHT_SHIN)) {
			return limb.getChild(RIGHT_SHIN);
		}
		if (limb.hasChild(LEFT_SHIN)) {
			return limb.getChild(LEFT_SHIN);
		}
		return null;
	}

	/** Dobla el codo (hacia delante, en radianes; 0 = recto). */
	public static void elbow(ModelPart arm, float bend) {
		ModelPart f = lower(arm);
		if (f != null) {
			f.pitch = -bend;
		}
	}

	/** Dobla el codo hacia dentro, de lado (para tensar un arco con el brazo al frente). */
	public static void elbowSide(ModelPart arm, float roll) {
		ModelPart f = lower(arm);
		if (f != null) {
			f.roll = roll;
		}
	}

	/** Dobla la rodilla (hacia atrás, en radianes; 0 = recta). */
	public static void knee(ModelPart leg, float bend) {
		ModelPart s = lower(leg);
		if (s != null) {
			s.pitch = Math.max(0.0F, bend);
		}
	}

	public static float elbowOf(ModelPart arm) {
		ModelPart f = lower(arm);
		return f == null ? 0.0F : -f.pitch;
	}

	/** Todo recto (al empezar cada fotograma). */
	public static void reset(BipedEntityModel<?> model) {
		for (ModelPart p : new ModelPart[] {model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
			ModelPart l = lower(p);
			if (l != null) {
				l.resetTransform();
			}
		}
	}

	/** Copia los codos y rodillas a otro cuerpo (la armadura, las mangas...). */
	public static void copy(ModelPart from, ModelPart to) {
		ModelPart a = lower(from), b = lower(to);
		if (a != null && b != null) {
			b.copyTransform(a);
		}
	}

	public static void copyAll(BipedEntityModel<?> from, BipedEntityModel<?> to) {
		copy(from.rightArm, to.rightArm);
		copy(from.leftArm, to.leftArm);
		copy(from.rightLeg, to.rightLeg);
		copy(from.leftLeg, to.leftLeg);
	}

	/** Lo que se lleva en la mano sigue al antebrazo (después de girar el brazo). */
	public static void followForearm(ModelPart arm, MatrixStack matrices) {
		ModelPart f = lower(arm);
		if (f != null) {
			f.rotate(matrices);
			matrices.translate(-f.pivotX / 16.0F, -f.pivotY / 16.0F, -f.pivotZ / 16.0F);
		}
	}

	/**
	 * Inclina el tronco desde la cadera (no desde el cuello): el tronco gira y la cabeza y los hombros se van adelante y
	 * abajo, y las piernas se quedan donde están.
	 */
	public static void leanAtHips(BipedEntityModel<?> model, float angle) {
		if (Math.abs(angle) < 1e-4F) {
			return;
		}
		float s = (float) Math.sin(angle), c = (float) Math.cos(angle);
		float dz = -12.0F * s, dy = 12.0F * (1.0F - c);
		model.body.pitch += angle;
		model.body.pivotZ += dz;
		model.body.pivotY += dy;
		model.head.pivotZ += dz;
		model.head.pivotY += dy;
		float az = -10.0F * s, ay = 10.0F * (1.0F - c);
		model.rightArm.pivotZ += az;
		model.rightArm.pivotY += ay;
		model.leftArm.pivotZ += az;
		model.leftArm.pivotY += ay;
	}
}

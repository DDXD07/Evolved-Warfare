package com.evolvedwarfare.client.render.melee;

import com.evolvedwarfare.client.render.musket.MusketItemRenderer;
import com.evolvedwarfare.weapon.WeaponType;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Dibuja las armas cuerpo a cuerpo en 3D ({@link MeleeModels}) y anima su golpe ({@link MeleeAnimations}); en el
 * inventario se ve el icono plano de siempre. Las cuentas son las mismas que tools/ew_melee_preview.py.
 *
 * <ul>
 *   <li>Primera persona: se deshace el golpe de vanilla (que mueve todos los objetos igual) y se pone el de cada arma,
 *   alrededor de la empuñadura.</li>
 *   <li>Tercera persona: los brazos los mueve {@code BipedEntityModelMixin}; aquí solo se inclina el arma en la mano
 *   (para que la lanza y la daga apunten al frente al dar la estocada).</li>
 *   <li>El mangual: la cadena cuelga hacia abajo (de verdad, según cómo esté el arma) y se estira al voltear la bola.</li>
 * </ul>
 */
public class MeleeWeaponRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	// lo mismo que el modelo de objeto (minecraft:item/handheld): primera y tercera persona, mano derecha
	private static final float[] FP_TRANSLATION = {1.13F / 16.0F, 3.2F / 16.0F, 1.13F / 16.0F};
	private static final float[] FP_ROTATION = {0.0F, -90.0F, 25.0F};
	private static final float FP_SCALE = 0.68F;
	private static final float[] TP_TRANSLATION = {0.0F, 4.0F / 16.0F, 0.5F / 16.0F};
	private static final float[] TP_ROTATION = {0.0F, -90.0F, 55.0F};
	private static final float TP_SCALE = 0.85F;

	private final WeaponType type;
	private final Identifier texture;
	private final Identifier icon;
	@Nullable
	private ModelPart weapon;
	@Nullable
	private ModelPart chain;

	public MeleeWeaponRenderer(WeaponType type, String material, Identifier icon) {
		this.type = type;
		this.texture = MeleeModels.texture(material);
		this.icon = icon;
	}

	private void parts() {
		if (weapon == null) {
			ModelPart root = MinecraftClient.getInstance().getEntityModelLoader().getModelPart(MeleeModels.layer(type));
			weapon = root.getChild("weapon");
			chain = root.hasChild("chain") ? root.getChild("chain") : null;
		}
	}

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, int overlay) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (mode == ModelTransformationMode.GUI) {
			// el icono plano (el modelo 3D se queda pequeño y en diagonal en un hueco del inventario)
			BakedModel model = client.getBakedModelManager().getModel(icon);
			matrices.push();
			matrices.translate(0.5F, 0.5F, 0.5F);
			client.getItemRenderer().renderItem(stack, mode, false, matrices, vertexConsumers, light, overlay, model);
			matrices.pop();
			return;
		}
		parts();
		float tickDelta = client.getRenderTickCounter().getTickDelta(true);
		boolean firstPerson = mode.isFirstPerson();
		boolean thirdPerson = mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
		boolean left = mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND;
		LivingEntity holder = firstPerson || thirdPerson ? MusketItemRenderer.holder : null;
		float side = left ? -1.0F : 1.0F;
		float progress = holder != null ? swingProgress(holder, left ? Arm.LEFT : Arm.RIGHT, tickDelta) : 0.0F;
		float stretch = 0.0F;
		Vector3f down = new Vector3f(0.0F, -1.0F, 0.0F);

		matrices.push();
		if (firstPerson) {
			float[] fp = MeleeAnimations.fp(type, progress);
			matrices.translate(0.5F, 0.5F, 0.5F);
			undoDisplay(matrices, FP_TRANSLATION, FP_ROTATION, FP_SCALE, side);
			undoVanillaSwing(matrices, progress, side);
			Vector3f grip = gripInHand(FP_TRANSLATION, FP_ROTATION, FP_SCALE, side);
			matrices.translate(grip.x, grip.y, grip.z);
			matrices.translate(side * fp[MeleeAnimations.FP_DX], fp[MeleeAnimations.FP_DY], fp[MeleeAnimations.FP_DZ]);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * fp[MeleeAnimations.FP_YAW]));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(fp[MeleeAnimations.FP_PITCH]));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * fp[MeleeAnimations.FP_ROLL]));
			matrices.translate(-grip.x, -grip.y, -grip.z);
			applyDisplay(matrices, FP_TRANSLATION, FP_ROTATION, FP_SCALE, side);
			matrices.translate(-0.5F, -0.5F, -0.5F);
			stretch = fp[MeleeAnimations.FP_CHAIN];
			// la vista no está alineada con el mundo: el "abajo" de verdad depende de hacia dónde mira el jugador
			if (holder != null) {
				float pitch = holder.getPitch(tickDelta) * MathHelper.RADIANS_PER_DEGREE;
				down.set(0.0F, -MathHelper.cos(pitch), -MathHelper.sin(pitch));
			}
		} else if (thirdPerson && progress > 0.0F) {
			float[] tp = MeleeAnimations.tp(type, progress);
			matrices.translate(0.5F, 0.5F, 0.5F);
			undoDisplay(matrices, TP_TRANSLATION, TP_ROTATION, TP_SCALE, side);
			Vector3f grip = gripInHand(TP_TRANSLATION, TP_ROTATION, TP_SCALE, side);
			matrices.translate(grip.x, grip.y, grip.z);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tp[MeleeAnimations.TP_TILT]));
			matrices.translate(-grip.x, -grip.y, -grip.z);
			applyDisplay(matrices, TP_TRANSLATION, TP_ROTATION, TP_SCALE, side);
			matrices.translate(-0.5F, -0.5F, -0.5F);
			stretch = tp[MeleeAnimations.TP_CHAIN];
		}

		VertexConsumer vertices = ItemRenderer.getDirectItemGlintConsumer(vertexConsumers, RenderLayer.getEntityCutout(texture), false,
				stack.hasGlint());
		weapon.render(matrices, vertices, light, overlay);
		if (chain != null) {
			renderChain(matrices, vertices, light, overlay, down, stretch);
		}
		matrices.pop();
	}

	/** Progreso del golpe de la mano en la que va el arma (0 si golpea la otra), como lo calcula vanilla. */
	private static float swingProgress(LivingEntity holder, Arm arm, float tickDelta) {
		Hand hand = arm == holder.getMainArm() ? Hand.MAIN_HAND : Hand.OFF_HAND;
		Hand swinging = holder.preferredHand == null ? Hand.MAIN_HAND : holder.preferredHand;
		return hand == swinging ? holder.getHandSwingProgress(tickDelta) : 0.0F;
	}

	// ------------------------------------------------------------------ cuentas de las matrices

	private static Quaternionf displayRotation(float[] rotation, float side) {
		return new Quaternionf().rotationXYZ(rotation[0] * MathHelper.RADIANS_PER_DEGREE, side * rotation[1] * MathHelper.RADIANS_PER_DEGREE,
				side * rotation[2] * MathHelper.RADIANS_PER_DEGREE);
	}

	/** Lo que hace el modelo de objeto (Transformation.apply), con la mano izquierda en espejo. */
	private static void applyDisplay(MatrixStack matrices, float[] translation, float[] rotation, float scale, float side) {
		matrices.translate(side * translation[0], translation[1], translation[2]);
		matrices.multiply(displayRotation(rotation, side));
		matrices.scale(scale, scale, scale);
	}

	private static void undoDisplay(MatrixStack matrices, float[] translation, float[] rotation, float scale, float side) {
		matrices.scale(1.0F / scale, 1.0F / scale, 1.0F / scale);
		matrices.multiply(displayRotation(rotation, side).conjugate());
		matrices.translate(-side * translation[0], -translation[1], -translation[2]);
	}

	/** Dónde queda la empuñadura en el marco de la mano (antes del modelo de objeto). */
	private static Vector3f gripInHand(float[] translation, float[] rotation, float scale, float side) {
		Matrix4f display = new Matrix4f().translation(side * translation[0], translation[1], translation[2])
				.rotate(displayRotation(rotation, side)).scale(scale);
		return display.transformPosition(new Vector3f(MeleeModels.GRIP_X / 16.0F - 0.5F, MeleeModels.GRIP_Y / 16.0F - 0.5F,
				MeleeModels.GRIP_Z / 16.0F - 0.5F));
	}

	/**
	 * Deshace el golpe de vanilla en primera persona (HeldItemRenderer: un desplazamiento antes de la mano y unos giros
	 * después). Los desplazamientos se pueden cambiar de orden con el de la mano, así que basta con deshacer los giros
	 * y luego el desplazamiento.
	 */
	private static void undoVanillaSwing(MatrixStack matrices, float swing, float side) {
		if (swing <= 0.0F) {
			return;
		}
		float f = MathHelper.sin(swing * swing * MathHelper.PI);
		float g = MathHelper.sin(MathHelper.sqrt(swing) * MathHelper.PI);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * 80.0F));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * g * 20.0F));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side * (45.0F - f * 20.0F)));
		float n = -0.4F * MathHelper.sin(MathHelper.sqrt(swing) * MathHelper.PI);
		float m = 0.2F * MathHelper.sin(MathHelper.sqrt(swing) * MathHelper.TAU);
		float z = -0.2F * MathHelper.sin(swing * MathHelper.PI);
		matrices.translate(-side * n, -m, -z);
	}

	/** La cadena y la bola del mangual: cuelgan hacia "abajo" y se estiran siguiendo el mango al voltearla. */
	private void renderChain(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, Vector3f spaceDown, float stretch) {
		matrices.push();
		weapon.rotate(matrices);
		matrices.translate(0.0F, MeleeModels.CHAIN_ANCHOR / 16.0F, 0.0F);
		Matrix3f toLocal = new Matrix3f();
		matrices.peek().getPositionMatrix().get3x3(toLocal);
		Vector3f down = new Vector3f(spaceDown);
		if (Math.abs(toLocal.determinant()) > 1.0E-9F) {
			toLocal.invert().transform(down);
		}
		if (down.lengthSquared() < 1.0E-9F) {
			down.set(0.0F, -1.0F, 0.0F);
		}
		down.normalize();
		Vector3f dir = new Vector3f(down).mul(1.0F - stretch).add(0.0F, stretch, 0.0F);
		if (dir.lengthSquared() < 1.0E-6F) {
			dir.set(down);
		}
		dir.normalize();
		matrices.multiply(new Quaternionf().rotationTo(0.0F, -1.0F, 0.0F, dir.x, dir.y, dir.z));
		chain.render(matrices, vertices, light, overlay);
		matrices.pop();
	}
}

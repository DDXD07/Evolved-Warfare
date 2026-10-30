package com.evolvedwarfare.client.render.musket;

import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.weapon.MusketItem;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

/**
 * Dibuja el mosquete en 3D y lo anima (las mismas cuentas que tools/ew_musket_preview.py):
 * <ul>
 *   <li>Apuntando (clic derecho), en primera persona: el arma va al centro con el punto de mira en la cruz.</li>
 *   <li>Sin apuntar, en tercera persona: lo lleva bajado, con la boca un poco hacia el suelo.</li>
 *   <li>Recarga (2 s): alza el arma con la boca hacia arriba, cae la bala por el cañón, la baqueta la aprieta dos
 *   veces, baja el arma, se cierra la cazoleta y sube el rastrillo.</li>
 *   <li>Disparo: el rastrillo cae contra el acero (que se abre) y el arma da un golpe hacia arriba y atrás.</li>
 * </ul>
 * En primera persona el movimiento es más amplio que en tercera, donde lo acompañan los brazos.
 */
public class MusketItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	/** Quién lleva el mosquete que se dibuja ahora (lo pone un mixin en HeldItemRenderer; null en inventario o suelo). */
	@Nullable
	public static LivingEntity holder;

	// puntos del modelo (píxeles)
	private static final float GRIP_X = 8.0F, GRIP_Y = 8.6F, GRIP_Z = 15.0F;
	private static final float BORE_X = 8.0F, BORE_Y = 9.8F, MUZZLE_Z = -8.0F;
	private static final float ROD_HEAD_X = 8.0F, ROD_HEAD_Y = 7.65F, ROD_HEAD_Z = -7.3F;
	// ángulos (grados)
	private static final float HAMMER_DOWN = -30.0F, HAMMER_COCKED = 25.0F;
	private static final float FRIZZEN_OPEN = -55.0F, FRIZZEN_CLOSED = 0.0F;
	private static final float RELOAD_TILT_FP = 60.0F, RELOAD_YAW_FP = 12.0F, RELOAD_TILT_TP = 95.0F;
	private static final float TP_IDLE_TILT = 70.0F;
	/** Lo que tarda en echárselo a la cara (ticks). */
	private static final float AIM_TICKS = 4.0F;
	// apuntar: el punto de mira (píxeles) queda en el centro de la pantalla, a ADS_Z bloques del ojo
	private static final float SIGHT_X = 8.0F / 16, SIGHT_Y = 10.9F / 16, SIGHT_Z = -7.2F / 16;
	private static final float ADS_Y = -0.004F, ADS_Z = -1.15F;
	// lo que hacen vanilla y musket.json en primera persona (mano derecha): desplazamiento de la mano y del modelo
	private static final float EQUIP_X = 0.56F, EQUIP_Y = -0.52F, EQUIP_Z = -0.72F;
	private static final float FP_X = -1.5F / 16, FP_Y = 2.5F / 16, FP_Z = -3.0F / 16, FP_YAW = 4.0F, FP_SCALE = 0.6F;

	@Nullable
	private ModelPart body;
	private ModelPart stock;
	private ModelPart ramrod;
	private ModelPart hammer;
	private ModelPart frizzen;
	private ModelPart ball;

	/** Cómo está el arma en este fotograma. */
	private static final class Pose {
		float tilt;
		float yaw;
		float back;
		float hammer;
		float frizzen;
		/** Desplazamiento de la bala a lo largo del cañón (píxeles), o NaN si no se ve. */
		float ball = Float.NaN;
		/** Cuánto ha entrado la baqueta en el cañón (píxeles), o NaN si está en su sitio. */
		float rod = Float.NaN;
		boolean hideRod;
	}

	private void parts() {
		if (body == null) {
			ModelPart root = MinecraftClient.getInstance().getEntityModelLoader().getModelPart(MusketModel.LAYER);
			body = root.getChild("body");
			stock = root.getChild("stock");
			ramrod = root.getChild("ramrod");
			hammer = root.getChild("hammer");
			frizzen = root.getChild("frizzen");
			ball = root.getChild("ball");
		}
	}

	@Override
	public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, int overlay) {
		parts();
		MinecraftClient client = MinecraftClient.getInstance();
		float tickDelta = client.getRenderTickCounter().getTickDelta(true);
		boolean firstPerson = mode.isFirstPerson();
		boolean inHand = firstPerson || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND
				|| mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
		boolean leftHand = mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND;
		LivingEntity user = inHand ? holder : null;

		float reload = -1.0F;
		Long reloadStart = stack.get(ModComponents.MUSKET_RELOAD);
		if (inHand && reloadStart != null && client.world != null) {
			int ticks = MusketItem.reloadTicks(holder instanceof net.minecraft.entity.player.PlayerEntity p ? p : null);
			reload = MathHelper.clamp((client.world.getTime() - reloadStart + tickDelta) / ticks, 0.0F, 1.0F);
		}
		float aim = 0.0F;
		if (user != null && reload < 0.0F && MusketItem.isAiming(user)) {
			aim = smooth((user.getItemUseTime() + tickDelta) / AIM_TICKS);
		}
		float sinceFire = Float.MAX_VALUE;
		Long fired = stack.get(ModComponents.MUSKET_FIRED);
		if (fired != null && client.world != null) {
			sinceFire = Math.max(0.0F, client.world.getTime() - fired + tickDelta);
		}
		Pose pose = pose(reload, MusketItem.isLoaded(stack), sinceFire, firstPerson, inHand, aim > 0.0F);

		matrices.push();
		if (firstPerson && aim > 0.0F) {
			aimDownSights(matrices, aim, leftHand ? -1.0F : 1.0F);
		}
		matrices.translate(0.0F, 0.0F, pose.back / 16.0F);
		matrices.translate(GRIP_X / 16.0F, GRIP_Y / 16.0F, GRIP_Z / 16.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(leftHand ? -pose.yaw : pose.yaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.tilt));
		matrices.translate(-GRIP_X / 16.0F, -GRIP_Y / 16.0F, -GRIP_Z / 16.0F);

		VertexConsumer vertices = ItemRenderer.getDirectItemGlintConsumer(vertexConsumers, RenderLayer.getEntityCutout(MusketModel.TEXTURE),
				false, stack.hasGlint());
		body.render(matrices, vertices, light, overlay);
		stock.render(matrices, vertices, light, overlay);
		hammer.pitch = pose.hammer * MathHelper.RADIANS_PER_DEGREE;
		hammer.render(matrices, vertices, light, overlay);
		frizzen.pitch = pose.frizzen * MathHelper.RADIANS_PER_DEGREE;
		frizzen.render(matrices, vertices, light, overlay);
		if (!Float.isNaN(pose.rod)) {
			// la baqueta, dada la vuelta, con la cabeza dentro del cañón
			matrices.push();
			matrices.translate(BORE_X / 16.0F, BORE_Y / 16.0F, (MUZZLE_Z + pose.rod) / 16.0F);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
			matrices.translate(-ROD_HEAD_X / 16.0F, -ROD_HEAD_Y / 16.0F, -ROD_HEAD_Z / 16.0F);
			ramrod.render(matrices, vertices, light, overlay);
			matrices.pop();
		} else if (!pose.hideRod) {
			ramrod.render(matrices, vertices, light, overlay);
		}
		if (!Float.isNaN(pose.ball)) {
			matrices.push();
			matrices.translate(0.0F, 0.0F, pose.ball / 16.0F);
			ball.render(matrices, vertices, light, overlay);
			matrices.pop();
		}
		matrices.pop();
	}

	/**
	 * Lleva el arma de la cadera a la cara: deshace el desplazamiento de la mano y del modelo para que el punto de mira
	 * quede en el centro de la pantalla y el cañón mire recto (a medias mientras se la echa a la cara).
	 */
	private static void aimDownSights(MatrixStack matrices, float aim, float side) {
		float vx = -side * EQUIP_X - side * FP_X;
		float vy = ADS_Y - EQUIP_Y - FP_Y;
		float vz = ADS_Z - EQUIP_Z - FP_Z;
		float a = -side * FP_YAW * MathHelper.RADIANS_PER_DEGREE;
		float cos = MathHelper.cos(a);
		float sin = MathHelper.sin(a);
		float kx = 0.5F + (vx * cos + vz * sin) / FP_SCALE;
		float ky = 0.5F + vy / FP_SCALE;
		float kz = 0.5F + (-vx * sin + vz * cos) / FP_SCALE;
		matrices.translate(MathHelper.lerp(aim, SIGHT_X, kx), MathHelper.lerp(aim, SIGHT_Y, ky), MathHelper.lerp(aim, SIGHT_Z, kz));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side * FP_YAW * aim));
		matrices.translate(-SIGHT_X, -SIGHT_Y, -SIGHT_Z);
	}

	private static Pose pose(float reload, boolean loaded, float sinceFire, boolean firstPerson, boolean inHand, boolean aiming) {
		Pose s = new Pose();
		s.hammer = loaded ? HAMMER_COCKED : HAMMER_DOWN;
		s.frizzen = loaded ? FRIZZEN_CLOSED : FRIZZEN_OPEN;
		if (inHand && !firstPerson && !aiming && reload < 0.0F) {
			s.tilt = TP_IDLE_TILT; // bajado
		}
		if (reload >= 0.0F) {
			float p = reload;
			float a;
			if (p < 0.15F) {
				a = smooth(p / 0.15F);
			} else if (p < 0.70F) {
				a = 1.0F;
			} else {
				a = 1.0F - smooth(seg(p, 0.70F, 0.82F));
			}
			s.tilt = (firstPerson ? RELOAD_TILT_FP : RELOAD_TILT_TP) * a;
			s.yaw = (firstPerson ? RELOAD_YAW_FP : 0.0F) * a;
			s.hammer = p < 0.85F ? HAMMER_DOWN : MathHelper.lerp(smooth(seg(p, 0.85F, 1.0F)), HAMMER_DOWN, HAMMER_COCKED);
			s.frizzen = MathHelper.lerp(smooth(seg(p, 0.74F, 0.82F)), FRIZZEN_OPEN, FRIZZEN_CLOSED);
			if (p >= 0.15F && p < 0.42F) {
				s.ball = MathHelper.lerp(smooth(seg(p, 0.15F, 0.42F)), -5.0F, 1.2F);
			}
			if (p >= 0.42F && p < 0.70F) {
				float d;
				if (p < 0.48F) {
					d = MathHelper.lerp(smooth(seg(p, 0.42F, 0.48F)), 0.0F, 11.0F);
				} else if (p < 0.55F) {
					d = MathHelper.lerp(smooth(seg(p, 0.48F, 0.55F)), 11.0F, 5.0F);
				} else if (p < 0.62F) {
					d = MathHelper.lerp(smooth(seg(p, 0.55F, 0.62F)), 5.0F, 11.0F);
				} else {
					d = MathHelper.lerp(smooth(seg(p, 0.62F, 0.70F)), 11.0F, 0.0F);
				}
				s.rod = d;
			}
			s.hideRod = p >= 0.40F && p < 0.72F;
		} else if (sinceFire < 5.0F) {
			if (!loaded && sinceFire < 2.0F) {
				float q = smooth(sinceFire / 1.5F);
				s.hammer = MathHelper.lerp(q, HAMMER_COCKED, HAMMER_DOWN);
				s.frizzen = MathHelper.lerp(q, FRIZZEN_CLOSED, FRIZZEN_OPEN);
			}
			if (inHand) {
				float k = (1.0F - sinceFire / 5.0F) * (1.0F - sinceFire / 5.0F);
				s.tilt += (firstPerson ? 14.0F : 8.0F) * k;
				s.back = (firstPerson ? 1.2F : 0.6F) * k;
			}
		}
		return s;
	}

	private static float smooth(float t) {
		t = MathHelper.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static float seg(float p, float a, float b) {
		return (p - a) / (b - a);
	}
}

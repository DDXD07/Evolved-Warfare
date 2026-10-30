package com.evolvedwarfare.client.render;

import com.evolvedwarfare.item.CropCrateItem;
import com.evolvedwarfare.network.CratePickupPayload;
import com.evolvedwarfare.registry.ModBlocks;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * La Caja de cultivos se lleva con las dos manos. Las cuentas son las mismas que las de tools/ew_crate_preview.py (que
 * dibuja cómo queda sin abrir el juego).
 * <ul>
 * <li><b>Primera persona</b>: la caja delante, abajo, con la tapa un poco hacia ti (se ve lo que lleva) y los dos brazos
 * saliendo de las esquinas de la pantalla hasta sus asas. Al cogerla del suelo sube desde abajo; andando se mece con el
 * paso.</li>
 * <li><b>Tercera persona</b> (jugadores y cualquiera con cuerpo de jugador): los dos brazos adelante y la caja entre las
 * manos. Al cogerla, agachado, los brazos bajan a por ella y la suben; andando, se balancea.</li>
 * </ul>
 * Lo de la otra mano no se ve mientras se lleva una caja (ni se puede usar).
 */
public final class CrateCarry {
	// primera persona (espacio de la vista: x derecha, y arriba, -z adelante; en bloques)
	private static final float FP_SIZE = 0.5F;
	private static final float[] FP_REST = {0.0F, -0.46F, -0.92F, 24.0F};
	private static final float[] FP_LOW = {0.0F, -1.25F, -0.80F, 50.0F};
	private static final float FP_HAND_Y = 0.09F;
	private static final float FP_HAND_IN = 0.3F / 16.0F;
	private static final float[] FP_SHOULDER = {0.70F, -0.62F, 0.36F};
	private static final float FP_WALK_Y = 0.022F;
	private static final float FP_WALK_X = 0.012F;
	private static final float FP_WALK_ROLL = 2.2F;
	// tercera persona (espacio del modelo: y hacia abajo, -z adelante; en bloques)
	private static final float TP_SIZE = 0.56F;
	private static final float TP_PITCH = -0.95F;
	private static final float TP_PITCH_LOW = -0.30F;
	private static final float TP_YAW_OUT = 0.06F;
	private static final float TP_GRIP = 9.5F;
	private static final float TP_WALK = 0.05F;
	private static final float TP_HANDLE = 3.0F / 16.0F;
	/** Lo que tarda en subirla al cogerla del suelo, y al sacarla del inventario (tics). */
	private static final float LIFT_TICKS = 12.0F;
	private static final float EQUIP_TICKS = 5.0F;

	/** Cuándo la cogió del suelo cada uno (su edad entonces, en tics). */
	private static final Map<Integer, Float> PICKUPS = new HashMap<>();
	/** Desde cuándo lleva una caja (para subirla al sacarla del inventario). */
	private static final Map<LivingEntity, Float> SINCE = new WeakHashMap<>();
	/** Mientras se dibujan los brazos de la primera persona (vanilla llama a setAngles y no hay que tocarlos). */
	private static boolean drawingArms;

	private CrateCarry() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CratePickupPayload.ID, (payload, context) -> {
			if (context.client().world != null && context.client().world.getEntityById(payload.entityId()) instanceof LivingEntity living) {
				PICKUPS.put(payload.entityId(), (float) living.age);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PICKUPS.clear();
			SINCE.clear();
		});
	}

	public static boolean carrying(LivingEntity entity) {
		return CropCrateItem.isCrate(entity.getMainHandStack());
	}

	/** 0 = abajo (empezando a cogerla), 1 = ya en las manos. {@code now}: edad + tickDelta. */
	private static float pickup(Entity entity, float now) {
		Float since = PICKUPS.get(entity.getId());
		if (since == null) {
			return 1.0F;
		}
		float t = (now - since) / LIFT_TICKS;
		if (t >= 1.0F || t < -1.0F) {
			PICKUPS.remove(entity.getId());
			return 1.0F;
		}
		return MathHelper.clamp(t, 0.0F, 1.0F);
	}

	private static float lift(LivingEntity entity, float now) {
		float picked = pickup(entity, now);
		Float since = SINCE.get(entity);
		if (since == null || now < since) {
			since = now;
			SINCE.put(entity, now);
		}
		return picked < 1.0F ? picked : MathHelper.clamp((now - since) / EQUIP_TICKS, 0.0F, 1.0F);
	}

	// ------------------------------------------------------------------ tercera persona

	/** Al final de setAngles: los brazos adelante, agarrando la caja. */
	public static void pose(BipedEntityModel<?> model, LivingEntity entity, float limbAngle, float limbDistance, float animationProgress) {
		if (drawingArms) {
			return;
		}
		if (!carrying(entity)) {
			SINCE.remove(entity);
			return;
		}
		float lift = smooth(lift(entity, animationProgress));
		float pitch = MathHelper.lerp(lift, TP_PITCH_LOW, TP_PITCH) + (model.sneaking ? 0.4F : 0.0F)
				+ TP_WALK * Math.min(1.0F, limbDistance) * MathHelper.cos(limbAngle * 0.6662F * 2.0F);
		model.rightArm.pitch = pitch;
		model.leftArm.pitch = pitch;
		model.rightArm.yaw = TP_YAW_OUT + model.body.yaw;
		model.leftArm.yaw = -TP_YAW_OUT + model.body.yaw;
		model.rightArm.roll = 0.0F;
		model.leftArm.roll = 0.0F;
	}

	/** La caja entre las manos (en el espacio del modelo, como el resto de lo que se lleva). */
	public static void renderThirdPerson(BipedEntityModel<?> model, LivingEntity entity, ItemStack crate, MatrixStack matrices,
			VertexConsumerProvider consumers, int light) {
		Vector3f right = handPoint(model.rightArm, -1.0F / 16.0F);
		Vector3f left = handPoint(model.leftArm, 1.0F / 16.0F);
		matrices.push();
		matrices.translate((right.x + left.x) / 2.0F, (right.y + left.y) / 2.0F + TP_HANDLE * TP_SIZE, (right.z + left.z) / 2.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotation(model.body.yaw));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
		drawCrate(crate, entity, TP_SIZE, matrices, consumers, light);
		matrices.pop();
	}

	private static Vector3f handPoint(ModelPart arm, float x) {
		MatrixStack stack = new MatrixStack();
		arm.rotate(stack);
		return stack.peek().getPositionMatrix().transformPosition(x, TP_GRIP / 16.0F, 0.0F, new Vector3f());
	}

	// ------------------------------------------------------------------ primera persona

	public static void renderFirstPerson(AbstractClientPlayerEntity player, float tickDelta, float equipProgress, float swingProgress,
			ItemStack crate, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		float lift = easeOut(pickup(player, player.age + tickDelta));
		float x = MathHelper.lerp(lift, FP_LOW[0], FP_REST[0]);
		float y = MathHelper.lerp(lift, FP_LOW[1], FP_REST[1]);
		float z = MathHelper.lerp(lift, FP_LOW[2], FP_REST[2]);
		float pitch = MathHelper.lerp(lift, FP_LOW[3], FP_REST[3]);
		float phase = player.limbAnimator.getPos(tickDelta) * 0.6662F;
		float amp = Math.min(1.0F, player.limbAnimator.getSpeed(tickDelta));
		y += -FP_WALK_Y * amp * Math.abs(MathHelper.cos(phase)) - equipProgress * 0.6F;
		x += FP_WALK_X * amp * MathHelper.sin(phase);
		float roll = FP_WALK_ROLL * amp * MathHelper.sin(phase);
		// si golpea con la caja en las manos, la empuja un poco hacia delante
		z -= 0.12F * MathHelper.sin(swingProgress * MathHelper.PI);
		Matrix4f box = new Matrix4f().translation(x, y, z).rotateX(pitch * MathHelper.RADIANS_PER_DEGREE)
				.rotateZ(roll * MathHelper.RADIANS_PER_DEGREE);
		if (!player.isInvisible()) {
			PlayerEntityRenderer renderer = (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
			drawingArms = true;
			try {
				for (int side : new int[] {1, -1}) {
					matrices.push();
					matrices.multiplyPositionMatrix(armMatrix(box, side));
					if (side > 0) {
						renderer.renderRightArm(matrices, consumers, light, player);
					} else {
						renderer.renderLeftArm(matrices, consumers, light, player);
					}
					matrices.pop();
				}
			} finally {
				drawingArms = false;
			}
		}
		matrices.push();
		matrices.multiplyPositionMatrix(box);
		drawCrate(crate, player, FP_SIZE, matrices, consumers, light);
		matrices.pop();
	}

	/**
	 * Del espacio en que vanilla dibuja el brazo (renderRightArm/LeftArm: el hombro arriba, la mano al final, a 12
	 * píxeles) a la vista: la mano en el asa de su lado y el brazo hacia la esquina de abajo de la pantalla.
	 */
	private static Matrix4f armMatrix(Matrix4f box, int side) {
		Vector3f hand = box.transformPosition(side * (FP_SIZE / 2.0F + 2.0F / 16.0F - FP_HAND_IN), FP_HAND_Y, 0.0F, new Vector3f());
		Vector3f down = new Vector3f(-side * FP_SHOULDER[0], -FP_SHOULDER[1], -FP_SHOULDER[2]).normalize();
		Vector3f inward = new Vector3f(-1.0F, 0.0F, 0.0F);
		Vector3f b = new Vector3f(inward).sub(new Vector3f(down).mul(inward.dot(down))).normalize();
		Vector3f c = new Vector3f(b).cross(down);
		Matrix4f rotation = new Matrix4f(
				b.x, b.y, b.z, 0.0F,
				down.x, down.y, down.z, 0.0F,
				c.x, c.y, c.z, 0.0F,
				0.0F, 0.0F, 0.0F, 1.0F);
		return new Matrix4f().translation(hand).mul(rotation).translate(6.0F * side / 16.0F, -12.0F / 16.0F, 0.0F);
	}

	// ------------------------------------------------------------------ la caja

	/** La caja (su modelo y lo de dentro) centrada en el origen, de {@code size} bloques. */
	private static void drawCrate(ItemStack crate, Entity holder, float size, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		matrices.scale(size, size, size);
		matrices.translate(-0.5F, -0.5F, -0.5F);
		MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(ModBlocks.CROP_CRATE.getDefaultState(), matrices, consumers,
				light, OverlayTexture.DEFAULT_UV);
		CropCrateItem.Contents contents = CropCrateItem.contents(crate);
		if (contents != null) {
			CropCrateRenderer.renderContents(contents.stack(), holder.getId(), matrices, consumers, light, OverlayTexture.DEFAULT_UV,
					holder.getWorld());
		}
	}

	private static float smooth(float t) {
		t = MathHelper.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static float easeOut(float t) {
		t = MathHelper.clamp(t, 0.0F, 1.0F);
		float u = 1.0F - t;
		return 1.0F - u * u * u;
	}
}

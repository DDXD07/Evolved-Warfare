package com.evolvedwarfare.client.thirst;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.climate.Thirst;
import com.evolvedwarfare.network.DrinkPosePayload;
import com.evolvedwarfare.network.DrinkRequestPayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Beber con las manos: un segundo. Agachado, con la mano vacía, clic derecho al agua (o al cielo cuando llueve encima).
 * <ul>
 * <li><b>Primera persona</b>: la vista baja hacia el agua, los dos brazos (con tu piel) bajan juntos, cogen agua en el
 * hueco de las manos y se la llevan a la boca, la cabeza se echa un poco atrás y vuelven abajo. Con la lluvia, las manos
 * juntas delante de la cara recogen las gotas.</li>
 * <li><b>Tercera persona</b> (los demás, y tú en F5): agachado, mira el agua, mete las manos, las sube a la boca y bebe.
 * Con la lluvia mira al cielo con las manos juntas.</li>
 * </ul>
 * El servidor lo confirma (y entonces lo ven todos); el agua, la sed y los parásitos los decide él.
 */
public final class DrinkAnimation {
	private static final float DURATION = 1000.0F;
	private static final Identifier WATER = EvolvedWarfare.id("textures/misc/cupped_water.png");

	/** Desde cuándo bebe cada uno (ms) y cómo. */
	private record Drink(long start, byte pose) {
	}

	private static final Map<Integer, Drink> DRINKS = new HashMap<>();
	/** Mientras se dibujan los brazos de la primera persona (vanilla llama a setAngles y no hay que tocarlos). */
	private static boolean drawingArms;

	// primera persona (espacio de la vista: x derecha, y arriba, -z adelante; en bloques), la mano derecha
	private static final float[][] SCOOP = {
			{0.0F, 0.30F, -0.95F, -0.45F},
			{0.30F, 0.075F, -0.64F, -0.62F},
			{0.55F, 0.065F, -0.32F, -0.42F},
			{0.85F, 0.055F, -0.23F, -0.31F},
			{1.0F, 0.30F, -0.95F, -0.45F}};
	private static final float[][] RAIN = {
			{0.0F, 0.30F, -0.95F, -0.45F},
			{0.25F, 0.07F, -0.30F, -0.46F},
			{0.75F, 0.07F, -0.29F, -0.44F},
			{0.88F, 0.055F, -0.23F, -0.31F},
			{1.0F, 0.30F, -0.95F, -0.45F}};
	private static final float[] SHOULDER = {0.42F, -0.80F, 0.10F};

	private DrinkAnimation() {
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DrinkPosePayload.ID, (payload, context) ->
				DRINKS.put(payload.entityId(), new Drink(now(), payload.pose())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DRINKS.clear());
	}

	@Nullable
	private static Drink of(Entity entity) {
		if (DRINKS.isEmpty()) {
			return null;
		}
		Drink d = DRINKS.get(entity.getId());
		if (d == null) {
			return null;
		}
		if (now() - d.start() >= DURATION) {
			DRINKS.remove(entity.getId());
			return null;
		}
		return d;
	}

	public static boolean active(Entity entity) {
		return of(entity) != null;
	}

	private static float progress(Drink d) {
		return MathHelper.clamp((now() - d.start()) / DURATION, 0.0F, 1.0F);
	}

	static float smooth(float t) {
		t = MathHelper.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static float between(float t, float a, float b) {
		return smooth((t - a) / (b - a));
	}

	// ------------------------------------------------------------------ pedir beber (clic derecho)

	/**
	 * Al usar con clic derecho: si está agachado, con la mano vacía, y mira agua (o al cielo mientras le llueve encima),
	 * pide beber y devuelve true (y no se hace nada más con ese clic).
	 */
	public static boolean tryDrink(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || !ClientThirst.enabled() || !player.isSneaking() || !player.getMainHandStack().isEmpty()
				|| player.isSpectator() || active(player) || client.crosshairTarget instanceof EntityHitResult) {
			return false;
		}
		if (player.getPitch() < -40.0F && client.world.hasRain(BlockPos.ofFloored(player.getEyePos()))) {
			return request(player, DrinkRequestPayload.RAIN);
		}
		HitResult fluid = player.raycast(player.getBlockInteractionRange(), 1.0F, true);
		if (!(fluid instanceof BlockHitResult water) || fluid.getType() != HitResult.Type.BLOCK
				|| !client.world.getFluidState(water.getBlockPos()).isIn(FluidTags.WATER)) {
			return false;
		}
		// algo delante del agua (un caldero, un cofre...): eso va primero
		if (client.crosshairTarget instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
				&& !block.getBlockPos().equals(water.getBlockPos())
				&& block.getPos().squaredDistanceTo(player.getEyePos()) + 1.0E-3 < water.getPos().squaredDistanceTo(player.getEyePos())) {
			return false;
		}
		return request(player, DrinkRequestPayload.WATER);
	}

	private static boolean request(ClientPlayerEntity player, byte kind) {
		if (ClientThirst.level() >= Thirst.MAX - 0.5F) {
			player.sendMessage(Text.translatable("message.evolvedwarfare.thirst.full"), true);
			return true;
		}
		if (ClientPlayNetworking.canSend(DrinkRequestPayload.ID)) {
			ClientPlayNetworking.send(new DrinkRequestPayload(kind));
		}
		return true;
	}

	// ------------------------------------------------------------------ la cámara (primera persona)

	/** Lo que baja (+) o sube (−) la vista mientras bebe, en grados. */
	public static float cameraPitch(Entity entity) {
		Drink d = of(entity);
		if (d == null) {
			return 0.0F;
		}
		float t = progress(d);
		if (d.pose() == DrinkPosePayload.RAIN) {
			// ya mira arriba: un poco más al echar la cabeza atrás
			return -8.0F * between(t, 0.5F, 0.8F) * (1.0F - between(t, 0.85F, 1.0F));
		}
		float down = 24.0F * between(t, 0.0F, 0.3F) * (1.0F - between(t, 0.35F, 0.7F));
		float back = -10.0F * between(t, 0.55F, 0.8F) * (1.0F - between(t, 0.85F, 1.0F));
		return down + back;
	}

	// ------------------------------------------------------------------ primera persona

	/** Los dos brazos y el agua en las manos (en lugar de lo de siempre). */
	public static void renderFirstPerson(AbstractClientPlayerEntity player, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		Drink d = of(player);
		if (d == null) {
			return;
		}
		float t = progress(d);
		float[][] keys = d.pose() == DrinkPosePayload.RAIN ? RAIN : SCOOP;
		Vector3f right = keyframe(keys, t);
		if (!player.isInvisible()) {
			PlayerEntityRenderer renderer = (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
			drawingArms = true;
			try {
				for (int side : new int[] {1, -1}) {
					Vector3f hand = new Vector3f(right.x * side, right.y, right.z);
					matrices.push();
					matrices.multiplyPositionMatrix(armMatrix(hand, side));
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
		// el agua en el hueco de las manos (se va acabando al beber)
		float from = d.pose() == DrinkPosePayload.RAIN ? 0.3F : 0.28F;
		float amount = d.pose() == DrinkPosePayload.RAIN ? between(t, from, 0.7F) : between(t, from, 0.36F);
		amount *= 1.0F - between(t, 0.72F, 0.88F);
		if (amount > 0.01F) {
			float size = 0.075F * (0.5F + 0.5F * amount);
			matrices.push();
			matrices.translate(0.0F, right.y + 0.035F, right.z);
			MatrixStack.Entry e = matrices.peek();
			Matrix4f m = e.getPositionMatrix();
			VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucent(WATER));
			int alpha = (int) (200 * Math.min(1.0F, amount * 1.5F));
			vertex(vc, e, m, -size, 0.0F, -size * 0.8F, 0.0F, 0.0F, alpha, light);
			vertex(vc, e, m, -size, 0.0F, size * 0.8F, 0.0F, 1.0F, alpha, light);
			vertex(vc, e, m, size, 0.0F, size * 0.8F, 1.0F, 1.0F, alpha, light);
			vertex(vc, e, m, size, 0.0F, -size * 0.8F, 1.0F, 0.0F, alpha, light);
			matrices.pop();
		}
	}

	private static void vertex(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, float x, float y, float z, float u, float v, int alpha, int light) {
		vc.vertex(m, x, y, z).color(255, 255, 255, alpha).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, 0.0F, 1.0F, 0.0F);
	}

	/** La mano derecha a esa altura de la animación (entre los fotogramas, suavizado). */
	private static Vector3f keyframe(float[][] keys, float t) {
		for (int i = 0; i < keys.length - 1; i++) {
			float[] a = keys[i];
			float[] b = keys[i + 1];
			if (t <= b[0]) {
				float k = smooth((t - a[0]) / (b[0] - a[0]));
				return new Vector3f(MathHelper.lerp(k, a[1], b[1]), MathHelper.lerp(k, a[2], b[2]), MathHelper.lerp(k, a[3], b[3]));
			}
		}
		float[] last = keys[keys.length - 1];
		return new Vector3f(last[1], last[2], last[3]);
	}

	/**
	 * Del espacio en que vanilla dibuja el brazo (el hombro arriba, la mano 12 píxeles abajo) a la vista: la mano en
	 * {@code hand}, el brazo hacia el hombro (abajo, a su lado, detrás) y la palma hacia arriba, haciendo cuenco.
	 */
	private static Matrix4f armMatrix(Vector3f hand, int side) {
		Vector3f down = new Vector3f(hand).sub(SHOULDER[0] * side, SHOULDER[1], SHOULDER[2]).normalize();
		Vector3f palm = new Vector3f(-0.5F, side, 0.0F);
		Vector3f b = new Vector3f(palm).sub(new Vector3f(down).mul(palm.dot(down))).normalize();
		Vector3f c = new Vector3f(b).cross(down);
		Matrix4f rotation = new Matrix4f(
				b.x, b.y, b.z, 0.0F,
				down.x, down.y, down.z, 0.0F,
				c.x, c.y, c.z, 0.0F,
				0.0F, 0.0F, 0.0F, 1.0F);
		return new Matrix4f().translation(hand).mul(rotation).translate(6.0F * side / 16.0F, -11.0F / 16.0F, 0.0F);
	}

	// ------------------------------------------------------------------ tercera persona (al final de setAngles)

	public static void pose(BipedEntityModel<?> model, LivingEntity entity) {
		if (drawingArms) {
			return;
		}
		Drink d = of(entity);
		if (d == null) {
			return;
		}
		float t = progress(d);
		float w = between(t, 0.0F, 0.15F) * (1.0F - between(t, 0.85F, 1.0F));
		float pitch;
		float yaw;
		float head;
		if (d.pose() == DrinkPosePayload.RAIN) {
			pitch = -2.0F;
			yaw = 0.45F;
			head = -0.9F - 0.25F * between(t, 0.6F, 0.85F);
		} else {
			float lift = between(t, 0.35F, 0.6F);
			pitch = MathHelper.lerp(lift, -0.55F, -2.25F);
			yaw = MathHelper.lerp(lift, 0.3F, 0.55F);
			head = MathHelper.lerp(lift, 0.9F, -0.1F) - 0.2F * between(t, 0.6F, 0.8F);
		}
		model.rightArm.pitch = MathHelper.lerp(w, model.rightArm.pitch, pitch);
		model.leftArm.pitch = MathHelper.lerp(w, model.leftArm.pitch, pitch);
		model.rightArm.yaw = MathHelper.lerp(w, model.rightArm.yaw, -yaw + model.body.yaw);
		model.leftArm.yaw = MathHelper.lerp(w, model.leftArm.yaw, yaw + model.body.yaw);
		model.rightArm.roll = MathHelper.lerp(w, model.rightArm.roll, 0.0F);
		model.leftArm.roll = MathHelper.lerp(w, model.leftArm.roll, 0.0F);
		model.head.pitch = MathHelper.lerp(w, model.head.pitch, head);
		model.hat.copyTransform(model.head);
	}
}

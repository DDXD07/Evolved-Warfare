package com.evolvedwarfare.client.catalog;

import com.evolvedwarfare.network.CatalogPosePayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Los demás jugadores leyendo un catálogo, vistos desde fuera y en tiempo real: cogen el libro del mostrador (que deja de
 * verse allí), lo sujetan abierto con las dos manos delante del pecho, con la cabeza baja, y cuando pasan una página la
 * hoja gira sobre el lomo y la mano de ese lado la acompaña. Al cerrarlo, lo cierran, bajan los brazos y el libro vuelve
 * al mostrador. Los tiempos son los mismos que los del que lee ({@link CatalogSession}, {@link CatalogScreen#TURN_MS}).
 *
 * <p>Espacio del modelo (como en {@link com.evolvedwarfare.client.render.CrateCarry}): y hacia abajo, -z adelante, en
 * bloques.</p>
 */
public final class CatalogRemote {
	/** Suben los brazos con el libro (ms). */
	private static final float RAISE = 450.0F;
	/** Se abre la tapa (de, a). */
	private static final float OPEN_FROM = 350.0F;
	private static final float OPEN_TO = 950.0F;
	/** Al cerrar: se cierra la tapa, bajan los brazos y se acaba (el libro vuelve al mostrador). */
	private static final float CLOSE_COVER = 400.0F;
	private static final float CLOSE_ARMS_FROM = 250.0F;
	private static final float CLOSE_END = 750.0F;

	// la postura
	private static final float ARM_PITCH = -1.02F;
	private static final float ARM_YAW = 0.36F;
	private static final float HEAD_PITCH = 0.58F;
	/** La mano que pasa la hoja: cuánto va hacia el otro lado y cuánto sube. */
	private static final float TURN_YAW = 0.62F;
	private static final float TURN_LIFT = 0.32F;
	/** Dónde agarra (píxeles desde el hombro) y el tamaño del libro en las manos. */
	private static final float GRIP = 10.0F;
	private static final float BOOK_SCALE = 0.62F;
	private static final float BOOK_TILT = 38.0F;

	/** Uno que lee. Los tiempos, en ms (−1: todavía no). */
	static final class Pose {
		long opened;
		long closed = -1L;
		long turned = -1L;
		int turnDir = 1;
		int trade;
		@Nullable
		BlockPos book;
		/** Dónde estaba el libro entre las manos sin pasar página. */
		@Nullable
		Vector3f rest;
	}

	private static final Map<Integer, Pose> POSES = new HashMap<>();

	private CatalogRemote() {
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CatalogPosePayload.ID, (payload, context) -> receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> POSES.clear());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> POSES.clear());
		// los que ya no están (se han ido lejos, o de este mundo)
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (POSES.isEmpty() || client.world == null || client.world.getTime() % 20 != 0) {
				return;
			}
			Iterator<Map.Entry<Integer, Pose>> it = POSES.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Integer, Pose> entry = it.next();
				if (client.world.getEntityById(entry.getKey()) == null || finished(entry.getValue())) {
					it.remove();
				}
			}
		});
	}

	static void receive(CatalogPosePayload payload) {
		long t = now();
		Pose pose = POSES.get(payload.entityId());
		switch (payload.action()) {
			case CatalogPosePayload.OPEN, CatalogPosePayload.READING -> {
				pose = new Pose();
				// si ya lo estaba leyendo, se ve directamente abierto
				pose.opened = payload.action() == CatalogPosePayload.READING ? t - 10_000L : t;
				pose.trade = payload.trade();
				pose.book = payload.hasBook() ? payload.book().toImmutable() : null;
				POSES.put(payload.entityId(), pose);
			}
			case CatalogPosePayload.CLOSE -> {
				if (pose != null && pose.closed < 0L) {
					pose.closed = t;
				}
			}
			case CatalogPosePayload.TURN_FORWARD, CatalogPosePayload.TURN_BACK -> {
				if (pose != null && pose.closed < 0L) {
					pose.turned = t;
					pose.turnDir = payload.action() == CatalogPosePayload.TURN_FORWARD ? 1 : -1;
				}
			}
			default -> {
			}
		}
	}

	private static boolean finished(Pose pose) {
		return pose.closed >= 0L && now() - pose.closed >= CLOSE_END;
	}

	/** Lo que está haciendo ese (o null si no lee). */
	@Nullable
	private static Pose of(LivingEntity entity) {
		if (!(entity instanceof AbstractClientPlayerEntity) || POSES.isEmpty()) {
			return null;
		}
		Pose pose = POSES.get(entity.getId());
		if (pose == null) {
			return null;
		}
		if (finished(pose)) {
			POSES.remove(entity.getId());
			return null;
		}
		return pose;
	}

	/** ¿Tiene alguien en las manos el libro de ese mostrador? */
	public static boolean holds(BlockPos pos) {
		if (POSES.isEmpty()) {
			return false;
		}
		for (Pose pose : POSES.values()) {
			if (pos.equals(pose.book) && !finished(pose)) {
				return true;
			}
		}
		return false;
	}

	private static float between(float t, float a, float b) {
		return CatalogSession.smooth((t - a) / (b - a));
	}

	/** Cuánto ha subido los brazos (0 a 1). */
	private static float raised(Pose pose) {
		float up = between(now() - pose.opened, 0.0F, RAISE);
		if (pose.closed >= 0L) {
			up *= 1.0F - between(now() - pose.closed, CLOSE_ARMS_FROM, CLOSE_END);
		}
		return up;
	}

	/** Cuánto está abierta la tapa (0 a 1). */
	private static float cover(Pose pose) {
		float open = between(now() - pose.opened, OPEN_FROM, OPEN_TO);
		if (pose.closed >= 0L) {
			open *= 1.0F - between(now() - pose.closed, 0.0F, CLOSE_COVER);
		}
		return open;
	}

	/** Por dónde va la página que pasa (0 a 1), o −1 si no está pasando ninguna. */
	private static float turn(Pose pose) {
		if (pose.turned < 0L) {
			return -1.0F;
		}
		float t = (now() - pose.turned) / CatalogScreen.TURN_MS;
		return t >= 1.0F ? -1.0F : MathHelper.clamp(t, 0.0F, 1.0F);
	}

	// ------------------------------------------------------------------ la postura (al final de setAngles)

	public static void pose(BipedEntityModel<?> model, LivingEntity entity) {
		Pose pose = of(entity);
		if (pose == null) {
			return;
		}
		float k = raised(pose);
		if (k <= 0.0F) {
			return;
		}
		float pitch = ARM_PITCH + (model.sneaking ? 0.35F : 0.0F);
		model.rightArm.pitch = MathHelper.lerp(k, model.rightArm.pitch, pitch);
		model.leftArm.pitch = MathHelper.lerp(k, model.leftArm.pitch, pitch);
		model.rightArm.yaw = MathHelper.lerp(k, model.rightArm.yaw, -ARM_YAW + model.body.yaw);
		model.leftArm.yaw = MathHelper.lerp(k, model.leftArm.yaw, ARM_YAW + model.body.yaw);
		model.rightArm.roll = MathHelper.lerp(k, model.rightArm.roll, 0.0F);
		model.leftArm.roll = MathHelper.lerp(k, model.leftArm.roll, 0.0F);
		// la mano que pasa la hoja va con ella hacia el otro lado (levantándose un poco) y vuelve
		float t = turn(pose);
		if (t >= 0.0F) {
			float s = MathHelper.sin(t * MathHelper.PI);
			if (pose.turnDir > 0) {
				model.rightArm.yaw -= TURN_YAW * s;
				model.rightArm.pitch -= TURN_LIFT * s;
			} else {
				model.leftArm.yaw += TURN_YAW * s;
				model.leftArm.pitch -= TURN_LIFT * s;
			}
		}
		// mirando el libro
		model.head.pitch = MathHelper.lerp(k, model.head.pitch, HEAD_PITCH);
		model.head.yaw = MathHelper.lerp(k * 0.7F, model.head.yaw, 0.0F);
		model.hat.copyTransform(model.head);
	}

	// ------------------------------------------------------------------ el libro en las manos

	/**
	 * En lugar de lo que lleva en las manos: el libro abierto entre las dos. Devuelve false si ese no está leyendo (y
	 * entonces se dibuja lo de siempre).
	 */
	public static boolean renderHeld(BipedEntityModel<?> model, LivingEntity entity, MatrixStack matrices, VertexConsumerProvider consumers,
			int light) {
		Pose pose = of(entity);
		if (pose == null) {
			return false;
		}
		if (raised(pose) <= 0.0F && pose.closed >= 0L) {
			return false;
		}
		// mientras pasa la hoja, una mano se va: el libro se queda donde estaba
		float t = turn(pose);
		if (t < 0.0F || pose.rest == null) {
			Vector3f right = handPoint(model.rightArm, -1.0F / 16.0F);
			Vector3f left = handPoint(model.leftArm, 1.0F / 16.0F);
			pose.rest = right.add(left).mul(0.5F);
		}
		Vector3f mid = pose.rest;
		float open = cover(pose);
		matrices.push();
		matrices.translate(mid.x, mid.y - 0.5F / 16.0F, mid.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotation(model.body.yaw));
		// el libro boca arriba (el modelo va con la y hacia abajo), el lomo a la izquierda del que lo lleva, inclinado hacia él
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(BOOK_TILT));
		matrices.scale(BOOK_SCALE / 16.0F, BOOK_SCALE / 16.0F, BOOK_SCALE / 16.0F);
		// abierto, el lomo en medio de las manos
		matrices.translate(6.0F * open, 0.0F, 0.0F);
		CatalogBookRenderer.drawBook(matrices, consumers, pose.trade, light, OverlayTexture.DEFAULT_UV, open, t, pose.turnDir);
		matrices.pop();
		return true;
	}

	private static Vector3f handPoint(ModelPart arm, float x) {
		MatrixStack stack = new MatrixStack();
		arm.rotate(stack);
		return stack.peek().getPositionMatrix().transformPosition(x, GRIP / 16.0F, 0.0F, new Vector3f());
	}
}

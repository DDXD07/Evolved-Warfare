package com.evolvedwarfare.client.anim;

import com.evolvedwarfare.network.ActivityPayload;
import com.evolvedwarfare.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Las animaciones de los minijuegos, en primera y en tercera persona (los demás jugadores también lo ven):
 * <ul>
 * <li><b>Pescando</b> (el minijuego de pesca): la caña en alto con las dos manos, tirando hacia atrás, con el temblor del pez
 * que tira; al mantener pulsado, tira más fuerte y recoge.</li>
 * <li><b>Forjando</b> (en el yunque): el martillo de herrero en la mano, que sube y baja de golpe en cada martillazo, y el
 * lingote al rojo sujeto con la otra mano sobre el yunque.</li>
 * </ul>
 */
public final class ActivityPoses {
	private record State(byte activity, long start) {
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	/** Lo del jugador local: si mantiene pulsado (pesca) y cuándo fue su último martillazo (forja). */
	private static boolean reeling;
	private static long lastStrike = -10_000L;
	private static float tension;

	private ActivityPoses() {
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ActivityPayload.ID, (payload, context) -> {
			if (payload.activity() == ActivityPayload.NONE) {
				STATES.remove(payload.entityId());
			} else {
				STATES.put(payload.entityId(), new State(payload.activity(), now()));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STATES.clear());
	}

	public static byte activity(Entity entity) {
		State s = state(entity);
		return s == null ? ActivityPayload.NONE : s.activity();
	}

	/** Lo de ese jugador; el local, solo mientras tiene abierto el minijuego (por si se ha cerrado sin avisar). */
	private static State state(Entity entity) {
		State s = STATES.get(entity.getId());
		MinecraftClient client = MinecraftClient.getInstance();
		if (s != null && s.activity() == ActivityPayload.POURING && entity == client.player && !client.player.isUsingItem()) {
			return null;
		}
		if (s != null && (s.activity() == ActivityPayload.PLACING_PACK || s.activity() == ActivityPayload.DONNING_PACK)) {
			return s;
		}
		if (s != null && s.activity() != ActivityPayload.POURING && entity == client.player && !(client.currentScreen instanceof com.evolvedwarfare.client.fishing.FishingScreen)
				&& !(client.currentScreen instanceof com.evolvedwarfare.client.smithing.ForgingScreen)) {
			return null;
		}
		return s;
	}

	/** Cuánto lleva (0-1) de dejar o recoger la mochila, o -1 si no está en ello. */
	public static float packProgress(Entity entity, byte activity) {
		State s = state(entity);
		if (s == null || s.activity() != activity) {
			return -1.0F;
		}
		return Math.min(1.0F, (now() - s.start()) / 600.0F);
	}

	/** Desde la pantalla de pesca: si se mantiene pulsado y lo que tira el pez (0-1). */
	public static void reeling(boolean holding, float pull) {
		reeling = holding;
		tension = pull;
	}

	/** Desde la pantalla de forja: un martillazo. */
	public static void strike() {
		lastStrike = now();
	}

	/** El martillo (0 arriba, 1 golpeando): para el jugador local, con sus golpes; para los demás, a ritmo. */
	private static float hammer(Entity entity, long start) {
		boolean self = entity == MinecraftClient.getInstance().player;
		float t;
		if (self) {
			t = (now() - lastStrike) / 1000.0F;
		} else {
			t = ((now() - start) % 700L) / 1000.0F;
		}
		if (t < 0.07F) {
			return t / 0.07F;
		}
		if (t < 0.12F) {
			return 1.0F;
		}
		if (t < 0.4F) {
			float u = (t - 0.12F) / 0.28F;
			return 1.0F - u * u * (3.0F - 2.0F * u);
		}
		return 0.0F;
	}

	private static float time() {
		return (now() % 100000L) / 1000.0F;
	}

	// ------------------------------------------------------------------ tercera persona

	public static void pose(BipedEntityModel<?> model, LivingEntity entity) {
		State s = state(entity);
		if (s == null) {
			return;
		}
		boolean right = !(entity instanceof net.minecraft.entity.player.PlayerEntity p) || p.getMainArm() == Arm.RIGHT;
		var main = right ? model.rightArm : model.leftArm;
		var other = right ? model.leftArm : model.rightArm;
		float side = right ? 1.0F : -1.0F;
		float t = time();
		if (s.activity() == ActivityPayload.FISHING) {
			boolean self = entity == MinecraftClient.getInstance().player;
			float pull = self ? (reeling ? 1.0F : 0.4F) : 0.5F + 0.5F * MathHelper.sin(t * 1.7F);
			float shake = MathHelper.sin(t * 31.0F) * 0.05F + MathHelper.sin(t * 13.0F) * 0.04F * (self ? 0.5F + tension : 1.0F);
			// la caña en alto, con las dos manos, tirando hacia atrás
			main.pitch = -1.95F + pull * 0.35F + shake;
			main.yaw = -0.25F * side;
			main.roll = 0.0F;
			other.pitch = -1.55F + pull * 0.3F - shake;
			other.yaw = 0.55F * side;
			other.roll = 0.0F;
			model.body.pitch = -0.08F * pull;
			model.head.pitch = Math.max(model.head.pitch, 0.25F);
		} else if (s.activity() == ActivityPayload.POURING) {
			// el crisol con las dos manos, inclinado hacia el molde
			float wobble = MathHelper.sin(t * 9.0F) * 0.03F;
			main.pitch = -1.05F + wobble;
			main.yaw = -0.35F * side;
			main.roll = 0.0F;
			other.pitch = -1.0F - wobble;
			other.yaw = 0.45F * side;
			other.roll = 0.0F;
			model.head.pitch = Math.max(model.head.pitch, 0.6F);
		} else if (s.activity() == ActivityPayload.FORGING) {
			float h = hammer(entity, s.start());
			if (entity.getOffHandStack().isOf(ModItems.SMITHING_HAMMER)) {
				// el martillo en la otra mano: martillea esa
				var swap = main;
				main = other;
				other = swap;
				side = -side;
			}
			// el martillo: arriba por encima del hombro y abajo de golpe sobre el yunque
			main.pitch = MathHelper.lerp(h, -2.7F, -0.75F);
			main.yaw = -0.15F * side;
			main.roll = 0.0F;
			// el lingote, sujeto sobre el yunque
			other.pitch = -0.95F;
			other.yaw = 0.35F * side;
			other.roll = 0.0F;
			model.head.pitch = 0.55F;
			model.body.pitch = 0.12F + h * 0.08F;
		}
	}

	// ------------------------------------------------------------------ primera persona

	/**
	 * La mano en primera persona mientras pesca o forja. Devuelve true si la ha dibujado ella (forja: el martillo en una mano
	 * y el lingote en la otra) y no hay que dibujar lo de vanilla; para la pesca solo mueve la mano (devuelve false) y hay que
	 * deshacerlo con {@link #pop}.
	 */
	public static boolean firstPerson(HeldItemRenderer renderer, AbstractClientPlayerEntity player, Hand hand, ItemStack item, MatrixStack matrices,
			VertexConsumerProvider consumers, int light) {
		byte a = activity(player);
		if (a == ActivityPayload.FISHING) {
			float t = time();
			float shake = MathHelper.sin(t * 31.0F) * (0.6F + tension) + MathHelper.sin(t * 11.0F) * 0.8F;
			float pull = reeling ? 1.0F : 0.0F;
			matrices.push();
			matrices.translate(0.0F, 0.12F + pull * 0.05F, 0.05F + pull * 0.08F);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(18.0F + pull * 12.0F + shake));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(t * 2.0F) * 2.0F));
			return false;
		}
		if (a == ActivityPayload.POURING) {
			// el crisol, inclinado: más cuanto más rato
			float tilt = Math.min(1.0F, (now() - STATES.get(player.getId()).start()) / 1500.0F);
			matrices.push();
			matrices.translate(0.0F, 0.1F + tilt * 0.06F, -0.1F);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-20.0F - tilt * 35.0F));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(time() * 8.0F) * 1.5F));
			return false;
		}
		if (a == ActivityPayload.FORGING) {
			boolean right = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
			float side = right ? 1.0F : -1.0F;
			matrices.push();
			if (hand == Hand.MAIN_HAND) {
				// el martillo
				float h = hammer(player, 0L);
				matrices.translate(side * 0.56F, -0.42F - h * 0.18F, -0.72F + h * 0.1F);
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F - h * 95.0F));
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -10.0F));
				renderer.renderItem(player, new ItemStack(ModItems.SMITHING_HAMMER),
						right ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND, !right, matrices, consumers, light);
			} else {
				// el lingote al rojo, en la otra mano, bajo y quieto
				ItemStack ingot = player.getMainHandStack();
				matrices.translate(side * 0.5F, -0.62F, -0.8F);
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-60.0F));
				renderer.renderItem(player, ingot, right ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND,
						!right, matrices, consumers, light);
			}
			matrices.pop();
			return true;
		}
		return false;
	}

}

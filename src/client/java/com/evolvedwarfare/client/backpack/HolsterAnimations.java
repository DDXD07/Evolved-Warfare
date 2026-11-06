package com.evolvedwarfare.client.backpack;

import com.evolvedwarfare.backpack.Backpacks;
import com.evolvedwarfare.backpack.Loadout;
import com.evolvedwarfare.client.anim.ActivityPoses;
import com.evolvedwarfare.network.ActivityPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;

/**
 * Las animaciones de la carga y de la mochila (fase 40), en tercera y en primera persona:
 * <ul>
 * <li><b>Envainar y desenvainar</b>: al cambiar de hueco, la mano lleva lo que tenía a su sitio (la espalda o la cadera) y lo
 * suelta allí, y luego va a por lo nuevo y lo saca (con su ruido de cuero o de metal). Lo que va en la mano no se dibuja en
 * el cuerpo, y lo que está en el cuerpo, sí, hasta que la mano lo coge.</li>
 * <li><b>Dejar la mochila</b>: la coge del hombro, se la descuelga y la baja hasta el suelo.</li>
 * <li><b>Recogerla</b>: se agacha, la coge con las dos manos, la sube y se la echa a la espalda.</li>
 * </ul>
 */
public final class HolsterAnimations {
	public enum Spot {
		BACK, RIGHT_HIP, LEFT_HIP, NONE
	}

	private record Anim(ItemStack from, Spot fromSpot, ItemStack to, Spot toSpot, int start) {
		int sheath() {
			return fromSpot == Spot.NONE ? 0 : SHEATH;
		}

		int total() {
			return sheath() + (toSpot == Spot.NONE ? 0 : DRAW);
		}
	}

	/** Lo que tarda cada mitad (tics). */
	public static final int SHEATH = 5, DRAW = 6;
	private static final Map<Integer, ItemStack> LAST = new HashMap<>();
	private static final Map<Integer, Anim> ANIMS = new HashMap<>();

	private HolsterAnimations() {
	}

	/** Cada tic: mira qué ha cambiado de mano cada jugador y empieza (o acaba) su animación. */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			LAST.clear();
			ANIMS.clear();
			return;
		}
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			int id = player.getId();
			ItemStack now = player.getMainHandStack();
			ItemStack last = LAST.get(id);
			if (last != null && !ItemStack.areItemsEqual(last, now)) {
				Spot fs = spot(player, last), ts = spot(player, now);
				if (fs != Spot.NONE || ts != Spot.NONE) {
					ANIMS.put(id, new Anim(last.copy(), fs, now.copy(), ts, player.age));
				}
			}
			LAST.put(id, now.copy());
			Anim a = ANIMS.get(id);
			if (a != null) {
				int t = player.age - a.start();
				if (t >= a.total()) {
					ANIMS.remove(id);
				} else if (a.toSpot() != Spot.NONE && t == a.sheath() + 2) {
					// el ruido al sacarlo: metal para las armas, cuero para las herramientas
					var sound = a.toSpot() == Spot.BACK ? SoundEvents.ITEM_ARMOR_EQUIP_IRON : SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
					client.world.playSound(player.getX(), player.getY(), player.getZ(), sound.value(), SoundCategory.PLAYERS, 0.45F, 1.2F, false);
				}
			}
		}
		LAST.keySet().removeIf(id -> client.world.getEntityById(id) == null);
		ANIMS.keySet().removeIf(id -> client.world.getEntityById(id) == null);
	}

	/** Dónde lleva guardada esa cosa (o NONE si no es de la carga). */
	public static Spot spot(PlayerEntity player, ItemStack stack) {
		if (stack.isEmpty() || Loadout.role(stack) == Loadout.Role.OTHER || !Backpacks.limited(player)) {
			return Spot.NONE;
		}
		List<ItemStack> shown = Loadout.shown(player);
		for (int i = 0; i < 3; i++) {
			ItemStack s = player == MinecraftClient.getInstance().player ? player.getInventory().getStack(i) : i < shown.size() ? shown.get(i) : ItemStack.EMPTY;
			if (ItemStack.areItemsEqual(s, stack)) {
				return Spot.values()[i];
			}
		}
		return Loadout.role(stack) == Loadout.Role.WEAPON ? Spot.BACK : Spot.RIGHT_HIP;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** El momento de la animación de ese jugador: dónde va la mano (spot), cuánto (0-1) y qué lleva en ella; null si nada. */
	public record Frame(Spot spot, float weight, ItemStack hand) {
	}

	public static Frame frame(PlayerEntity player, float tickDelta) {
		Anim a = ANIMS.get(player.getId());
		if (a == null) {
			return null;
		}
		float t = player.age - a.start() + tickDelta;
		int sheath = a.sheath();
		if (t < sheath) {
			float p = t / sheath;
			float w = p < 0.6F ? ease(p / 0.6F) : ease(1.0F - (p - 0.6F) / 0.4F);
			return new Frame(a.fromSpot(), w, p < 0.6F ? a.from() : ItemStack.EMPTY);
		}
		if (t < a.total()) {
			float p = (t - sheath) / DRAW;
			float w = p < 0.4F ? ease(p / 0.4F) : ease(1.0F - (p - 0.4F) / 0.6F);
			return new Frame(a.toSpot(), w, p < 0.4F ? ItemStack.EMPTY : player.getMainHandStack());
		}
		return null;
	}

	/** Lo que se ve ahora en la mano principal (durante la animación puede ser lo de antes, o nada). */
	public static ItemStack visibleHand(PlayerEntity player, float tickDelta) {
		Frame f = frame(player, tickDelta);
		return f == null ? player.getMainHandStack() : f.hand();
	}

	public static boolean animating(PlayerEntity player) {
		return ANIMS.containsKey(player.getId());
	}

	public static float delta() {
		return MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
	}

	// ------------------------------------------------------------------ tercera persona: los brazos

	private static void blend(ModelPart arm, float w, float pitch, float yaw, float roll) {
		arm.pitch = MathHelper.lerp(w, arm.pitch, pitch);
		arm.yaw = MathHelper.lerp(w, arm.yaw, yaw);
		arm.roll = MathHelper.lerp(w, arm.roll, roll);
	}

	public static void pose(BipedEntityModel<?> model, LivingEntity entity, float animationProgress) {
		if (!(entity instanceof PlayerEntity player)) {
			return;
		}
		boolean right = player.getMainArm() == Arm.RIGHT;
		ModelPart main = right ? model.rightArm : model.leftArm;
		ModelPart other = right ? model.leftArm : model.rightArm;
		float side = right ? 1.0F : -1.0F;
		float place = ActivityPoses.packProgress(player, ActivityPayload.PLACING_PACK);
		float don = ActivityPoses.packProgress(player, ActivityPayload.DONNING_PACK);
		if (place >= 0.0F) {
			if (place < 0.35F) {
				// la coge del hombro
				blend(main, ease(place / 0.35F), -2.9F, -0.45F * side, 0.0F);
			} else {
				// se la descuelga y la baja hasta el suelo, con las dos manos
				float q = (place - 0.35F) / 0.65F;
				float w = q < 0.8F ? ease(q / 0.3F) : ease((1.0F - q) / 0.2F);
				blend(main, w, MathHelper.lerp(ease(q), -2.2F, -0.9F), -0.25F * side, 0.0F);
				blend(other, w, MathHelper.lerp(ease(q), -2.0F, -0.9F), 0.25F * side, 0.0F);
				model.body.pitch += 0.35F * w * ease(q);
			}
			return;
		}
		if (don >= 0.0F) {
			float w = don < 0.85F ? ease(don / 0.2F) : ease((1.0F - don) / 0.15F);
			float lift = ease((don - 0.35F) / 0.5F);
			// se agacha y la coge (abajo, delante) y la sube por encima del hombro
			blend(main, w, MathHelper.lerp(lift, -0.95F, -3.0F), -0.25F * side, 0.0F);
			blend(other, w, MathHelper.lerp(lift, -0.95F, -2.7F), 0.25F * side, 0.0F);
			model.body.pitch += 0.4F * w * (1.0F - lift);
			return;
		}
		Frame f = frame(player, animationProgress - (int) animationProgress);
		if (f == null || f.weight() <= 0.0F) {
			return;
		}
		switch (f.spot()) {
			case BACK -> {
				blend(main, f.weight(), -2.95F, -0.5F * side, 0.0F);
				model.body.yaw += 0.12F * f.weight() * side;
			}
			case RIGHT_HIP, LEFT_HIP -> {
				boolean same = (f.spot() == Spot.RIGHT_HIP) == right;
				if (same) {
					blend(main, f.weight(), 0.35F, 0.0F, 0.22F * side);
				} else {
					// a la cadera del otro lado, cruzando por delante
					blend(main, f.weight(), -0.55F, 0.85F * side, 0.0F);
					model.body.yaw -= 0.15F * f.weight() * side;
				}
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ la mochila durante las animaciones

	/** Dónde se dibuja la mochila: 0 a la espalda, 1 en las manos (con su avance 0-1), -1 en ningún sitio. */
	public record PackPose(int where, float progress) {
	}

	public static PackPose packPose(PlayerEntity player) {
		float place = ActivityPoses.packProgress(player, ActivityPayload.PLACING_PACK);
		if (place >= 0.35F) {
			return new PackPose(1, (place - 0.35F) / 0.65F);
		}
		float don = ActivityPoses.packProgress(player, ActivityPayload.DONNING_PACK);
		if (don >= 0.0F && don < 0.85F) {
			return don < 0.3F ? new PackPose(-1, 0.0F) : new PackPose(2, (don - 0.3F) / 0.55F);
		}
		return new PackPose(0, 0.0F);
	}

	/** ¿Está dejando o recogiendo la mochila (con las manos ocupadas)? */
	public static boolean handsBusy(PlayerEntity player) {
		return ActivityPoses.packProgress(player, ActivityPayload.PLACING_PACK) >= 0.0F
				|| ActivityPoses.packProgress(player, ActivityPayload.DONNING_PACK) >= 0.0F;
	}
}

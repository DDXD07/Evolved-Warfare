package com.evolvedwarfare.pickup;

import com.evolvedwarfare.EvolvedWarfare;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Recoger a mano: lo que está en el suelo ya no se recoge solo al pasar por encima. Se recoge agachándose encima, o con clic
 * derecho mirándolo (desde un poco lejos, como quien se agacha a cogerlo).
 */
public final class Pickup {
	/** Alcance del clic derecho (bloques). */
	public static final double REACH = 4.5;
	private static final ThreadLocal<Boolean> ALLOWED = ThreadLocal.withInitial(() -> false);

	private Pickup() {
	}

	public record PickupPayload(int entityId) implements CustomPayload {
		public static final Id<PickupPayload> ID = new Id<>(EvolvedWarfare.id("pickup"));
		public static final PacketCodec<RegistryByteBuf, PickupPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, PickupPayload::entityId,
				PickupPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(PickupPayload.ID, PickupPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PickupPayload.ID, (payload, context) -> pick(context.player(), payload.entityId()));
	}

	/** ¿Puede este jugador coger eso al tocarlo? Solo agachado (o si lo ha pedido con clic derecho). */
	public static boolean mayCollect(PlayerEntity player) {
		return ALLOWED.get() || player.isSneaking() || player.isSpectator();
	}

	/** El clic derecho: lo coge si está a mano. */
	public static boolean pick(ServerPlayerEntity player, int id) {
		if (!(player.getServerWorld().getEntityById(id) instanceof ItemEntity item) || !item.isAlive()
				|| item.squaredDistanceTo(player.getEyePos()) > (REACH + 1.5) * (REACH + 1.5)) {
			return false;
		}
		int before = item.getStack().getCount();
		ALLOWED.set(true);
		try {
			item.onPlayerCollision(player);
		} finally {
			ALLOWED.set(false);
		}
		boolean took = item.isRemoved() || item.getStack().getCount() < before;
		if (took) {
			player.swingHand(Hand.MAIN_HAND, true);
		}
		return took;
	}

	/** Lo que hay en el suelo en la línea de la mira (lo más cercano), o null. */
	public static ItemEntity aimed(PlayerEntity player, double maxDistance) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(maxDistance));
		ItemEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (ItemEntity item : player.getWorld().getEntitiesByClass(ItemEntity.class, new Box(eye, end).expand(1.0), e -> e.isAlive())) {
			var hit = item.getBoundingBox().expand(0.3).raycast(eye, end);
			if (hit.isPresent()) {
				double d = hit.get().squaredDistanceTo(eye);
				if (d < bestD) {
					bestD = d;
					best = item;
				}
			}
		}
		return best;
	}
}

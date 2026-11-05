package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Servidor → el jugador y los que lo ven: qué está haciendo (para animarlo en primera y tercera persona): nada, recogiendo
 * un pez en el minijuego de pesca, o forjando en el yunque.
 */
public record ActivityPayload(int entityId, byte activity) implements CustomPayload {
	public static final byte NONE = 0;
	public static final byte FISHING = 1;
	public static final byte FORGING = 2;

	public static final CustomPayload.Id<ActivityPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("activity"));
	public static final PacketCodec<RegistryByteBuf, ActivityPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, ActivityPayload::entityId,
			PacketCodecs.BYTE, ActivityPayload::activity, ActivityPayload::new);

	private static final Map<UUID, Byte> ACTIVE = new HashMap<>();

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public static void register() {
		EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
			Byte a = ACTIVE.get(tracked.getUuid());
			if (a != null && tracked instanceof ServerPlayerEntity) {
				send(viewer, new ActivityPayload(tracked.getId(), a));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.player.getUuid()));
	}

	private static void send(ServerPlayerEntity to, ActivityPayload payload) {
		if (ServerPlayNetworking.canSend(to, ID)) {
			ServerPlayNetworking.send(to, payload);
		}
	}

	/** Lo que está haciendo ahora (para las pruebas). */
	public static byte current(ServerPlayerEntity player) {
		return ACTIVE.getOrDefault(player.getUuid(), NONE);
	}

	/** Lo cambia y se lo dice a él y a los que lo ven. */
	public static void set(ServerPlayerEntity player, byte activity) {
		if (activity == NONE) {
			if (ACTIVE.remove(player.getUuid()) == null) {
				return;
			}
		} else {
			ACTIVE.put(player.getUuid(), activity);
		}
		ActivityPayload payload = new ActivityPayload(player.getId(), activity);
		send(player, payload);
		for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
			if (viewer != player) {
				send(viewer, payload);
			}
		}
	}
}

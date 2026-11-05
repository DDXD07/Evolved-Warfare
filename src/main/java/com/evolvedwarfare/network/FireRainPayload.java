package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Servidor → cliente: si está lloviendo fuego en el Nether (el cielo se pone más rojo y caen más brasas). */
public record FireRainPayload(boolean active) implements CustomPayload {
	public static final CustomPayload.Id<FireRainPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("fire_rain"));
	public static final PacketCodec<RegistryByteBuf, FireRainPayload> CODEC = PacketCodec.tuple(PacketCodecs.BOOL, FireRainPayload::active,
			FireRainPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public static void send(ServerPlayerEntity player, boolean active) {
		if (ServerPlayNetworking.canSend(player, ID)) {
			ServerPlayNetworking.send(player, new FireRainPayload(active));
		}
	}
}

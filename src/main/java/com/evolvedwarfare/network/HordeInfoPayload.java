package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Servidor → cliente: cada cuántos días llega una horda (0 = no hay hordas: desactivadas, pacífico o sin criaturas), para
 * que el contador de días se ponga rojo el día que toca.
 */
public record HordeInfoPayload(int every) implements CustomPayload {
	public static final CustomPayload.Id<HordeInfoPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("horde_info"));
	public static final PacketCodec<RegistryByteBuf, HordeInfoPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, HordeInfoPayload::every,
			HordeInfoPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public static void send(ServerPlayerEntity player, int every) {
		if (ServerPlayNetworking.canSend(player, ID)) {
			ServerPlayNetworking.send(player, new HordeInfoPayload(every));
		}
	}
}

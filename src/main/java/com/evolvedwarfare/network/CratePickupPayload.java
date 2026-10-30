package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Servidor → clientes: alguien acaba de coger una Caja de cultivos del suelo (para la animación de levantarla). */
public record CratePickupPayload(int entityId) implements CustomPayload {
	public static final CustomPayload.Id<CratePickupPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("crate_pickup"));
	public static final PacketCodec<RegistryByteBuf, CratePickupPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, CratePickupPayload::entityId,
			CratePickupPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** A él y a los que lo ven. */
	public static void send(ServerPlayerEntity player) {
		CratePickupPayload payload = new CratePickupPayload(player.getId());
		if (ServerPlayNetworking.canSend(player, ID)) {
			ServerPlayNetworking.send(player, payload);
		}
		for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
			if (other != player && ServerPlayNetworking.canSend(other, ID)) {
				ServerPlayNetworking.send(other, payload);
			}
		}
	}
}

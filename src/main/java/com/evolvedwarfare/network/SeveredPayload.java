package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor → los que lo ven: lo que le falta a un NPC (bits de Dismemberment), para no dibujarlo. */
public record SeveredPayload(int entityId, int mask) implements CustomPayload {
	public static final CustomPayload.Id<SeveredPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("severed"));
	public static final PacketCodec<RegistryByteBuf, SeveredPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SeveredPayload::entityId,
			PacketCodecs.VAR_INT, SeveredPayload::mask,
			SeveredPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor → jugador: su sed (0 a 20) y si la sed está activada (para el medidor encima del hambre). */
public record ThirstPayload(float level, boolean enabled) implements CustomPayload {
	public static final CustomPayload.Id<ThirstPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("thirst"));
	public static final PacketCodec<RegistryByteBuf, ThirstPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, ThirstPayload::level,
			PacketCodecs.BOOL, ThirstPayload::enabled,
			ThirstPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

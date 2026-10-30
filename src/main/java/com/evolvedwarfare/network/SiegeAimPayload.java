package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Cliente → servidor: el punto que marca con la cámara aérea quien maneja una máquina de asedio (o que ha dejado de
 * usarla, con {@code active = false}). El servidor solo lo usa para el siguiente disparo si llega a tiempo.
 */
public record SiegeAimPayload(boolean active, double x, double y, double z) implements CustomPayload {
	public static final CustomPayload.Id<SiegeAimPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("siege_aim"));
	public static final PacketCodec<RegistryByteBuf, SiegeAimPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOL, SiegeAimPayload::active,
			PacketCodecs.DOUBLE, SiegeAimPayload::x,
			PacketCodecs.DOUBLE, SiegeAimPayload::y,
			PacketCodecs.DOUBLE, SiegeAimPayload::z,
			SiegeAimPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

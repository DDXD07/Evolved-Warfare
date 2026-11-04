package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Cliente → servidor: agachado y con la mano vacía, quiere beber del agua que mira o de la lluvia (el servidor lo comprueba). */
public record DrinkRequestPayload(byte kind) implements CustomPayload {
	public static final byte WATER = 0;
	public static final byte RAIN = 1;

	public static final CustomPayload.Id<DrinkRequestPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("drink_request"));
	public static final PacketCodec<RegistryByteBuf, DrinkRequestPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BYTE, DrinkRequestPayload::kind,
			DrinkRequestPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

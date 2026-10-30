package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Cliente → servidor: disparar (clic izquierdo) o recargar (tecla R) el arma de fuego de la mano. */
public record FirearmPayload(int action) implements CustomPayload {
	public static final int FIRE = 0;
	public static final int RELOAD = 1;
	public static final CustomPayload.Id<FirearmPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("firearm"));
	public static final PacketCodec<RegistryByteBuf, FirearmPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, FirearmPayload::action,
			FirearmPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

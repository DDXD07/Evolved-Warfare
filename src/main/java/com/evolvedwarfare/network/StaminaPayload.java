package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor → jugador: su aguante (0 a 100), si está agotado y si se usa (fase 97). */
public record StaminaPayload(float value, boolean exhausted, boolean enabled) implements CustomPayload {
	public static final CustomPayload.Id<StaminaPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("stamina"));
	public static final PacketCodec<RegistryByteBuf, StaminaPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, StaminaPayload::value,
			PacketCodecs.BOOL, StaminaPayload::exhausted,
			PacketCodecs.BOOL, StaminaPayload::enabled,
			StaminaPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

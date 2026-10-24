package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Cliente → servidor: un botón de la pantalla de diplomacia (facción y acción de {@code Diplomacy}). */
public record DiplomacyActionPayload(int faction, int action) implements CustomPayload {
	public static final CustomPayload.Id<DiplomacyActionPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("diplomacy_action"));
	public static final PacketCodec<RegistryByteBuf, DiplomacyActionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DiplomacyActionPayload::faction,
			PacketCodecs.VAR_INT, DiplomacyActionPayload::action,
			DiplomacyActionPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

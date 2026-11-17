package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor → jugador: cómo tiene cada parte del cuerpo (0 a 1), qué está roto, qué entablillado y si sangra (fase 92). */
public record WoundsPayload(List<Float> parts, int broken, int splinted, boolean bleeding, boolean enabled) implements CustomPayload {
	public static final CustomPayload.Id<WoundsPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("wounds"));
	public static final PacketCodec<RegistryByteBuf, WoundsPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT.collect(PacketCodecs.toList()), WoundsPayload::parts,
			PacketCodecs.VAR_INT, WoundsPayload::broken,
			PacketCodecs.VAR_INT, WoundsPayload::splinted,
			PacketCodecs.BOOL, WoundsPayload::bleeding,
			PacketCodecs.BOOL, WoundsPayload::enabled,
			WoundsPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Servidor → jugador: cómo tiene cada parte del cuerpo (0 a 1), lo que le queda a cada entablillado (segundos), qué está
 * roto, los segundos que le quedan sangrando, los corazones (los que tiene y, ×256, el máximo) y si está activo (fase 92).
 */
public record WoundsPayload(List<Float> parts, List<Integer> splintLeft, int broken, int bleedSeconds, int hearts, boolean enabled)
		implements CustomPayload {
	public static final CustomPayload.Id<WoundsPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("wounds"));
	public static final PacketCodec<RegistryByteBuf, WoundsPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT.collect(PacketCodecs.toList()), WoundsPayload::parts,
			PacketCodecs.VAR_INT.collect(PacketCodecs.toList()), WoundsPayload::splintLeft,
			PacketCodecs.VAR_INT, WoundsPayload::broken,
			PacketCodecs.VAR_INT, WoundsPayload::bleedSeconds,
			PacketCodecs.VAR_INT, WoundsPayload::hearts,
			PacketCodecs.BOOL, WoundsPayload::enabled,
			WoundsPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Servidor → jugador: ganó experiencia de superviviente por algo que merece aviso (fase 112): 'reason' es kill, craft,
 * biome o dawn y 'arg', la clave de traducción de lo que mató, fabricó o el bioma (o el día, al amanecer).
 */
public record SurvivorPayload(float amount, String reason, String arg) implements CustomPayload {
	public static final CustomPayload.Id<SurvivorPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("survivor_xp"));
	public static final PacketCodec<RegistryByteBuf, SurvivorPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, SurvivorPayload::amount,
			PacketCodecs.STRING, SurvivorPayload::reason,
			PacketCodecs.STRING, SurvivorPayload::arg,
			SurvivorPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

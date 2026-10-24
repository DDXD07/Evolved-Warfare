package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Servidor → cliente: tu reputación y tus tratos con cada facción (en el orden de {@code Faction}). Con
 * {@code open} abre la pantalla de diplomacia; sin él solo la actualiza.
 */
public record DiplomacyDataPayload(boolean open, List<Status> factions) implements CustomPayload {
	public static final CustomPayload.Id<DiplomacyDataPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("diplomacy_data"));

	/** Reputación, trato (Standing), ticks que le quedan a la tregua y hasta que puedas volver a pedir ayuda. */
	public record Status(int reputation, int standing, int truceLeft, int aidLeft) {
		static final PacketCodec<RegistryByteBuf, Status> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, Status::reputation,
				PacketCodecs.VAR_INT, Status::standing,
				PacketCodecs.VAR_INT, Status::truceLeft,
				PacketCodecs.VAR_INT, Status::aidLeft,
				Status::new);
	}

	public static final PacketCodec<RegistryByteBuf, DiplomacyDataPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOL, DiplomacyDataPayload::open,
			Status.CODEC.collect(PacketCodecs.toList()), DiplomacyDataPayload::factions,
			DiplomacyDataPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

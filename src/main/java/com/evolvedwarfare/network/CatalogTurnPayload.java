package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Cliente → servidor: he pasado una página del catálogo ({@code forward}: hacia delante), para que lo vean los demás. */
public record CatalogTurnPayload(boolean forward) implements CustomPayload {
	public static final CustomPayload.Id<CatalogTurnPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("catalog_turn"));
	public static final PacketCodec<RegistryByteBuf, CatalogTurnPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOL, CatalogTurnPayload::forward,
			CatalogTurnPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

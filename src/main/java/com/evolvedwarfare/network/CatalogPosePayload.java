package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Servidor → los que ven a un jugador: ese jugador abre su catálogo, lo cierra o pasa una página (para verlo con el
 * libro en las manos). {@code trade}: el oficio de la portada; {@code book}: el libro del mostrador que ha cogido (si
 * {@code hasBook}), que no se dibuja en el mostrador mientras lo tiene él.
 */
public record CatalogPosePayload(int entityId, byte action, int trade, BlockPos book, boolean hasBook) implements CustomPayload {
	/** Lo acaba de abrir. */
	public static final byte OPEN = 0;
	/** Ya lo tenía abierto (el que lo ve acaba de llegar). */
	public static final byte READING = 1;
	public static final byte CLOSE = 2;
	public static final byte TURN_FORWARD = 3;
	public static final byte TURN_BACK = 4;

	public static final CustomPayload.Id<CatalogPosePayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("catalog_pose"));
	public static final PacketCodec<RegistryByteBuf, CatalogPosePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, CatalogPosePayload::entityId,
			PacketCodecs.BYTE, CatalogPosePayload::action,
			PacketCodecs.VAR_INT, CatalogPosePayload::trade,
			BlockPos.PACKET_CODEC, CatalogPosePayload::book,
			PacketCodecs.BOOL, CatalogPosePayload::hasBook,
			CatalogPosePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

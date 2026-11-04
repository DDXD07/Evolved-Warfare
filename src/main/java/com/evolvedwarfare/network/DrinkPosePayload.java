package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor → el que bebe y los que lo ven: empieza a beber con las manos (para la animación de un segundo). */
public record DrinkPosePayload(int entityId, byte pose) implements CustomPayload {
	/** Coge agua con las manos (de abajo) y se la lleva a la boca. */
	public static final byte SCOOP = 0;
	/** Mira al cielo con las manos juntas, recogiendo la lluvia. */
	public static final byte RAIN = 1;

	public static final CustomPayload.Id<DrinkPosePayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("drink_pose"));
	public static final PacketCodec<RegistryByteBuf, DrinkPosePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, DrinkPosePayload::entityId,
			PacketCodecs.BYTE, DrinkPosePayload::pose,
			DrinkPosePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

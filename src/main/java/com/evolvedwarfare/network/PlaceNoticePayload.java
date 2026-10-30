package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

/**
 * Servidor → cliente: el nombre del sitio al que acabas de llegar (ciudad, asentamiento…) y su línea de abajo. El cliente
 * lo pinta en pequeño, encima de los corazones y la barra de hambre, en vez del título grande de vanilla.
 */
public record PlaceNoticePayload(Text title, Text subtitle) implements CustomPayload {
	public static final CustomPayload.Id<PlaceNoticePayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("place_notice"));
	public static final PacketCodec<RegistryByteBuf, PlaceNoticePayload> CODEC = PacketCodec.tuple(
			TextCodecs.REGISTRY_PACKET_CODEC, PlaceNoticePayload::title,
			TextCodecs.REGISTRY_PACKET_CODEC, PlaceNoticePayload::subtitle,
			PlaceNoticePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Se lo enseña al jugador (si su juego no tiene el mod, como título de vanilla). */
	public static void send(ServerPlayerEntity player, Text title, Text subtitle) {
		if (ServerPlayNetworking.canSend(player, ID)) {
			ServerPlayNetworking.send(player, new PlaceNoticePayload(title, subtitle));
		} else {
			player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
			player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
			player.networkHandler.sendPacket(new TitleS2CPacket(title));
		}
	}
}

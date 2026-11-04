package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Servidor → cliente, cada segundo: la temperatura del cuerpo y la del aire donde está, la estación, qué día de la
 * estación es y cuántos dura, y si la temperatura y las estaciones están activadas.
 */
public record ClimatePayload(float body, float air, int season, int day, int length, boolean enabled, boolean seasons) implements CustomPayload {
	public static final CustomPayload.Id<ClimatePayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("climate"));
	public static final PacketCodec<RegistryByteBuf, ClimatePayload> CODEC = PacketCodec.of(ClimatePayload::write, ClimatePayload::read);

	private void write(RegistryByteBuf buf) {
		buf.writeFloat(body);
		buf.writeFloat(air);
		buf.writeVarInt(season);
		buf.writeVarInt(day);
		buf.writeVarInt(length);
		buf.writeBoolean(enabled);
		buf.writeBoolean(seasons);
	}

	private static ClimatePayload read(RegistryByteBuf buf) {
		return new ClimatePayload(buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
				buf.readBoolean());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public static void send(ServerPlayerEntity player, ClimatePayload payload) {
		if (ServerPlayNetworking.canSend(player, ID)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}

package com.evolvedwarfare.fishing;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Los mensajes del minijuego de pesca. */
public final class FishingPayloads {
	private FishingPayloads() {
	}

	/** Servidor → cliente: ha picado (empieza el minijuego). */
	public record Start(int session, ItemStack fish, int difficulty, int behavior, float zoneSize, boolean treasure, int level, long seed)
			implements CustomPayload {
		public static final Id<Start> ID = new Id<>(EvolvedWarfare.id("fishing_start"));
		public static final PacketCodec<RegistryByteBuf, Start> CODEC = PacketCodec.of(Start::write, Start::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(session);
			ItemStack.PACKET_CODEC.encode(buf, fish);
			buf.writeVarInt(difficulty);
			buf.writeVarInt(behavior);
			buf.writeFloat(zoneSize);
			buf.writeBoolean(treasure);
			buf.writeVarInt(level);
			buf.writeLong(seed);
		}

		private static Start read(RegistryByteBuf buf) {
			return new Start(buf.readVarInt(), ItemStack.PACKET_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readBoolean(),
					buf.readVarInt(), buf.readLong());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Cliente → servidor: cómo ha acabado. */
	public record Result(int session, boolean caught, boolean perfect, boolean treasure) implements CustomPayload {
		public static final Id<Result> ID = new Id<>(EvolvedWarfare.id("fishing_result"));
		public static final PacketCodec<RegistryByteBuf, Result> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, Result::session,
				PacketCodecs.BOOL, Result::caught, PacketCodecs.BOOL, Result::perfect, PacketCodecs.BOOL, Result::treasure, Result::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Servidor → cliente: se acabó sin ti (se te soltó la caña, el anzuelo se fue...). */
	public record End(int session) implements CustomPayload {
		public static final Id<End> ID = new Id<>(EvolvedWarfare.id("fishing_end"));
		public static final PacketCodec<RegistryByteBuf, End> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, End::session, End::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}

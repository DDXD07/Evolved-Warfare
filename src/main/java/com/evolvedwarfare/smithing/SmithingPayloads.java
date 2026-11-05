package com.evolvedwarfare.smithing;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Los mensajes de la forja en el yunque. */
public final class SmithingPayloads {
	private SmithingPayloads() {
	}

	/** Servidor → cliente: abre la forja (metal del lingote al rojo, cuántos, diamantes que llevas, nivel). */
	public record Open(int session, int material, int ingots, int diamonds, int level, long seed) implements CustomPayload {
		public static final Id<Open> ID = new Id<>(EvolvedWarfare.id("forge_open"));
		public static final PacketCodec<RegistryByteBuf, Open> CODEC = PacketCodec.of((p, buf) -> {
			buf.writeVarInt(p.session);
			buf.writeVarInt(p.material);
			buf.writeVarInt(p.ingots);
			buf.writeVarInt(p.diamonds);
			buf.writeVarInt(p.level);
			buf.writeLong(p.seed);
		}, buf -> new Open(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readLong()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Cliente → servidor: lo forjado (la pieza, si con diamante, y la puntería media), o -1 si se cancela. */
	public record Result(int session, int part, boolean diamond, float score) implements CustomPayload {
		public static final Id<Result> ID = new Id<>(EvolvedWarfare.id("forge_result"));
		public static final PacketCodec<RegistryByteBuf, Result> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, Result::session, PacketCodecs.VAR_INT,
				Result::part, PacketCodecs.BOOL, Result::diamond, PacketCodecs.FLOAT, Result::score, Result::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}

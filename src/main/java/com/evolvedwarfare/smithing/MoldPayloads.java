package com.evolvedwarfare.smithing;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Los mensajes del molde: abrir el minijuego de tallar (servidor → cliente) y lo tallado (cliente → servidor). */
public final class MoldPayloads {
	private MoldPayloads() {
	}

	public record Open(BlockPos pos) implements CustomPayload {
		public static final Id<Open> ID = new Id<>(EvolvedWarfare.id("mold_open"));
		public static final PacketCodec<RegistryByteBuf, Open> CODEC = PacketCodec.of((p, buf) -> buf.writeBlockPos(p.pos), buf -> new Open(buf.readBlockPos()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** La pieza elegida, las casillas talladas (16x16, por bits) y lo bien que ha salido (0-1). */
	public record Carve(BlockPos pos, int part, long[] bits, float score) implements CustomPayload {
		public static final Id<Carve> ID = new Id<>(EvolvedWarfare.id("mold_carve"));
		public static final PacketCodec<RegistryByteBuf, Carve> CODEC = PacketCodec.of((p, buf) -> {
			buf.writeBlockPos(p.pos);
			buf.writeVarInt(p.part);
			for (int i = 0; i < 4; i++) {
				buf.writeLong(p.bits[i]);
			}
			buf.writeFloat(p.score);
		}, buf -> new Carve(buf.readBlockPos(), buf.readVarInt(), new long[] {buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong()},
				buf.readFloat()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}

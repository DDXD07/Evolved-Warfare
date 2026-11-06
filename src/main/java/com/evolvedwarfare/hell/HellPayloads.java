package com.evolvedwarfare.hell;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Los mensajes del Nether bíblico: la corrupción del jugador y las visiones (una silueta en un sitio). */
public final class HellPayloads {
	private HellPayloads() {
	}

	public record Corruption(int value) implements CustomPayload {
		public static final Id<Corruption> ID = new Id<>(EvolvedWarfare.id("corruption"));
		public static final PacketCodec<RegistryByteBuf, Corruption> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, Corruption::value, Corruption::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Una silueta que aparece ahí unos segundos (y se va si la miras). */
	public record Vision(double x, double y, double z) implements CustomPayload {
		public static final Id<Vision> ID = new Id<>(EvolvedWarfare.id("vision"));
		public static final PacketCodec<RegistryByteBuf, Vision> CODEC = PacketCodec.tuple(PacketCodecs.DOUBLE, Vision::x, PacketCodecs.DOUBLE, Vision::y,
				PacketCodecs.DOUBLE, Vision::z, Vision::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}

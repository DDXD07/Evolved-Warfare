package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Los engramas por red (fase 113): qué desbloquea cada uno (al cliente) y aprender uno (al servidor). */
public final class EngramPayloads {
	private EngramPayloads() {
	}

	/** Servidor → cliente: los objetos de cada engrama (según las recetas del servidor) y si hacen falta. */
	public record Catalog(NbtCompound data) implements CustomPayload {
		public static final CustomPayload.Id<Catalog> ID = new CustomPayload.Id<>(EvolvedWarfare.id("engram_catalog"));
		public static final PacketCodec<RegistryByteBuf, Catalog> CODEC = PacketCodec.tuple(
				PacketCodecs.NBT_COMPOUND, Catalog::data,
				Catalog::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Cliente → servidor: aprender un engrama. */
	public record Learn(String engram) implements CustomPayload {
		public static final CustomPayload.Id<Learn> ID = new CustomPayload.Id<>(EvolvedWarfare.id("engram_learn"));
		public static final PacketCodec<RegistryByteBuf, Learn> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Learn::engram,
				Learn::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}

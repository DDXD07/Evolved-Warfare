package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** El árbol de habilidades por red: lo del jugador (al cliente) y desbloquear una ventaja (al servidor). */
public final class SkillPayloads {
	private SkillPayloads() {
	}

	/** Servidor → cliente: sus habilidades ('open' abre la pantalla; 'required': hace falta la ventaja para fabricar). */
	public record Data(NbtCompound data, boolean open, boolean required) implements CustomPayload {
		public static final CustomPayload.Id<Data> ID = new CustomPayload.Id<>(EvolvedWarfare.id("skills"));
		public static final PacketCodec<RegistryByteBuf, Data> CODEC = PacketCodec.tuple(
				PacketCodecs.NBT_COMPOUND, Data::data,
				PacketCodecs.BOOL, Data::open,
				PacketCodecs.BOOL, Data::required,
				Data::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Cliente → servidor: coger (o subir de rango) una ventaja. */
	public record Unlock(String perk) implements CustomPayload {
		public static final CustomPayload.Id<Unlock> ID = new CustomPayload.Id<>(EvolvedWarfare.id("skill_unlock"));
		public static final PacketCodec<RegistryByteBuf, Unlock> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Unlock::perk,
				Unlock::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}

package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Mensajes de la investigación. */
public final class ResearchPayloads {
	private ResearchPayloads() {
	}

	/**
	 * Servidor → cliente: lo investigado, lo que está en curso (progreso 0..1 y tics que faltan a la velocidad de la
	 * mesa), la velocidad de la mesa (1 = normal), si hay que abrir la pantalla y si la investigación es obligatoria.
	 * 'desk' = la mesa (o ORIGIN si no).
	 */
	public record Data(boolean open, BlockPos desk, List<String> done, String current, float progress, int remaining, float speed,
			boolean required) implements CustomPayload {
		public static final Id<Data> ID = new Id<>(EvolvedWarfare.id("research_data"));
		public static final PacketCodec<RegistryByteBuf, Data> CODEC = PacketCodec.ofStatic((buf, data) -> {
			buf.writeBoolean(data.open());
			buf.writeBlockPos(data.desk());
			buf.writeVarInt(data.done().size());
			for (String tech : data.done()) {
				buf.writeString(tech);
			}
			buf.writeString(data.current());
			buf.writeFloat(data.progress());
			buf.writeVarInt(data.remaining());
			buf.writeFloat(data.speed());
			buf.writeBoolean(data.required());
		}, buf -> {
			boolean open = buf.readBoolean();
			BlockPos desk = buf.readBlockPos();
			int count = buf.readVarInt();
			List<String> done = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				done.add(buf.readString());
			}
			return new Data(open, desk, done, buf.readString(), buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readBoolean());
		});

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Cliente → servidor: empezar una técnica, cancelar la que está en curso o pedir datos frescos. */
	public record Action(int action, String tech, BlockPos desk) implements CustomPayload {
		public static final int START = 0;
		public static final int CANCEL = 1;
		public static final int REFRESH = 2;
		public static final Id<Action> ID = new Id<>(EvolvedWarfare.id("research_action"));
		public static final PacketCodec<RegistryByteBuf, Action> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, Action::action,
				PacketCodecs.STRING, Action::tech,
				BlockPos.PACKET_CODEC, Action::desk,
				Action::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}

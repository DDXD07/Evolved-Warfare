package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.blueprint.BlueprintLibrary;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Paquetes de los planos: escáner, mesa del arquitecto y datos para la vista previa. */
public final class BlueprintPayloads {
	private BlueprintPayloads() {
	}

	static final PacketCodec<RegistryByteBuf, BlueprintLibrary.Summary> SUMMARY = PacketCodec.ofStatic(
			(buf, s) -> {
				PacketCodecs.STRING.encode(buf, s.id());
				PacketCodecs.STRING.encode(buf, s.name());
				PacketCodecs.STRING.encode(buf, s.author());
				PacketCodecs.BOOL.encode(buf, s.builtin());
				PacketCodecs.VAR_INT.encode(buf, s.sizeX());
				PacketCodecs.VAR_INT.encode(buf, s.sizeY());
				PacketCodecs.VAR_INT.encode(buf, s.sizeZ());
				PacketCodecs.VAR_INT.encode(buf, s.blocks());
			},
			buf -> new BlueprintLibrary.Summary(PacketCodecs.STRING.decode(buf), PacketCodecs.STRING.decode(buf),
					PacketCodecs.STRING.decode(buf), PacketCodecs.BOOL.decode(buf), PacketCodecs.VAR_INT.decode(buf),
					PacketCodecs.VAR_INT.decode(buf), PacketCodecs.VAR_INT.decode(buf), PacketCodecs.VAR_INT.decode(buf)));

	/** Cliente → servidor: escanear la caja del escáner que llevas en la mano. */
	public record Scan(String name, int front, boolean mainHand) implements CustomPayload {
		public static final Id<Scan> ID = new Id<>(EvolvedWarfare.id("blueprint_scan"));
		public static final PacketCodec<RegistryByteBuf, Scan> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Scan::name,
				PacketCodecs.VAR_INT, Scan::front,
				PacketCodecs.BOOL, Scan::mainHand,
				Scan::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Servidor → cliente: la lista de planos de la mesa del arquitecto (con 'open', abre la pantalla). */
	public record Library(boolean open, BlockPos table, String player, boolean admin, List<BlueprintLibrary.Summary> entries)
			implements CustomPayload {
		public static final Id<Library> ID = new Id<>(EvolvedWarfare.id("blueprint_library"));
		public static final PacketCodec<RegistryByteBuf, Library> CODEC = PacketCodec.tuple(
				PacketCodecs.BOOL, Library::open,
				BlockPos.PACKET_CODEC, Library::table,
				PacketCodecs.STRING, Library::player,
				PacketCodecs.BOOL, Library::admin,
				SUMMARY.collect(PacketCodecs.toList()), Library::entries,
				Library::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Cliente → servidor: pide los bloques de un plano (para la vista previa). */
	public record Request(String id) implements CustomPayload {
		public static final Id<Request> ID = new Id<>(EvolvedWarfare.id("blueprint_request"));
		public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.tuple(PacketCodecs.STRING, Request::id, Request::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Servidor → cliente: los bloques de un plano. */
	public record Data(String id, int sizeX, int sizeY, int sizeZ, List<String> palette, List<Integer> blocks) implements CustomPayload {
		public static final Id<Data> ID = new Id<>(EvolvedWarfare.id("blueprint_data"));
		public static final PacketCodec<RegistryByteBuf, Data> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Data::id,
				PacketCodecs.VAR_INT, Data::sizeX,
				PacketCodecs.VAR_INT, Data::sizeY,
				PacketCodecs.VAR_INT, Data::sizeZ,
				PacketCodecs.STRING.collect(PacketCodecs.toList()), Data::palette,
				PacketCodecs.VAR_INT.collect(PacketCodecs.toList()), Data::blocks,
				Data::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Cliente → servidor: imprimir o borrar un plano desde la mesa. */
	public record Action(int action, String id, BlockPos table) implements CustomPayload {
		public static final int PRINT = 0;
		public static final int DELETE = 1;
		public static final Id<Action> ID = new Id<>(EvolvedWarfare.id("blueprint_action"));
		public static final PacketCodec<RegistryByteBuf, Action> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, Action::action,
				PacketCodecs.STRING, Action::id,
				BlockPos.PACKET_CODEC, Action::table,
				Action::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}

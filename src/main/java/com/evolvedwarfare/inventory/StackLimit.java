package com.evolvedwarfare.inventory;

import com.evolvedwarfare.EvolvedWarfare;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Lo que cabe en un hueco (opción "stackLimit" de la configuración): 64 como siempre, o 100, 500, 1000 o 5000 para todo lo
 * que se apila (lo que no se apila, como herramientas y armaduras, sigue de uno en uno). El servidor manda el valor a cada
 * jugador al entrar y cuando cambia, para que su juego cuente igual.
 */
public final class StackLimit {
	public static final int STANDARD = 64;
	private static volatile int value = STANDARD;
	private static MinecraftServer server;

	private StackLimit() {
	}

	public record StackLimitPayload(int limit) implements CustomPayload {
		public static final Id<StackLimitPayload> ID = new Id<>(EvolvedWarfare.id("stack_limit"));
		public static final PacketCodec<RegistryByteBuf, StackLimitPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, StackLimitPayload::limit,
				StackLimitPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(StackLimitPayload.ID, StackLimitPayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			value = Math.max(STANDARD, com.evolvedwarfare.config.EwConfig.get().stackLimit);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> send(handler.player));
	}

	/** Lo que cabe en un hueco ahora (64 = como siempre). */
	public static int get() {
		return value;
	}

	/** Lo pone el cliente al recibirlo del servidor. */
	public static void setFromServer(int limit) {
		value = Math.max(STANDARD, limit);
	}

	/** Ha cambiado la configuración. */
	public static void changed(int limit) {
		value = Math.max(STANDARD, limit);
		MinecraftServer s = server;
		if (s != null) {
			s.execute(() -> s.getPlayerManager().getPlayerList().forEach(StackLimit::send));
		}
	}

	private static void send(ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, StackLimitPayload.ID)) {
			ServerPlayNetworking.send(player, new StackLimitPayload(value));
		}
	}

	/** Lo que cabe de algo que de normal cabe "base" (1 = no se apila, y sigue igual). */
	public static int apply(int base) {
		int v = value;
		return base <= 1 || v <= STANDARD ? base : Math.max(base, v);
	}

	/** Lo que cabe en un inventario (de normal, 99). */
	public static int inventory(int base) {
		int v = value;
		return v <= STANDARD ? base : Math.max(base, v);
	}

	/** Para que quepa en la casilla: 1500 → "1.5k", 5000 → "5k". */
	public static String shortCount(int count) {
		if (count < 1000) {
			return Integer.toString(count);
		}
		int tenths = count / 100;
		return tenths % 10 == 0 ? (tenths / 10) + "k" : (tenths / 10) + "." + (tenths % 10) + "k";
	}
}

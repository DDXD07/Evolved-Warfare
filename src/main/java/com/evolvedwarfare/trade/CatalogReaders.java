package com.evolvedwarfare.trade;

import com.evolvedwarfare.network.CatalogPosePayload;
import com.evolvedwarfare.network.CatalogTurnPayload;
import com.evolvedwarfare.screen.CatalogScreenHandler;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Quién está leyendo un catálogo de mercader (en el servidor), para que los demás jugadores lo vean en tiempo real: con
 * el libro abierto en las manos, la cabeza baja y pasando las páginas. A cada uno que lo ve se le manda al abrirlo, al
 * cerrarlo, al pasar una página y, si llega cuando ya lo estaba leyendo, al empezar a verlo. El que lee no recibe nada
 * (lo ve en primera persona).
 */
public final class CatalogReaders {
	/** Lo poco que hace falta pasar las páginas sin inundar la red (ms). */
	private static final long MIN_TURN_MS = 150L;

	/** Uno que lee: la portada (oficio) y el libro del mostrador que ha cogido (o null). */
	public record Reader(int trade, @Nullable BlockPos book) {
	}

	private static final Map<UUID, Reader> READERS = new HashMap<>();
	private static final Map<UUID, Long> LAST_TURN = new HashMap<>();

	private CatalogReaders() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(CatalogTurnPayload.ID, (payload, context) -> turn(context.player(), payload.forward()));
		EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
			if (tracked instanceof ServerPlayerEntity reader && reader != viewer) {
				Reader r = READERS.get(reader.getUuid());
				if (r != null) {
					send(viewer, payload(reader, CatalogPosePayload.READING, r));
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> close(handler.getPlayer()));
		// por si se cierra de una forma que no pasa por el menú (muerte, cambio de dimensión, otro menú…)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (READERS.isEmpty() || server.getTicks() % 10 != 0) {
				return;
			}
			Iterator<Map.Entry<UUID, Reader>> it = READERS.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Reader> entry = it.next();
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
				if (player == null || player.isRemoved() || !(player.currentScreenHandler instanceof CatalogScreenHandler)) {
					it.remove();
					LAST_TURN.remove(entry.getKey());
					if (player != null) {
						broadcast(player, payload(player, CatalogPosePayload.CLOSE, entry.getValue()));
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			READERS.clear();
			LAST_TURN.clear();
		});
	}

	/** ¿Está leyendo un catálogo? */
	public static boolean reading(PlayerEntity player) {
		return READERS.containsKey(player.getUuid());
	}

	@Nullable
	public static Reader of(PlayerEntity player) {
		return READERS.get(player.getUuid());
	}

	/** Ha abierto el catálogo (con el libro del mostrador, si lo hay). */
	public static void open(ServerPlayerEntity player, int trade, @Nullable BlockPos book) {
		Reader r = new Reader(trade, book != null ? book.toImmutable() : null);
		READERS.put(player.getUuid(), r);
		LAST_TURN.remove(player.getUuid());
		broadcast(player, payload(player, CatalogPosePayload.OPEN, r));
		player.getWorld().playSound(player, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN,
				SoundCategory.PLAYERS, 0.8F, 0.9F);
	}

	/** Lo ha cerrado (o se ha ido). */
	public static void close(ServerPlayerEntity player) {
		Reader r = READERS.remove(player.getUuid());
		LAST_TURN.remove(player.getUuid());
		if (r != null) {
			broadcast(player, payload(player, CatalogPosePayload.CLOSE, r));
			if (!player.isRemoved()) {
				player.getWorld().playSound(player, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ITEM_BOOK_PUT,
						SoundCategory.PLAYERS, 0.8F, 1.0F);
			}
		}
	}

	/** Ha pasado una página (solo si de verdad tiene un catálogo abierto). */
	public static void turn(ServerPlayerEntity player, boolean forward) {
		Reader r = READERS.get(player.getUuid());
		if (r == null || !(player.currentScreenHandler instanceof CatalogScreenHandler)) {
			return;
		}
		long now = System.currentTimeMillis();
		Long last = LAST_TURN.get(player.getUuid());
		if (last != null && now - last < MIN_TURN_MS) {
			return;
		}
		LAST_TURN.put(player.getUuid(), now);
		broadcast(player, payload(player, forward ? CatalogPosePayload.TURN_FORWARD : CatalogPosePayload.TURN_BACK, r));
		player.getWorld().playSound(player, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN,
				SoundCategory.PLAYERS, 0.6F, 0.9F + player.getRandom().nextFloat() * 0.2F);
	}

	private static CatalogPosePayload payload(ServerPlayerEntity player, byte action, Reader r) {
		return new CatalogPosePayload(player.getId(), action, r.trade(), r.book() != null ? r.book() : BlockPos.ORIGIN, r.book() != null);
	}

	private static void broadcast(ServerPlayerEntity reader, CatalogPosePayload payload) {
		for (ServerPlayerEntity other : PlayerLookup.tracking(reader)) {
			if (other != reader) {
				send(other, payload);
			}
		}
	}

	private static void send(ServerPlayerEntity to, CatalogPosePayload payload) {
		if (ServerPlayNetworking.canSend(to, CatalogPosePayload.ID)) {
			ServerPlayNetworking.send(to, payload);
		}
	}
}

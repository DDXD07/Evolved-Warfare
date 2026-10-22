package com.evolvedwarfare.network;

import com.evolvedwarfare.screen.WarehouseScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Paquetes propios del mod (solo los del terminal del almacén; el resto de menús usa los botones de vanilla). */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(WarehouseContentsPayload.ID, WarehouseContentsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(WarehouseActionPayload.ID, WarehouseActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(WarehouseActionPayload.ID, (payload, context) -> {
			if (context.player().currentScreenHandler instanceof WarehouseScreenHandler handler
					&& handler.syncId == payload.syncId()) {
				handler.handleAction(context.player(), payload.action(), payload.prototype());
			}
		});
	}
}

package com.evolvedwarfare.network;

import com.evolvedwarfare.screen.WarehouseScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Paquetes propios del mod: el terminal del almacén, la diplomacia y las armas de fuego (el resto de menús usa los botones de vanilla). */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(WarehouseContentsPayload.ID, WarehouseContentsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(WarehouseActionPayload.ID, WarehouseActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DiplomacyDataPayload.ID, DiplomacyDataPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DiplomacyActionPayload.ID, DiplomacyActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ResearchPayloads.Data.ID, ResearchPayloads.Data.CODEC);
		PayloadTypeRegistry.playC2S().register(ResearchPayloads.Action.ID, ResearchPayloads.Action.CODEC);
		PayloadTypeRegistry.playC2S().register(FirearmPayload.ID, FirearmPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FirearmPayload.ID, (payload, context) -> {
			if (payload.action() == FirearmPayload.FIRE) {
				com.evolvedwarfare.weapon.MusketItem.tryFire(context.player());
			} else if (payload.action() == FirearmPayload.RELOAD) {
				com.evolvedwarfare.weapon.MusketItem.tryReload(context.player());
			}
		});
		DiplomacyNetworking.register();
		com.evolvedwarfare.research.Research.register();
		BlueprintNetworking.register();
		ServerPlayNetworking.registerGlobalReceiver(WarehouseActionPayload.ID, (payload, context) -> {
			if (context.player().currentScreenHandler instanceof WarehouseScreenHandler handler
					&& handler.syncId == payload.syncId()) {
				handler.handleAction(context.player(), payload.action(), payload.prototype());
			}
		});
	}
}

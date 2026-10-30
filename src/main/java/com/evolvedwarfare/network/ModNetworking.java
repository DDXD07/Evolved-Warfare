package com.evolvedwarfare.network;

import com.evolvedwarfare.screen.WarehouseScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Paquetes propios del mod: el terminal del almacén, la diplomacia, el árbol de habilidades, las armas de fuego y la cámara aérea de las máquinas de asedio (el resto de menús usa los botones de vanilla). */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(WarehouseContentsPayload.ID, WarehouseContentsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(WarehouseActionPayload.ID, WarehouseActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DiplomacyDataPayload.ID, DiplomacyDataPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DiplomacyActionPayload.ID, DiplomacyActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FirearmPayload.ID, FirearmPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PlaceNoticePayload.ID, PlaceNoticePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FirearmPayload.ID, (payload, context) -> {
			if (payload.action() == FirearmPayload.FIRE) {
				com.evolvedwarfare.weapon.MusketItem.tryFire(context.player());
			} else if (payload.action() == FirearmPayload.RELOAD) {
				com.evolvedwarfare.weapon.MusketItem.tryReload(context.player());
			}
		});
		PayloadTypeRegistry.playC2S().register(SiegeAimPayload.ID, SiegeAimPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SiegeAimPayload.ID, (payload, context) -> {
			if (context.player().getVehicle() instanceof com.evolvedwarfare.siege.SiegeEngineEntity engine) {
				engine.setPilotAim(context.player(), payload.active()
						? new net.minecraft.util.math.Vec3d(payload.x(), payload.y(), payload.z()) : null);
			}
		});
		DiplomacyNetworking.register();
		com.evolvedwarfare.skill.Skills.register();
		BlueprintNetworking.register();
		ServerPlayNetworking.registerGlobalReceiver(WarehouseActionPayload.ID, (payload, context) -> {
			if (context.player().currentScreenHandler instanceof WarehouseScreenHandler handler
					&& handler.syncId == payload.syncId()) {
				handler.handleAction(context.player(), payload.action(), payload.prototype());
			}
		});
	}
}

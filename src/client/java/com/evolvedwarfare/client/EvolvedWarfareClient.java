package com.evolvedwarfare.client;

import com.evolvedwarfare.client.render.FactionNpcRenderer;
import com.evolvedwarfare.client.render.ModModelLayers;
import com.evolvedwarfare.client.render.RecruitRenderer;
import com.evolvedwarfare.client.screen.BlueprintScreen;
import com.evolvedwarfare.client.screen.NpcScreen;
import com.evolvedwarfare.client.screen.RouteSlipScreen;
import com.evolvedwarfare.client.screen.ShopScreen;
import com.evolvedwarfare.client.screen.WarehouseScreen;
import com.evolvedwarfare.client.screen.WorkSiteScreen;
import com.evolvedwarfare.client.screen.DiplomacyScreen;
import com.evolvedwarfare.entity.BanditEntity;
import com.evolvedwarfare.entity.PirateEntity;
import com.evolvedwarfare.entity.VikingEntity;
import com.evolvedwarfare.network.DiplomacyDataPayload;
import com.evolvedwarfare.network.WarehouseContentsPayload;
import com.evolvedwarfare.screen.WarehouseScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModScreens;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;

public class EvolvedWarfareClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModModelLayers.init();
		EntityRendererRegistry.register(ModEntities.RECRUIT, RecruitRenderer::new);
		EntityRendererRegistry.register(ModEntities.SOLDIER, RecruitRenderer::new);
		EntityRendererRegistry.register(ModEntities.WORKER, RecruitRenderer::new);
		EntityRendererRegistry.register(ModEntities.BANDIT, context -> new FactionNpcRenderer<>(context, "bandit", BanditEntity.VARIANTS));
		EntityRendererRegistry.register(ModEntities.VIKING, context -> new FactionNpcRenderer<>(context, "viking", VikingEntity.VARIANTS));
		EntityRendererRegistry.register(ModEntities.PIRATE, context -> new FactionNpcRenderer<>(context, "pirate", PirateEntity.VARIANTS));
		HandledScreens.register(ModScreens.NPC, NpcScreen::new);
		HandledScreens.register(ModScreens.WORK_SITE, WorkSiteScreen::new);
		HandledScreens.register(ModScreens.BLUEPRINT, BlueprintScreen::new);
		HandledScreens.register(ModScreens.WAREHOUSE, WarehouseScreen::new);
		HandledScreens.register(ModScreens.ROUTE_SLIP, RouteSlipScreen::new);
		HandledScreens.register(ModScreens.SHOP, ShopScreen::new);
		// lo que hay en la red del almacén abierto
		ClientPlayNetworking.registerGlobalReceiver(WarehouseContentsPayload.ID, (payload, context) -> {
			if (context.client().player != null
					&& context.client().player.currentScreenHandler instanceof WarehouseScreenHandler handler
					&& handler.syncId == payload.syncId()) {
				handler.setClientContents(payload.containers(), payload.entries());
			}
		});
		// reputación con las facciones (y abrir la diplomacia)
		ClientPlayNetworking.registerGlobalReceiver(DiplomacyDataPayload.ID, (payload, context) -> {
			ClientDiplomacy.update(payload.factions());
			if (payload.open()) {
				context.client().setScreen(new DiplomacyScreen());
			}
		});
		// el dibujo del frente de los puestos tiene transparencias
		for (Block block : ModBlocks.WORK_SITES.values()) {
			BlockRenderLayerMap.INSTANCE.putBlock(block, RenderLayer.getCutout());
		}
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TOWN_HALL, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.WAREHOUSE, RenderLayer.getCutout());
	}
}

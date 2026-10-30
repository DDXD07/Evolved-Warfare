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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;

public class EvolvedWarfareClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModModelLayers.init();
		EntityRendererRegistry.register(ModEntities.RECRUIT, RecruitRenderer::new);
		EntityRendererRegistry.register(ModEntities.SOLDIER, RecruitRenderer::new);
		EntityRendererRegistry.register(ModEntities.WORKER, RecruitRenderer::new);
		EntityRendererRegistry.register(ModEntities.TRADER, com.evolvedwarfare.client.render.TraderRenderer::new);
		EntityRendererRegistry.register(ModEntities.BANDIT, context -> new FactionNpcRenderer<>(context, "bandit", BanditEntity.VARIANTS));
		EntityRendererRegistry.register(ModEntities.VIKING, context -> new FactionNpcRenderer<>(context, "viking", VikingEntity.VARIANTS));
		EntityRendererRegistry.register(ModEntities.PIRATE, context -> new FactionNpcRenderer<>(context, "pirate", PirateEntity.VARIANTS));
		// asedio (fase 7)
		for (com.evolvedwarfare.siege.SiegeKind kind : com.evolvedwarfare.siege.SiegeKind.values()) {
			EntityRendererRegistry.register(ModEntities.engineType(kind),
					context -> com.evolvedwarfare.client.render.SiegeEngineRenderer.forKind(context, kind));
		}
		EntityRendererRegistry.register(ModEntities.SIEGE_SHOT, com.evolvedwarfare.client.render.SiegeShotRenderer::new);
		// fase 8: jabalina, arco largo (se tensa como el arco) y pantalla de investigación
		EntityRendererRegistry.register(ModEntities.JAVELIN, com.evolvedwarfare.client.render.JavelinEntityRenderer::new);
		// fase 10: mosquete (modelo 3D animado) y su bala
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.evolvedwarfare.client.render.musket.MusketModel.LAYER, com.evolvedwarfare.client.render.musket.MusketModel::getTexturedModelData);
		net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(com.evolvedwarfare.registry.ModItems.MUSKET,
				new com.evolvedwarfare.client.render.musket.MusketItemRenderer());
		MusketControls.register();
		EntityRendererRegistry.register(ModEntities.MUSKET_BALL,
				context -> new net.minecraft.client.render.entity.FlyingItemEntityRenderer<>(context, 0.5F, true));
		net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry.register(
				com.evolvedwarfare.registry.ModItems.LONGBOW, net.minecraft.util.Identifier.ofVanilla("pull"), (stack, world, entity, seed) ->
						entity == null || entity.getActiveItem() != stack ? 0.0F
								: (stack.getMaxUseTime(entity) - entity.getItemUseTimeLeft()) / 30.0F);
		net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry.register(
				com.evolvedwarfare.registry.ModItems.LONGBOW, net.minecraft.util.Identifier.ofVanilla("pulling"), (stack, world, entity, seed) ->
						entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F);
		net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry.register(
				com.evolvedwarfare.registry.ModItems.JAVELIN, net.minecraft.util.Identifier.ofVanilla("throwing"), (stack, world, entity, seed) ->
						entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F);
		ClientResearch.register();
		// máquinas de asedio: sus modelos 3D, la cámara aérea (clic izquierdo mantenido) y la vista previa del tiro
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.evolvedwarfare.client.render.siege.SiegeModels.CATAPULT, com.evolvedwarfare.client.render.siege.SiegeModels::catapult);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.evolvedwarfare.client.render.siege.SiegeModels.BALLISTA, com.evolvedwarfare.client.render.siege.SiegeModels::ballista);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.evolvedwarfare.client.render.siege.SiegeModels.TREBUCHET, com.evolvedwarfare.client.render.siege.SiegeModels::trebuchet);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
				com.evolvedwarfare.client.render.siege.SiegeModels.RAM, com.evolvedwarfare.client.render.siege.SiegeModels::ram);
		SiegeAerialCamera.register();
		HandledScreens.register(ModScreens.NPC, NpcScreen::new);
		HandledScreens.register(ModScreens.WORK_SITE, WorkSiteScreen::new);
		HandledScreens.register(ModScreens.BLUEPRINT, BlueprintScreen::new);
		HandledScreens.register(ModScreens.WAREHOUSE, WarehouseScreen::new);
		HandledScreens.register(ModScreens.ROUTE_SLIP, RouteSlipScreen::new);
		HandledScreens.register(ModScreens.SHOP, ShopScreen::new);
		HandledScreens.register(ModScreens.TRADER, com.evolvedwarfare.client.screen.TraderScreen::new);
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
		// planos: el escáner abre su pantalla, la mesa del arquitecto su biblioteca, y llegan los planos para la vista previa
		com.evolvedwarfare.ClientBridge.openScanner = hand -> MinecraftClient.getInstance().setScreen(
				new com.evolvedwarfare.client.screen.ScanScreen(hand));
		ClientPlayNetworking.registerGlobalReceiver(com.evolvedwarfare.network.BlueprintPayloads.Library.ID, (payload, context) -> {
			com.evolvedwarfare.client.blueprint.ClientBlueprints.library(payload);
			if (payload.open()) {
				context.client().setScreen(new com.evolvedwarfare.client.screen.ArchitectScreen());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(com.evolvedwarfare.network.BlueprintPayloads.Data.ID,
				(payload, context) -> com.evolvedwarfare.client.blueprint.ClientBlueprints.data(payload));
		// el dibujo del frente de los puestos tiene transparencias
		for (Block block : ModBlocks.WORK_SITES.values()) {
			BlockRenderLayerMap.INSTANCE.putBlock(block, RenderLayer.getCutout());
		}
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TOWN_HALL, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.WAREHOUSE, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.PORTCULLIS, RenderLayer.getCutout());
	}
}

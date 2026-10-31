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
		// armas cuerpo a cuerpo: modelos 3D en la mano (el icono plano en el inventario) y su golpe animado
		java.util.List<net.minecraft.util.Identifier> meleeIcons = new java.util.ArrayList<>();
		for (com.evolvedwarfare.weapon.WeaponType type : com.evolvedwarfare.weapon.WeaponType.values()) {
			net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
					com.evolvedwarfare.client.render.melee.MeleeModels.layer(type), () -> com.evolvedwarfare.client.render.melee.MeleeModels.of(type));
			java.util.List<net.minecraft.item.Item> items = com.evolvedwarfare.registry.ModItems.WEAPONS.get(type);
			for (int i = 0; i < items.size(); i++) {
				String material = com.evolvedwarfare.registry.ModItems.WEAPON_MATERIALS[i];
				net.minecraft.util.Identifier icon = com.evolvedwarfare.EvolvedWarfare.id("item/" + material + "_" + type.id() + "_icon");
				meleeIcons.add(icon);
				net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(items.get(i),
						new com.evolvedwarfare.client.render.melee.MeleeWeaponRenderer(type, material, icon));
			}
		}
		net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(plugin -> plugin.addModels(meleeIcons));
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
		ClientSkills.register();
		PlaceNoticeHud.register();
		DayClockHud.register();
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
		// caja de cultivos: su hueco, lo que lleva dentro (en el mundo y en el icono)
		HandledScreens.register(ModScreens.CROP_CRATE, com.evolvedwarfare.client.screen.CropCrateScreen::new);
		net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(ModBlocks.CROP_CRATE_ENTITY,
				com.evolvedwarfare.client.render.CropCrateRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.CROP_CRATE_ITEM,
				com.evolvedwarfare.client.render.CropCrateRenderer.ITEM);
		com.evolvedwarfare.client.render.CrateCarry.register();
		com.evolvedwarfare.client.render.melee.MeleeHeavy.register();
		// carreta: su modelo (con lo que lleva encima) y la pantalla de la carga
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.evolvedwarfare.registry.ModEntities.CART,
				com.evolvedwarfare.client.render.CartEntityRenderer::new);
		HandledScreens.register(ModScreens.CART, com.evolvedwarfare.client.screen.CartScreen::new);
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
		com.evolvedwarfare.ClientBridge.openGuide = () -> MinecraftClient.getInstance().setScreen(new com.evolvedwarfare.client.screen.GuideScreen());
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

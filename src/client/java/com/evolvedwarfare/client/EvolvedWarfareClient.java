package com.evolvedwarfare.client;

import com.evolvedwarfare.client.render.BanditRenderer;
import com.evolvedwarfare.client.render.ModModelLayers;
import com.evolvedwarfare.client.render.RecruitRenderer;
import com.evolvedwarfare.client.screen.BlueprintScreen;
import com.evolvedwarfare.client.screen.NpcScreen;
import com.evolvedwarfare.client.screen.WorkSiteScreen;
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
		EntityRendererRegistry.register(ModEntities.BANDIT, BanditRenderer::new);
		HandledScreens.register(ModScreens.NPC, NpcScreen::new);
		HandledScreens.register(ModScreens.WORK_SITE, WorkSiteScreen::new);
		HandledScreens.register(ModScreens.BLUEPRINT, BlueprintScreen::new);
		// el dibujo del frente de los puestos tiene transparencias
		for (Block block : ModBlocks.WORK_SITES.values()) {
			BlockRenderLayerMap.INSTANCE.putBlock(block, RenderLayer.getCutout());
		}
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TOWN_HALL, RenderLayer.getCutout());
	}
}

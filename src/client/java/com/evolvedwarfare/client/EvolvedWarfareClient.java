package com.evolvedwarfare.client;

import com.evolvedwarfare.client.render.ModModelLayers;
import com.evolvedwarfare.client.render.RecruitRenderer;
import com.evolvedwarfare.client.screen.NpcScreen;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModScreens;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class EvolvedWarfareClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModModelLayers.init();
		EntityRendererRegistry.register(ModEntities.RECRUIT, RecruitRenderer::new);
		HandledScreens.register(ModScreens.NPC, NpcScreen::new);
	}
}

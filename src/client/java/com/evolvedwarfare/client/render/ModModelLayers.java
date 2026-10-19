package com.evolvedwarfare.client.render;

import com.evolvedwarfare.EvolvedWarfare;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/** Capas de modelo propias: cuerpo de persona (como el jugador, con 2ª capa de skin) y armaduras. */
public final class ModModelLayers {
	public static final EntityModelLayer NPC = new EntityModelLayer(EvolvedWarfare.id("npc"), "main");
	public static final EntityModelLayer NPC_INNER_ARMOR = new EntityModelLayer(EvolvedWarfare.id("npc"), "inner_armor");
	public static final EntityModelLayer NPC_OUTER_ARMOR = new EntityModelLayer(EvolvedWarfare.id("npc"), "outer_armor");

	private ModModelLayers() {
	}

	public static void init() {
		EntityModelLayerRegistry.registerModelLayer(NPC,
				() -> TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64));
		// Mismas medidas que la armadura del jugador: 0,5 la interior (piernas) y 1,0 la exterior.
		EntityModelLayerRegistry.registerModelLayer(NPC_INNER_ARMOR,
				() -> TexturedModelData.of(ArmorEntityModel.getModelData(new Dilation(0.5F)), 64, 32));
		EntityModelLayerRegistry.registerModelLayer(NPC_OUTER_ARMOR,
				() -> TexturedModelData.of(ArmorEntityModel.getModelData(new Dilation(1.0F)), 64, 32));
	}
}

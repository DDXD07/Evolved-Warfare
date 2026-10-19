package com.evolvedwarfare;

import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.registry.ModScreens;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evolved Warfare — NPCs, ejércitos y asedio (inspirado en Ancient Warfare).
 *
 * <p>Fase 1: el recluta. Un NPC con dueño, equipo, órdenes básicas y paga diaria.</p>
 */
public class EvolvedWarfare implements ModInitializer {
	public static final String MOD_ID = "evolvedwarfare";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModEntities.init();
		ModItems.init();
		ModScreens.init();
		LOGGER.info("Evolved Warfare cargado");
	}
}

package com.evolvedwarfare;

import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.registry.ModScreens;
import com.evolvedwarfare.registry.ModStructures;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evolved Warfare — NPCs, ejércitos y asedio (inspirado en Ancient Warfare).
 *
 * <p>Fase 1: el recluta (NPC con dueño, equipo, órdenes y paga), las ciudades, campamentos y torres donde
 * vive, y las aldeas vanilla mejoradas (empedrado, farolas y empalizada).</p>
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
		ModStructures.init();
		LOGGER.info("Evolved Warfare cargado");
	}
}

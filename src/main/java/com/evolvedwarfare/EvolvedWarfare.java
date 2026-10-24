package com.evolvedwarfare;

import com.evolvedwarfare.command.ModCommands;
import com.evolvedwarfare.entity.NpcSides;
import com.evolvedwarfare.item.RouteSlipItem;
import com.evolvedwarfare.network.ModNetworking;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModEntities;
import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.registry.ModScreens;
import com.evolvedwarfare.registry.ModStructures;
import com.evolvedwarfare.world.FactionSpawner;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evolved Warfare — NPCs, ejércitos y asedio (inspirado en Ancient Warfare).
 *
 * <p>Fase 1: el recluta (NPC con dueño, equipo, órdenes y paga), las ciudades, campamentos y torres donde
 * vive, y las aldeas vanilla mejoradas (empedrado, farolas y empalizada).</p>
 *
 * <p>Fase 2: soldados (guerrero, bárbaro, arquero, ballestero, médico), bandidos hostiles con fuertes,
 * patrullas y asaltos, niveles y bastón de mando.</p>
 *
 * <p>Fase 3: trabajadores con 14 oficios, cada uno con su puesto de trabajo, y el ayuntamiento que les paga.</p>
 *
 * <p>Fase 4: logística y comercio: almacén en red, mensajeros (automáticos o con hoja de ruta) y comerciantes.</p>
 *
 * <p>Fase 5: vikingos y piratas, reputación con cada facción y diplomacia (tributos, treguas, alianzas, mercenarios).</p>
 */
public class EvolvedWarfare implements ModInitializer {
	public static final String MOD_ID = "evolvedwarfare";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModBlocks.init();
		ModEntities.init();
		ModItems.init();
		ModScreens.init();
		ModNetworking.init();
		ModStructures.init();
		NpcSides.register();
		FactionSpawner.register();
		ModCommands.register();
		RouteSlipItem.registerEvents();
		LOGGER.info("Evolved Warfare cargado");
	}
}

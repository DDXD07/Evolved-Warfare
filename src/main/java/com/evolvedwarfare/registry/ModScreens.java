package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.screen.BlueprintScreenHandler;
import com.evolvedwarfare.screen.NpcScreenHandler;
import com.evolvedwarfare.screen.RouteSlipScreenHandler;
import com.evolvedwarfare.screen.ShopScreenHandler;
import com.evolvedwarfare.screen.WarehouseScreenHandler;
import com.evolvedwarfare.screen.WorkSiteScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;

public final class ModScreens {
	/** Menú de un NPC. Al abrirse se envía al cliente el id de la entidad. */
	public static final ScreenHandlerType<NpcScreenHandler> NPC = Registry.register(Registries.SCREEN_HANDLER,
			EvolvedWarfare.id("npc"),
			new ExtendedScreenHandlerType<>(NpcScreenHandler::new, PacketCodecs.VAR_INT));

	/** Menú de un puesto de trabajo. Al abrirse se envía al cliente la posición del puesto. */
	public static final ScreenHandlerType<WorkSiteScreenHandler> WORK_SITE = Registry.register(Registries.SCREEN_HANDLER,
			EvolvedWarfare.id("work_site"),
			new ExtendedScreenHandlerType<>(WorkSiteScreenHandler::new, BlockPos.PACKET_CODEC));

	/** Menú del edificio de un puesto (vista previa y semillas). Al abrirse se envía la posición del puesto. */
	public static final ScreenHandlerType<BlueprintScreenHandler> BLUEPRINT = Registry.register(Registries.SCREEN_HANDLER,
			EvolvedWarfare.id("blueprint"),
			new ExtendedScreenHandlerType<>(BlueprintScreenHandler::new, BlockPos.PACKET_CODEC));

	/** Terminal del almacén. Al abrirse se envía la posición del almacén. */
	public static final ScreenHandlerType<WarehouseScreenHandler> WAREHOUSE = Registry.register(Registries.SCREEN_HANDLER,
			EvolvedWarfare.id("warehouse"),
			new ExtendedScreenHandlerType<>(WarehouseScreenHandler::new, BlockPos.PACKET_CODEC));

	/** Editor de la hoja de ruta. Al abrirse se envía con qué mano se lleva (0 principal, 1 secundaria). */
	public static final ScreenHandlerType<RouteSlipScreenHandler> ROUTE_SLIP = Registry.register(Registries.SCREEN_HANDLER,
			EvolvedWarfare.id("route_slip"),
			new ExtendedScreenHandlerType<RouteSlipScreenHandler, Integer>((syncId, inventory, hand) -> new RouteSlipScreenHandler(syncId, inventory, hand),
					PacketCodecs.VAR_INT));

	/** Tienda del comerciante. Al abrirse se envía la posición del puesto. */
	public static final ScreenHandlerType<ShopScreenHandler> SHOP = Registry.register(Registries.SCREEN_HANDLER,
			EvolvedWarfare.id("shop"),
			new ExtendedScreenHandlerType<>(ShopScreenHandler::new, BlockPos.PACKET_CODEC));

	private ModScreens() {
	}

	public static void init() {
	}
}

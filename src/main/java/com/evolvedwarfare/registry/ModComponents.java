package com.evolvedwarfare.registry;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.logistics.RouteData;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Datos propios que llevan los objetos. */
public final class ModComponents {
	/** Las paradas de una hoja de ruta. */
	public static final ComponentType<RouteData> ROUTE = Registry.register(Registries.DATA_COMPONENT_TYPE, EvolvedWarfare.id("route"),
			ComponentType.<RouteData>builder().codec(RouteData.CODEC).packetCodec(RouteData.PACKET_CODEC).build());

	private ModComponents() {
	}

	public static void init() {
	}
}

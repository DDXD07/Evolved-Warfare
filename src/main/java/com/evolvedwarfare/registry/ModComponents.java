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

	/** Las esquinas marcadas con el escáner de planos. */
	public static final ComponentType<com.evolvedwarfare.blueprint.ScanArea> SCAN_AREA = Registry.register(Registries.DATA_COMPONENT_TYPE,
			EvolvedWarfare.id("scan_area"),
			ComponentType.<com.evolvedwarfare.blueprint.ScanArea>builder().codec(com.evolvedwarfare.blueprint.ScanArea.CODEC)
					.packetCodec(com.evolvedwarfare.blueprint.ScanArea.PACKET_CODEC).build());

	/** Qué plano lleva un objeto Plano. */
	public static final ComponentType<com.evolvedwarfare.blueprint.BlueprintRef> BLUEPRINT = Registry.register(Registries.DATA_COMPONENT_TYPE,
			EvolvedWarfare.id("blueprint"),
			ComponentType.<com.evolvedwarfare.blueprint.BlueprintRef>builder().codec(com.evolvedwarfare.blueprint.BlueprintRef.CODEC)
					.packetCodec(com.evolvedwarfare.blueprint.BlueprintRef.PACKET_CODEC).build());

	private ModComponents() {
	}

	public static void init() {
	}
}

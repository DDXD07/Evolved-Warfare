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

	/** Las Glory Coins que guarda una Bolsa de Gloria. */
	public static final ComponentType<Integer> GLORY_BALANCE = Registry.register(Registries.DATA_COMPONENT_TYPE,
			EvolvedWarfare.id("glory_balance"),
			ComponentType.<Integer>builder().codec(net.minecraft.util.dynamic.Codecs.NONNEGATIVE_INT)
					.packetCodec(net.minecraft.network.codec.PacketCodecs.VAR_INT).build());

	/** Si un mosquete está cargado. */
	public static final ComponentType<Boolean> MUSKET_LOADED = Registry.register(Registries.DATA_COMPONENT_TYPE,
			EvolvedWarfare.id("musket_loaded"),
			ComponentType.<Boolean>builder().codec(com.mojang.serialization.Codec.BOOL)
					.packetCodec(net.minecraft.network.codec.PacketCodecs.BOOL).build());

	/** Cuándo disparó por última vez (tiempo del mundo; para la animación del rastrillo y el retroceso). */
	public static final ComponentType<Long> MUSKET_FIRED = Registry.register(Registries.DATA_COMPONENT_TYPE,
			EvolvedWarfare.id("musket_fired"),
			ComponentType.<Long>builder().codec(com.mojang.serialization.Codec.LONG)
					.packetCodec(net.minecraft.network.codec.PacketCodecs.VAR_LONG).build());

	private ModComponents() {
	}

	public static void init() {
	}
}

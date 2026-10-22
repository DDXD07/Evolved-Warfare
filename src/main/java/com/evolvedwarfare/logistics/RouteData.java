package com.evolvedwarfare.logistics;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

/** Las paradas de una hoja de ruta, en orden (el mensajero las recorre una tras otra y vuelve a empezar). */
public record RouteData(List<RouteStop> stops) {
	public static final int MAX_STOPS = 6;
	public static final int MAX_FILTERS = 3;
	public static final RouteData EMPTY = new RouteData(List.of());

	public static final Codec<RouteData> CODEC = RouteStop.CODEC.listOf().xmap(RouteData::new, RouteData::stops);
	public static final PacketCodec<RegistryByteBuf, RouteData> PACKET_CODEC =
			RouteStop.PACKET_CODEC.collect(PacketCodecs.toList()).xmap(RouteData::new, RouteData::stops);

	public int indexOf(BlockPos pos) {
		for (int i = 0; i < stops.size(); i++) {
			if (stops.get(i).pos().equals(pos)) {
				return i;
			}
		}
		return -1;
	}

	public RouteData with(RouteStop stop) {
		List<RouteStop> list = new ArrayList<>(stops);
		list.add(stop);
		return new RouteData(List.copyOf(list));
	}

	public RouteData replace(int index, RouteStop stop) {
		List<RouteStop> list = new ArrayList<>(stops);
		list.set(index, stop);
		return new RouteData(List.copyOf(list));
	}

	public RouteData without(int index) {
		List<RouteStop> list = new ArrayList<>(stops);
		list.remove(index);
		return new RouteData(List.copyOf(list));
	}
}

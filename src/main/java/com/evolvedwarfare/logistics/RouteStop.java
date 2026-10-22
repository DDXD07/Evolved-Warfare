package com.evolvedwarfare.logistics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

/**
 * Una parada de una hoja de ruta: dónde, si se recoge o se entrega, y qué objetos (si el filtro está vacío, todos).
 */
public record RouteStop(BlockPos pos, boolean pickup, List<ItemStack> filter) {
	public static final Codec<RouteStop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(RouteStop::pos),
			Codec.BOOL.fieldOf("pickup").forGetter(RouteStop::pickup),
			ItemStack.CODEC.listOf().optionalFieldOf("filter", List.of()).forGetter(RouteStop::filter)
	).apply(instance, RouteStop::new));

	public static final PacketCodec<RegistryByteBuf, RouteStop> PACKET_CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, RouteStop::pos,
			PacketCodecs.BOOL, RouteStop::pickup,
			ItemStack.LIST_PACKET_CODEC, RouteStop::filter,
			RouteStop::new);

	/** ¿Le vale este objeto a la parada? */
	public boolean accepts(ItemStack stack) {
		if (filter.isEmpty()) {
			return true;
		}
		for (ItemStack wanted : filter) {
			if (stack.isOf(wanted.getItem())) {
				return true;
			}
		}
		return false;
	}
}

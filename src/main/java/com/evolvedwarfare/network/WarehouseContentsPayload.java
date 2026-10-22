package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.logistics.StorageNetwork;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor → cliente: lo que hay en la red del almacén abierto (un objeto de cada tipo y cuántos hay). */
public record WarehouseContentsPayload(int syncId, int containers, List<StorageNetwork.Entry> entries) implements CustomPayload {
	public static final CustomPayload.Id<WarehouseContentsPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("warehouse_contents"));
	private static final PacketCodec<RegistryByteBuf, StorageNetwork.Entry> ENTRY = PacketCodec.tuple(
			ItemStack.PACKET_CODEC, StorageNetwork.Entry::prototype,
			PacketCodecs.VAR_INT, StorageNetwork.Entry::count,
			StorageNetwork.Entry::new);
	public static final PacketCodec<RegistryByteBuf, WarehouseContentsPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, WarehouseContentsPayload::syncId,
			PacketCodecs.VAR_INT, WarehouseContentsPayload::containers,
			ENTRY.collect(PacketCodecs.toList()), WarehouseContentsPayload::entries,
			WarehouseContentsPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

package com.evolvedwarfare.network;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Cliente → servidor: un clic en la rejilla del almacén (sacar o meter). */
public record WarehouseActionPayload(int syncId, int action, ItemStack prototype) implements CustomPayload {
	/** Sacar un montón al cursor. */
	public static final int TAKE_STACK = 0;
	/** Sacar medio montón al cursor. */
	public static final int TAKE_HALF = 1;
	/** Sacar un montón directo al inventario (Mayús + clic). */
	public static final int TAKE_TO_INVENTORY = 2;
	/** Meter lo del cursor. */
	public static final int INSERT_CURSOR = 3;
	/** Meter uno de lo del cursor (clic derecho). */
	public static final int INSERT_ONE = 4;

	public static final CustomPayload.Id<WarehouseActionPayload> ID = new CustomPayload.Id<>(EvolvedWarfare.id("warehouse_action"));
	public static final PacketCodec<RegistryByteBuf, WarehouseActionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, WarehouseActionPayload::syncId,
			PacketCodecs.VAR_INT, WarehouseActionPayload::action,
			ItemStack.OPTIONAL_PACKET_CODEC, WarehouseActionPayload::prototype,
			WarehouseActionPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

package com.evolvedwarfare.trade;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Una línea del catálogo de un comerciante.
 *
 * @param sell     true si el comerciante lo vende (pagas monedas); false si te lo compra (te paga monedas)
 * @param count    tamaño del lote
 * @param price    monedas por lote
 * @param stock    lotes por día (siempre al menos 20 unidades del objeto)
 */
public record Deal(boolean sell, Item item, int count, int price, int stock, Tier tier) {
	public static final PacketCodec<RegistryByteBuf, Deal> PACKET_CODEC = PacketCodec.of(Deal::write, Deal::read);

	public ItemStack stack() {
		return new ItemStack(item, count);
	}

	private void write(RegistryByteBuf buf) {
		buf.writeBoolean(sell);
		buf.writeIdentifier(Registries.ITEM.getId(item));
		buf.writeVarInt(count);
		buf.writeVarInt(price);
		buf.writeVarInt(stock);
		buf.writeVarInt(tier.ordinal());
	}

	private static Deal read(RegistryByteBuf buf) {
		boolean sell = buf.readBoolean();
		Identifier id = buf.readIdentifier();
		int count = buf.readVarInt();
		int price = buf.readVarInt();
		int stock = buf.readVarInt();
		Tier[] tiers = Tier.values();
		Tier tier = tiers[Math.floorMod(buf.readVarInt(), tiers.length)];
		return new Deal(sell, Registries.ITEM.get(id), count, price, stock, tier);
	}
}

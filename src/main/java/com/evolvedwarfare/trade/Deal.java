package com.evolvedwarfare.trade;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Una línea del catálogo de un comerciante.
 *
 * @param sell      true si el comerciante lo vende (pagas monedas); false si te lo compra (te paga monedas)
 * @param prototype la cosa, de una en una (con su desgaste o sus encantamientos si los tiene)
 * @param count     tamaño del lote
 * @param price     monedas por lote
 * @param stock     lotes por día (siempre al menos 20 unidades del objeto)
 * @param grade     su {@link Quality} (posición), o -1 si la calidad no le afecta
 */
public record Deal(boolean sell, ItemStack prototype, int count, int price, int stock, Tier tier, int grade) {
	public static final PacketCodec<RegistryByteBuf, Deal> PACKET_CODEC = PacketCodec.of(Deal::write, Deal::read);

	public Item item() {
		return prototype.getItem();
	}

	public ItemStack stack() {
		return prototype.copyWithCount(count);
	}

	@Nullable
	public Quality quality() {
		Quality[] all = Quality.values();
		return grade >= 0 && grade < all.length ? all[grade] : null;
	}

	private void write(RegistryByteBuf buf) {
		buf.writeBoolean(sell);
		ItemStack.PACKET_CODEC.encode(buf, prototype.copyWithCount(1));
		buf.writeVarInt(count);
		buf.writeVarInt(price);
		buf.writeVarInt(stock);
		buf.writeVarInt(tier.ordinal());
		buf.writeVarInt(grade + 1);
	}

	private static Deal read(RegistryByteBuf buf) {
		boolean sell = buf.readBoolean();
		ItemStack prototype = ItemStack.PACKET_CODEC.decode(buf);
		int count = buf.readVarInt();
		int price = buf.readVarInt();
		int stock = buf.readVarInt();
		Tier[] tiers = Tier.values();
		Tier tier = tiers[Math.floorMod(buf.readVarInt(), tiers.length)];
		int grade = buf.readVarInt() - 1;
		return new Deal(sell, prototype, count, price, stock, tier, grade);
	}
}

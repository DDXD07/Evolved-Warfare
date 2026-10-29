package com.evolvedwarfare.trade;

import java.util.Locale;
import net.minecraft.util.Formatting;

/**
 * Rareza de lo que vende o compra un comerciante: decide su rango de precios, su color y cuántos lotes mueve al día.
 * Todos los comerciantes venden y compran todo su catálogo en cualquier ciudad; el nivel de la ciudad solo encarece.
 *
 * <p>Cada rareza tiene su rango de precios en Glory Coins (lo que cuesta un lote en una ciudad de nivel 1; las ciudades
 * de más nivel cobran un recargo encima).</p>
 */
public enum Tier {
	COMMON(16, 24, 1, 500, Formatting.WHITE, 0xFF9A9A9A),
	UNCOMMON(10, 16, 501, 2000, Formatting.GREEN, 0xFF3FA34D),
	RARE(6, 8, 2001, 5000, Formatting.AQUA, 0xFF3A7BD5),
	EPIC(3, 4, 5001, 10000, Formatting.LIGHT_PURPLE, 0xFF9C3FD0),
	LEGENDARY(2, 2, 10001, 500000, Formatting.GOLD, 0xFFE0A21B);

	/** Lotes que vende al día (como mínimo, los que hagan falta para 20 unidades). */
	public final int sellStock;
	/** Lotes que compra al día. */
	public final int buyStock;
	/** Precio mínimo y máximo de un lote (ciudad de nivel 1). */
	public final int minPrice;
	public final int maxPrice;
	public final Formatting color;
	/** Color del marco de la ficha en la tienda. */
	public final int frame;

	Tier(int sellStock, int buyStock, int minPrice, int maxPrice, Formatting color, int frame) {
		this.sellStock = sellStock;
		this.buyStock = buyStock;
		this.minPrice = minPrice;
		this.maxPrice = maxPrice;
		this.color = color;
		this.frame = frame;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "gui.evolvedwarfare.tier." + id();
	}
}

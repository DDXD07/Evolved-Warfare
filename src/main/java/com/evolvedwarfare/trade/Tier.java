package com.evolvedwarfare.trade;

import com.evolvedwarfare.world.CityLevels;
import java.util.Locale;
import net.minecraft.util.Formatting;

/**
 * Rareza de lo que vende o compra un comerciante. Las ciudades más lejanas (de más nivel) desbloquean las más raras:
 * común desde el nivel 1, poco común desde el 3, rara desde el 5, épica desde el 7 y legendaria desde el 9.
 */
public enum Tier {
	COMMON(1, 16, 24, Formatting.WHITE, 0xFF9A9A9A),
	UNCOMMON(3, 10, 16, Formatting.GREEN, 0xFF3FA34D),
	RARE(5, 6, 8, Formatting.AQUA, 0xFF3A7BD5),
	EPIC(7, 3, 4, Formatting.LIGHT_PURPLE, 0xFF9C3FD0),
	LEGENDARY(9, 2, 2, Formatting.GOLD, 0xFFE0A21B);

	private final int minLevel;
	/** Lotes que vende al día. */
	public final int sellStock;
	/** Lotes que compra al día. */
	public final int buyStock;
	public final Formatting color;
	/** Color del marco de la ficha en la tienda. */
	public final int frame;

	Tier(int minLevel, int sellStock, int buyStock, Formatting color, int frame) {
		this.minLevel = minLevel;
		this.sellStock = sellStock;
		this.buyStock = buyStock;
		this.color = color;
		this.frame = frame;
	}

	/** Nivel de ciudad que hace falta (nunca más que el máximo configurado, para que todo sea alcanzable). */
	public int minLevel() {
		return Math.min(minLevel, CityLevels.maxLevel());
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "gui.evolvedwarfare.tier." + id();
	}
}

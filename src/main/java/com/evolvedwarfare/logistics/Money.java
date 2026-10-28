package com.evolvedwarfare.logistics;

import net.minecraft.text.Text;

/** Cómo se escriben las cantidades de monedas (los separadores salen del idioma del jugador). */
public final class Money {
	private Money() {
	}

	/** Con separador de miles: "12.500" en español, "12,500" en inglés. */
	public static String full(long amount) {
		String digits = Long.toString(Math.abs(amount));
		String separator = Text.translatable("gui.evolvedwarfare.thousands").getString();
		StringBuilder out = new StringBuilder();
		int first = digits.length() % 3;
		if (first == 0) {
			first = 3;
		}
		out.append(digits, 0, first);
		for (int i = first; i < digits.length(); i += 3) {
			out.append(separator).append(digits, i, i + 3);
		}
		return (amount < 0 ? "-" : "") + out;
	}

	/** Corto, para las fichas de la tienda: "2050", "12,5k", "250k", "1,9M". */
	public static String compact(long amount) {
		if (amount < 10000) {
			return Long.toString(amount);
		}
		String decimal = Text.translatable("gui.evolvedwarfare.decimal").getString();
		if (amount < 100000) {
			return oneDecimal(amount / 100L, decimal) + "k";
		}
		if (amount < 1000000) {
			return (amount / 1000L) + "k";
		}
		return oneDecimal(amount / 100000L, decimal) + "M";
	}

	/** {@code tenths} décimas, sin el ",0" si es exacto. */
	private static String oneDecimal(long tenths, String decimal) {
		long whole = tenths / 10;
		long rest = tenths % 10;
		return rest == 0 ? Long.toString(whole) : whole + decimal + rest;
	}
}

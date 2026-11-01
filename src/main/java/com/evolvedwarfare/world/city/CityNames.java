package com.evolvedwarfare.world.city;

import java.util.Locale;
import java.util.Random;

/** El nombre de cada ciudad, sacado de su semilla (siempre el mismo), con sílabas según el estilo. */
public final class CityNames {
	private static final String[][] MEDIEVAL = {
			{"Val", "Mont", "Bel", "Ros", "Alt", "Cal", "Mir", "Sal", "Tor", "Ven", "Bra", "Cas", "Lor", "Fen", "Gal"},
			{"de", "a", "e", "o", "ri", "la", "ne", "", "", "ra"},
			{"mora", "fort", "burgo", "vila", "sierra", "dor", "gard", "lunda", "mar", "peña", "ria", "brava", "monte"}};
	private static final String[][] DESERT = {
			{"Al", "Qas", "Sam", "Zar", "Ka", "Ma", "Ra", "Sha", "Ta", "Jad", "Dun", "Nim"},
			{"ra", "ma", "sa", "ki", "di", "fa", "", "a"},
			{"bad", "dah", "rak", "zir", "nar", "kesh", "mun", "ra", "zan", "hir"}};
	private static final String[][] SNOW = {
			{"Kald", "Frost", "Ulv", "Hrim", "Bjor", "Sval", "Is", "Vin", "Skar", "Ner", "Hal", "Thor"},
			{"e", "a", "o", "", "", "i"},
			{"heim", "vik", "gard", "fell", "stad", "holm", "borg", "dal", "havn", "mark"}};

	private CityNames() {
	}

	public static String of(long seed, CityDesigner.Style style) {
		String[][] parts = switch (style) {
			case DESERT -> DESERT;
			case SNOW -> SNOW;
			default -> MEDIEVAL;
		};
		Random r = new Random(seed ^ 0x5DEECE66DL);
		String name = parts[0][r.nextInt(parts[0].length)] + parts[1][r.nextInt(parts[1].length)] + parts[2][r.nextInt(parts[2].length)];
		name = name.toLowerCase(Locale.ROOT);
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}
}

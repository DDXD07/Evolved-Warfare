package com.evolvedwarfare.faction;

import java.util.Locale;
import net.minecraft.util.Formatting;

/** Cómo te trata una facción. */
public enum Standing {
	/** Te atacan nada más verte y asaltan tu pueblo. */
	HOSTILE(Formatting.RED),
	/** Tregua pagada: no te atacan aunque te odien (hasta que se acabe). */
	TRUCE(Formatting.GOLD),
	/** Te dejan en paz mientras no les ataques. */
	NEUTRAL(Formatting.GRAY),
	/** Puedes contratar a sus guerreros como mercenarios. */
	FRIENDLY(Formatting.GREEN),
	/** Alianza firmada: luchan a tu lado y puedes pedirles ayuda. */
	ALLIED(Formatting.AQUA);

	private final Formatting color;

	Standing(Formatting color) {
		this.color = color;
	}

	public Formatting color() {
		return color;
	}

	public String translationKey() {
		return "standing.evolvedwarfare." + name().toLowerCase(Locale.ROOT);
	}

	public boolean atLeast(Standing other) {
		return ordinal() >= other.ordinal();
	}

	public static Standing byOrdinal(int ordinal) {
		Standing[] values = values();
		return values[Math.max(0, Math.min(ordinal, values.length - 1))];
	}
}

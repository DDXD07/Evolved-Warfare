package com.evolvedwarfare.town;

import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

/** El carácter de un NPC: cambia lo que dice, con quién se lleva bien y cómo reacciona al peligro. */
public enum Personality {
	CHEERFUL("cheerful"),
	GRUMPY("grumpy"),
	FEARFUL("fearful"),
	BRAVE("brave"),
	LAZY("lazy"),
	KIND("kind"),
	CURIOUS("curious");

	public final String id;

	Personality(String id) {
		this.id = id;
	}

	public Text label() {
		return label(false);
	}

	/** En femenino si es mujer (en inglés da igual). */
	public Text label(boolean female) {
		return Text.translatable("npc.evolvedwarfare.personality." + id + (female ? ".f" : ""));
	}

	public static Personality byOrdinal(int i) {
		Personality[] all = values();
		return i >= 0 && i < all.length ? all[i] : CHEERFUL;
	}

	public static Personality random(Random random) {
		return values()[random.nextInt(values().length)];
	}

	/** Lo bien que se llevan dos caracteres al conocerse (de -20 a +20). */
	public int chemistry(Personality other) {
		if (this == other) {
			return this == GRUMPY ? -5 : 12;
		}
		if (this == GRUMPY || other == GRUMPY) {
			return (this == KIND || other == KIND) ? 4 : -12;
		}
		if ((this == BRAVE && other == FEARFUL) || (this == FEARFUL && other == BRAVE)) {
			return -6;
		}
		if ((this == LAZY && other == CURIOUS) || (this == CURIOUS && other == LAZY)) {
			return -4;
		}
		if (this == KIND || other == KIND || this == CHEERFUL || other == CHEERFUL) {
			return 10;
		}
		return 3;
	}
}

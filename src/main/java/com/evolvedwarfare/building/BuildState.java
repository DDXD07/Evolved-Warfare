package com.evolvedwarfare.building;

import java.util.Locale;

/** En qué punto está el edificio de un puesto. */
public enum BuildState {
	/** Recién puesto: el jugador está eligiendo diseño (el trabajador espera). */
	CHOOSING,
	/** En obra: el trabajador lo construye antes de ponerse con su oficio. */
	BUILDING,
	/** Terminado. */
	DONE,
	/** El jugador eligió trabajar sin edificio (o canceló la obra). */
	NONE;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static BuildState byOrdinal(int ordinal) {
		BuildState[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CHOOSING;
	}

	public static BuildState byId(String id) {
		for (BuildState state : values()) {
			if (state.id().equals(id)) {
				return state;
			}
		}
		return CHOOSING;
	}
}

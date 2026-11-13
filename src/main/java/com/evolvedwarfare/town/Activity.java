package com.evolvedwarfare.town;

/** Lo que está haciendo un NPC según su rutina (y la hora). */
public enum Activity {
	/** Sin rutina (contratado y siguiéndote, de guardia fija en un puesto...). */
	NONE,
	SLEEP,
	BREAKFAST,
	WORK,
	LEISURE,
	DINNER,
	HOME,
	/** Hay peligro: corre a casa. */
	HIDE,
	/** Guardias: ronda por el pueblo. */
	PATROL;

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}

package com.evolvedwarfare.work;

import java.util.Locale;

/** Lo que está haciendo (o por qué no trabaja) el trabajador de un puesto. Se muestra en el menú del puesto. */
public enum WorkStatus {
	NO_WORKER(Tone.BAD),
	WORKING(Tone.GOOD),
	/** Hay trabajo, pero ahora mismo nada está listo (cultivos creciendo, colmenas sin miel...). */
	WAITING(Tone.NEUTRAL),
	NOTHING_HERE(Tone.BAD),
	STORAGE_FULL(Tone.BAD),
	NO_SUPPLIES(Tone.BAD),
	NO_FUEL(Tone.BAD),
	NO_WATER(Tone.BAD),
	NEED_TOOL(Tone.BAD),
	RESTING(Tone.NEUTRAL),
	UNPAID(Tone.BAD),
	ABSENT(Tone.BAD),
	AWAY(Tone.NEUTRAL),
	DONE(Tone.GOOD),
	/** El jugador aún no ha elegido (o descartado) el edificio del puesto. */
	CHOOSE_BUILDING(Tone.NEUTRAL),
	/** Construyendo el edificio del puesto. */
	BUILDING(Tone.GOOD),
	/** La obra está parada: falta algún material en los suministros. */
	NO_MATERIALS(Tone.BAD);

	public enum Tone { GOOD, NEUTRAL, BAD }

	private final Tone tone;

	WorkStatus(Tone tone) {
		this.tone = tone;
	}

	public Tone tone() {
		return tone;
	}

	public String translationKey() {
		return "status.evolvedwarfare." + name().toLowerCase(Locale.ROOT);
	}

	public static WorkStatus byOrdinal(int ordinal) {
		WorkStatus[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : WAITING;
	}
}

package com.evolvedwarfare.climate;

import com.evolvedwarfare.config.EwConfig;
import com.evolvedwarfare.world.Hordes;
import java.util.Locale;

/**
 * Las cuatro estaciones, de {@link #length()} días cada una (91 de normal: un año de 364 días), empezando en primavera el
 * día 1. Cada una cambia la temperatura del aire, el tiempo que hace, lo que crecen los cultivos, si nieva y se hiela el
 * agua, y el color de la hierba y las hojas.
 */
public enum Season {
	/** Templada y lluviosa: todo crece, la nieve y el hielo se derriten. */
	SPRING(0.0F, 0.0F, 0x7CD34A, 0.40F, 0.05F, 0x55FF55),
	/** Calor de día, tormentas: cuidado con la hipertermia en el desierto. */
	SUMMER(7.0F, 0.25F, 0xC9D05A, 0.15F, 0.12F, 0xFFD24A),
	/** Fresco, hojas naranjas, lluvia. */
	AUTUMN(-4.0F, -0.25F, 0xE07A26, 0.45F, 0.05F, 0xFF9A3C),
	/** Frío: nieva en las llanuras, el agua se hiela y los cultivos al raso casi no crecen. */
	WINTER(-13.0F, -0.72F, 0x9AA38C, 0.50F, 0.02F, 0x9AD8FF);

	/** Lo que sube o baja el aire (°C). */
	public final float airOffset;
	/** Lo que cambia la temperatura de los biomas (la que decide si nieva o se hiela el agua). */
	public final float biomeOffset;
	/** Hacia qué color van la hierba y las hojas. */
	public final int tint;
	/** Probabilidad de que llueva (o nieve) cada día y de que haya tormenta. */
	public final float rainChance;
	public final float thunderChance;
	/** Color del nombre en la pantalla. */
	public final int color;

	Season(float airOffset, float biomeOffset, int tint, float rainChance, float thunderChance, int color) {
		this.airOffset = airOffset;
		this.biomeOffset = biomeOffset;
		this.tint = tint;
		this.rainChance = rainChance;
		this.thunderChance = thunderChance;
		this.color = color;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "season.evolvedwarfare." + id();
	}

	/** Días que dura cada estación (la configuración; 91 de normal). */
	public static int length() {
		return Math.max(1, EwConfig.get().seasonLength);
	}

	/** La estación de un día (el día 1 es el primero de la primavera). */
	public static Season of(long day, int length) {
		long index = Math.floorDiv(Math.max(0L, day - 1L), Math.max(1, length));
		return values()[(int) Math.floorMod(index, 4L)];
	}

	/** Qué día de su estación es (de 1 a length). */
	public static int dayOf(long day, int length) {
		return (int) Math.floorMod(Math.max(0L, day - 1L), Math.max(1, length)) + 1;
	}

	/** La estación de ahora en ese mundo (por la hora del mundo). */
	public static Season at(long timeOfDay) {
		return of(Hordes.day(timeOfDay), length());
	}

	public static Season byId(String id) {
		for (Season s : values()) {
			if (s.id().equalsIgnoreCase(id)) {
				return s;
			}
		}
		return null;
	}
}

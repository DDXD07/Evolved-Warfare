package com.evolvedwarfare.health;

/** Las partes del cuerpo con su propia vida (fase 92). */
public enum BodyPart {
	HEAD(8.0F),
	TORSO(12.0F),
	RIGHT_ARM(8.0F),
	LEFT_ARM(8.0F),
	RIGHT_LEG(10.0F),
	LEFT_LEG(10.0F);

	public final float max;

	BodyPart(float max) {
		this.max = max;
	}

	public boolean isArm() {
		return this == RIGHT_ARM || this == LEFT_ARM;
	}

	public boolean isLeg() {
		return this == RIGHT_LEG || this == LEFT_LEG;
	}

	/** Brazos y piernas se rompen; la cabeza y el torso se quedan «críticos». */
	public boolean canBreak() {
		return isArm() || isLeg();
	}

	public int bit() {
		return 1 << ordinal();
	}

	public String translationKey() {
		return "health.evolvedwarfare.part." + name().toLowerCase(java.util.Locale.ROOT);
	}
}

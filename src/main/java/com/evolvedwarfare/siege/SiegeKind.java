package com.evolvedwarfare.siege;

import java.util.Locale;

/**
 * Las máquinas de asedio y sus números. Velocidades en bloques por tic; ángulos en grados (elevación: positivo hacia
 * arriba); recarga en tics.
 *
 * <p>Alcance máximo aproximado (con rozamiento): catapulta ~56 bloques, trebuchet ~105, balista ~170 (tiro tenso).</p>
 */
public enum SiegeKind {
	/** Lanza piedras (o vasijas incendiarias) en parábola; rompe muros de piedra. */
	CATAPULT(60.0F, 0.05, 2.0F, 1.9, 0.05, 8.0F, 65.0F, 30.0F, 70, 4.0F, 1.6F, true),
	/** Dispara virotes en tiro tenso: mucho daño a una sola cosa (gente u otras máquinas). */
	BALLISTA(45.0F, 0.065, 3.0F, 3.0, 0.025, -15.0F, 35.0F, 60.0F, 40, 0.0F, 0.0F, false),
	/** La pieza más pesada: piedras grandes muy lejos, y abre brechas en casi cualquier muro. */
	TREBUCHET(100.0F, 0.025, 1.5F, 2.8, 0.05, 20.0F, 70.0F, 20.0F, 140, 7.0F, 2.6F, true),
	/** Ariete cubierto: se empuja hasta la puerta o el muro y lo derriba a golpes. */
	RAM(90.0F, 0.06, 3.5F, 0.0, 0.0, 0.0F, 0.0F, 0.0F, 30, 10.0F, 0.0F, false);

	/** Vida de la máquina. */
	public final float health;
	/** Velocidad al empujarla (bloques por tic). */
	public final double moveSpeed;
	/** Giro por tic al empujarla de lado (grados). */
	public final float turnSpeed;
	/** Velocidad del proyectil al salir. */
	public final double shotSpeed;
	/** Gravedad del proyectil (por tic). */
	public final double shotGravity;
	public final float minElevation;
	public final float maxElevation;
	/** Cuánto puede apuntar a los lados respecto a hacia donde mira la máquina (grados). */
	public final float yawArc;
	public final int reload;
	/** Fuerza contra los bloques: dureza máxima que puede romper (el ariete, golpe a golpe). */
	public final float power;
	/** Radio de destrucción del impacto. */
	public final float blastRadius;
	/** ¿Tira en parábola alta cuando puede (para pasar por encima de los muros)? */
	public final boolean lob;

	SiegeKind(float health, double moveSpeed, float turnSpeed, double shotSpeed, double shotGravity, float minElevation,
			float maxElevation, float yawArc, int reload, float power, float blastRadius, boolean lob) {
		this.health = health;
		this.moveSpeed = moveSpeed;
		this.turnSpeed = turnSpeed;
		this.shotSpeed = shotSpeed;
		this.shotGravity = shotGravity;
		this.minElevation = minElevation;
		this.maxElevation = maxElevation;
		this.yawArc = yawArc;
		this.reload = reload;
		this.power = power;
		this.blastRadius = blastRadius;
		this.lob = lob;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public boolean fires() {
		return this != RAM;
	}

	/** Distancia mínima a la que la dotación (NPC) dispara, para no hacerse daño ella misma. */
	public double minCrewRange() {
		return switch (this) {
			case CATAPULT -> 12.0;
			case TREBUCHET -> 22.0;
			case BALLISTA -> 3.0;
			case RAM -> 0.0;
		};
	}

	/** Distancia máxima a la que la dotación busca blancos. */
	public double maxCrewRange() {
		return switch (this) {
			case CATAPULT -> 52.0;
			case TREBUCHET -> 96.0;
			case BALLISTA -> 64.0;
			case RAM -> 0.0;
		};
	}

	/** Desde dónde sale el proyectil: altura y adelanto respecto al centro de la máquina. */
	public double muzzleHeight() {
		return switch (this) {
			case CATAPULT -> 3.2;
			case BALLISTA -> 1.35;
			case TREBUCHET -> 11.0;
			case RAM -> 1.0;
		};
	}

	public double muzzleForward() {
		return switch (this) {
			case CATAPULT -> 0.4;
			case BALLISTA -> 1.2;
			case TREBUCHET -> 1.5;
			case RAM -> 1.8;
		};
	}
}

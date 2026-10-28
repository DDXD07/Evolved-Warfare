package com.evolvedwarfare.weapon;

import java.util.Locale;

/**
 * Las armas medievales. Daño: lo que se suma al del material (hierro +2, diamante +3, netherita +4) y al puño (1).
 * Velocidad: como en las armas del juego (espada -2,4 = 1,6 golpes por segundo). Alcance: bloques de más (o de menos)
 * para el jugador; los NPCs también llegan algo más lejos con las armas largas.
 */
public enum WeaponType {
	/** Rápida y corta; por la espalda hace mucho más daño. */
	DAGGER(1, -1.6F, -0.5, 0.0),
	/** Llega lejos. */
	SPEAR(2, -2.6F, 1.5, 1.0),
	/** Golpe fuerte y barrido amplio. */
	LONGSWORD(5, -2.9F, 0.5, 0.3),
	/** La que más llega; mucho daño contra jinetes y monturas. */
	HALBERD(6, -3.1F, 2.0, 1.5),
	/** Lenta; atraviesa armaduras y empuja. */
	WARHAMMER(6, -3.2F, 0.0, 0.0),
	/** Puede aturdir (lentitud y debilidad un par de segundos). */
	FLAIL(5, -3.0F, 0.0, 0.0),
	/** Un hacha de guerra: mucho daño y desarma escudos. */
	BATTLE_AXE(8, -3.2F, 0.0, 0.0);

	public final int damage;
	public final float speed;
	public final double reach;
	public final double npcReach;

	WeaponType(int damage, float speed, double reach, double npcReach) {
		this.damage = damage;
		this.speed = speed;
		this.reach = reach;
		this.npcReach = npcReach;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}

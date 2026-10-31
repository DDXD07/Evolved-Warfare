package com.evolvedwarfare.weapon;

/**
 * Cómo es el golpe de cada arma cuerpo a cuerpo (generado por tools/ew_melee.py: no lo edites a mano): lo que dura
 * la animación (en el servidor y en el cliente, para que vayan a la par) y si se coge con las dos manos.
 */
public final class MeleeSwing {
	private MeleeSwing() {
	}

	/** Lo que dura el golpe (tics; el de vanilla son 6). */
	public static int ticks(WeaponType type) {
		return switch (type) {
			case DAGGER -> 5;
			case SPEAR -> 7;
			case LONGSWORD -> 9;
			case HALBERD -> 10;
			case WARHAMMER -> 11;
			case FLAIL -> 10;
			case BATTLE_AXE -> 10;
		};
	}

	/** Lo que tarda en cargarse el golpe fuerte (tics, manteniendo clic derecho). */
	public static int chargeTicks(WeaponType type) {
		return switch (type) {
			case DAGGER -> 10;
			case SPEAR -> 16;
			case LONGSWORD -> 20;
			case HALBERD -> 24;
			case WARHAMMER -> 26;
			case FLAIL -> 20;
			case BATTLE_AXE -> 26;
		};
	}

	/** Lo que dura la animación del golpe fuerte al soltar (tics). */
	public static int heavyTicks(WeaponType type) {
		return switch (type) {
			case DAGGER -> 7;
			case SPEAR -> 9;
			case LONGSWORD -> 12;
			case HALBERD -> 13;
			case WARHAMMER -> 14;
			case FLAIL -> 12;
			case BATTLE_AXE -> 12;
		};
	}

	/** Si se coge con las dos manos al golpear (cuando la otra mano está libre). */
	public static boolean twoHanded(WeaponType type) {
		return switch (type) {
			case DAGGER -> false;
			case SPEAR -> true;
			case LONGSWORD -> true;
			case HALBERD -> true;
			case WARHAMMER -> true;
			case FLAIL -> false;
			case BATTLE_AXE -> false;
		};
	}
}

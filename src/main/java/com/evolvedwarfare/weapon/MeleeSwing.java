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

package com.evolvedwarfare.entity;

import com.evolvedwarfare.item.MedicKitItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import org.jetbrains.annotations.Nullable;

/** Clases de combatiente. Las comparten los soldados del jugador y los bandidos. */
public enum NpcRole {
	/** Espada (y escudo si lo lleva): aguanta en primera línea. */
	WARRIOR("warrior", Kind.MELEE, 24.0, 1.0, 0.30, 20),
	/** Hacha: golpea muy fuerte pero más despacio. */
	BARBARIAN("barbarian", Kind.MELEE, 22.0, 1.0, 0.32, 30),
	/** Arco: dispara desde lejos y se aparta si te acercas. */
	ARCHER("archer", Kind.BOW, 20.0, 0.5, 0.30, 20),
	/** Ballesta: carga despacio, pero cada virote hace mucho daño. */
	CROSSBOWMAN("crossbowman", Kind.CROSSBOW, 20.0, 0.5, 0.28, 20),
	/** Botiquín: no ataca; cura a sus aliados heridos. */
	MEDIC("medic", Kind.HEALER, 18.0, 0.5, 0.31, 20),
	/** Jefe de los bandidos: armadura completa y mucha vida. */
	CAPTAIN("captain", Kind.MELEE, 40.0, 3.0, 0.30, 20);

	public enum Kind { MELEE, BOW, CROSSBOW, HEALER }

	private final String id;
	private final Kind kind;
	private final double health;
	private final double attackDamage;
	private final double speed;
	private final int meleeCooldown;

	NpcRole(String id, Kind kind, double health, double attackDamage, double speed, int meleeCooldown) {
		this.id = id;
		this.kind = kind;
		this.health = health;
		this.attackDamage = attackDamage;
		this.speed = speed;
		this.meleeCooldown = meleeCooldown;
	}

	public String id() {
		return id;
	}

	public Kind kind() {
		return kind;
	}

	public double health() {
		return health;
	}

	public double attackDamage() {
		return attackDamage;
	}

	public double speed() {
		return speed;
	}

	public int meleeCooldown() {
		return meleeCooldown;
	}

	public String translationKey() {
		return "role.evolvedwarfare." + id;
	}

	public static NpcRole byId(String id, NpcRole fallback) {
		for (NpcRole role : values()) {
			if (role.id.equals(id)) {
				return role;
			}
		}
		return fallback;
	}

	public static NpcRole byOrdinal(int i) {
		NpcRole[] values = values();
		return values[Math.max(0, Math.min(i, values.length - 1))];
	}

	/** Qué clase sale al entrenar a un recluta con este objeto en la mano. */
	@Nullable
	public static NpcRole fromWeapon(ItemStack stack) {
		if (stack.getItem() instanceof SwordItem) {
			return WARRIOR;
		}
		if (stack.getItem() instanceof AxeItem) {
			return BARBARIAN;
		}
		if (stack.getItem() instanceof BowItem) {
			return ARCHER;
		}
		if (stack.getItem() instanceof CrossbowItem) {
			return CROSSBOWMAN;
		}
		if (stack.getItem() instanceof MedicKitItem) {
			return MEDIC;
		}
		return null;
	}
}

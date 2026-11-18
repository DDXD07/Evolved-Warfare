package com.evolvedwarfare.skill;

import java.util.Locale;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Los atributos del superviviente (fase 114, como en ARK pero con aumentos pequeños): cada nivel da un punto para
 * repartir. 'perPoint' es lo que da cada punto (en corazones, en tanto por uno...) y 'cap' el máximo (0: sin máximo).
 */
public enum SurvivorAttribute {
	/** +0,2 corazones por punto. */
	HEALTH("minecraft:golden_apple", 0xE05050, 0.2, 0.0),
	/** El aguante se gasta un 0,5 % menos y se recupera un 0,5 % más rápido por punto. */
	STAMINA("minecraft:sugar", 0x3CC8E6, 0.005, 0.5),
	/** +0,5 % de daño cuerpo a cuerpo por punto. */
	MELEE("minecraft:iron_sword", 0xF0A050, 0.005, 0.0),
	/** +0,2 % de velocidad al andar por punto. */
	SPEED("minecraft:feather", 0xE6E6E6, 0.002, 0.25),
	/** Un 0,5 % menos de daño de sangrado, veneno, infección, frío, calor, sed y hambre por punto. */
	FORTITUDE("minecraft:totem_of_undying", 0xC8B060, 0.005, 0.5),
	/** El hambre y la sed bajan un 0,5 % más despacio por punto. */
	METABOLISM("minecraft:cooked_beef", 0xC87850, 0.005, 0.5),
	/** La carga pesada de la mochila frena un 0,8 % menos por punto. */
	WEIGHT("evolvedwarfare:backpack_basic", 0xA08060, 0.008, 0.8),
	/** Aguantar la respiración: +0,03 de oxígeno por punto (unos 33 puntos, como Respiración I). */
	OXYGEN("minecraft:heart_of_the_sea", 0x5AA0E6, 0.03, 0.0);

	public final Identifier icon;
	public final int color;
	public final double perPoint;
	public final double cap;

	SurvivorAttribute(String icon, int color, double perPoint, double cap) {
		this.icon = Identifier.of(icon);
		this.color = color;
		this.perPoint = perPoint;
		this.cap = cap;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "attribute.evolvedwarfare.survivor." + id();
	}

	/** Lo que dan esos puntos (con el máximo). */
	public double value(int points) {
		double v = perPoint * Math.max(0, points);
		return cap > 0.0 ? Math.min(cap, v) : v;
	}

	@Nullable
	public static SurvivorAttribute byId(String id) {
		for (SurvivorAttribute a : values()) {
			if (a.id().equals(id)) {
				return a;
			}
		}
		return null;
	}
}

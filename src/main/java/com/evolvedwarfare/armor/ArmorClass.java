package com.evolvedwarfare.armor;

import java.util.List;
import java.util.Locale;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Las armaduras de clase (con el aspecto de las de vanilla): arquero, guerrero, ladrón y mosquetero. Cada una tiene sus
 * ventajas, que suben con el material ({@link ArmorTier#level}: cuero 1 ... netherita 5), y sus desventajas, fijas. Cada
 * pieza da una cuarta parte: con el conjunto entero se tiene todo.
 *
 * <ul>
 * <li><b>Arquero</b>: +5 % por escalón al tensar arcos y cargar ballestas, +3 % por escalón de daño con flechas;
 * −15 % de daño cuerpo a cuerpo.</li>
 * <li><b>Guerrero</b>: +4 % por escalón de daño cuerpo a cuerpo, +5 % por escalón de resistencia al empuje; −10 % de
 * velocidad.</li>
 * <li><b>Ladrón</b>: +3 % por escalón de velocidad, +6 % por escalón de daño por la espalda; −4 de armadura.</li>
 * <li><b>Mosquetero</b>: +5 % por escalón de recarga con armas de fuego, +3 % por escalón de daño de bala; −4 de
 * armadura.</li>
 * </ul>
 */
public enum ArmorClass {
	ARCHER(0x5E8C3A, Items.FEATHER, List.of(new Bonus(Effect.DRAW_SPEED, 5), new Bonus(Effect.ARROW_DAMAGE, 3)),
			List.of(new Bonus(Effect.MELEE_DAMAGE, -15)), 0),
	WARRIOR(0xB0352E, Items.RED_DYE, List.of(new Bonus(Effect.MELEE_DAMAGE, 4), new Bonus(Effect.KNOCKBACK, 5)),
			List.of(new Bonus(Effect.MOVE_SPEED, -10)), 0),
	THIEF(0x6A5A86, Items.INK_SAC, List.of(new Bonus(Effect.MOVE_SPEED, 3), new Bonus(Effect.BACKSTAB, 6)), List.of(), 1),
	MUSKETEER(0x3A5FA8, Items.GUNPOWDER, List.of(new Bonus(Effect.RELOAD_SPEED, 5), new Bonus(Effect.BULLET_DAMAGE, 3)), List.of(), 1);

	/** Lo que puede cambiar una armadura de clase. */
	public enum Effect {
		DRAW_SPEED, ARROW_DAMAGE, MELEE_DAMAGE, KNOCKBACK, MOVE_SPEED, BACKSTAB, RELOAD_SPEED, BULLET_DAMAGE;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Un efecto y cuánto (en %): en las ventajas, por escalón de material; en las desventajas, fijo. */
	public record Bonus(Effect effect, int percent) {
	}

	/** Color de la clase (en los textos y en las texturas). */
	public final int color;
	/** Lo que lleva cada receta además del material. */
	public final Item accent;
	public final List<Bonus> pros;
	public final List<Bonus> cons;
	/** Armadura que pierde cada pieza (va más ligera). */
	public final int armorPenalty;

	ArmorClass(int color, Item accent, List<Bonus> pros, List<Bonus> cons, int armorPenalty) {
		this.color = color;
		this.accent = accent;
		this.pros = pros;
		this.cons = cons;
		this.armorPenalty = armorPenalty;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Lo que da el conjunto entero de ese material para ese efecto (en %; 0 si no lo toca). */
	public int setPercent(Effect effect, ArmorTier tier) {
		int total = 0;
		for (Bonus bonus : pros) {
			if (bonus.effect() == effect) {
				total += bonus.percent() * tier.level;
			}
		}
		for (Bonus bonus : cons) {
			if (bonus.effect() == effect) {
				total += bonus.percent();
			}
		}
		return total;
	}
}

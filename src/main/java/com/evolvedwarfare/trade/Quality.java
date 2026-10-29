package com.evolvedwarfare.trade;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

/**
 * Calidad de lo que venden los comerciantes según el nivel de su ciudad. Solo cambia las cosas con durabilidad (armas,
 * armaduras, herramientas, arcos, escudos...):
 * <ul>
 *   <li>Nivel 1–2, <b>desgastado</b>: llegan gastadas (les falta entre un 35 % y un 55 % de durabilidad).</li>
 *   <li>Nivel 3–4, <b>normal</b>: nuevas, sin encantar.</li>
 *   <li>Nivel 5–6, <b>bueno</b>: encantadas como en una mesa con poca experiencia (5–10).</li>
 *   <li>Nivel 7–8, <b>excelente</b>: encantadas a media potencia (15–22).</li>
 *   <li>Nivel 9–10, <b>obra maestra</b>: encantadas casi al máximo de la mesa (26–30).</li>
 * </ul>
 * Los encantamientos cambian cada día (con la reposición) y cada comerciante tiene los suyos.
 */
public enum Quality {
	WORN(Formatting.GRAY, 0, 0),
	STANDARD(Formatting.WHITE, 0, 0),
	FINE(Formatting.GREEN, 5, 10),
	SUPERIOR(Formatting.AQUA, 15, 22),
	MASTERWORK(Formatting.LIGHT_PURPLE, 26, 30);

	public final Formatting color;
	private final int minPower;
	private final int maxPower;

	Quality(Formatting color, int minPower, int maxPower) {
		this.color = color;
		this.minPower = minPower;
		this.maxPower = maxPower;
	}

	public static Quality forLevel(int cityLevel) {
		if (cityLevel <= 2) {
			return WORN;
		}
		if (cityLevel <= 4) {
			return STANDARD;
		}
		if (cityLevel <= 6) {
			return FINE;
		}
		if (cityLevel <= 8) {
			return SUPERIOR;
		}
		return MASTERWORK;
	}

	/** Si la calidad cambia algo en esta cosa (lo que tiene durabilidad). */
	public static boolean affects(ItemStack stack) {
		return stack.isDamageable();
	}

	/** Esta cosa con esta calidad: gastada, nueva o encantada. */
	public ItemStack apply(ItemStack stack, Random random, DynamicRegistryManager registries) {
		if (!affects(stack)) {
			return stack;
		}
		if (this == WORN) {
			int max = stack.getMaxDamage();
			stack.setDamage(Math.min(max - 1, (int) (max * (0.35F + random.nextFloat() * 0.2F))));
			return stack;
		}
		if (maxPower > 0 && registries != null) {
			int power = minPower + random.nextInt(maxPower - minPower + 1);
			Optional<RegistryEntryList.Named<Enchantment>> table = registries.get(RegistryKeys.ENCHANTMENT)
					.getEntryList(EnchantmentTags.IN_ENCHANTING_TABLE);
			return EnchantmentHelper.enchant(random, stack, power, registries, table);
		}
		return stack;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "gui.evolvedwarfare.quality." + id();
	}

	/** "objetos excelentes" (para el aviso al entrar en la ciudad). */
	public String cityKey() {
		return translationKey() + ".city";
	}
}

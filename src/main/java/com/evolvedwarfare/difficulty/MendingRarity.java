package com.evolvedwarfare.difficulty;

import com.evolvedwarfare.config.EwConfig;
import java.util.function.UnaryOperator;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.random.Random;

/**
 * La reparación (Mending), rarísima (fase 100): los libros encantados que salen al azar (cofres, pesca, bibliotecarios)
 * ya no la traen; en su lugar, uno de cada {@code mendingBookChance} (5000) libros es un libro de reparación. Las
 * reliquias de los jefes de las mazmorras la siguen llevando.
 */
public final class MendingRarity {
	private MendingRarity() {
	}

	public static int chance() {
		return EwConfig.get().mendingBookChance;
	}

	public static boolean hasMending(ItemStack stack) {
		for (RegistryEntry<Enchantment> e : EnchantmentHelper.getEnchantments(stack).getEnchantments()) {
			if (e.matchesKey(Enchantments.MENDING)) {
				return true;
			}
		}
		return false;
	}

	public static void strip(ItemStack stack) {
		EnchantmentHelper.apply(stack, builder -> builder.remove(e -> e.matchesKey(Enchantments.MENDING)));
	}

	public static ItemStack mendingBook(DynamicRegistryManager registries, int count) {
		RegistryEntry<Enchantment> mending = registries.get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.MENDING).orElse(null);
		if (mending == null) {
			return ItemStack.EMPTY;
		}
		ItemStack book = net.minecraft.item.EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(mending, 1));
		book.setCount(Math.max(1, count));
		return book;
	}

	/**
	 * Lo que sale de encantar al azar: sin reparación (se vuelve a tirar, o se le quita) y, si es un libro, una vez entre
	 * {@code chance} es de reparación.
	 */
	public static ItemStack filter(ItemStack result, ItemStack original, Random random, DynamicRegistryManager registries,
			UnaryOperator<ItemStack> reroll) {
		int chance = chance();
		if (chance <= 0 || result.isEmpty()) {
			return result;
		}
		if (hasMending(result)) {
			for (int k = 0; k < 6 && hasMending(result); k++) {
				result = reroll.apply(original.copy());
			}
			if (hasMending(result)) {
				strip(result);
			}
		}
		if (result.isOf(Items.ENCHANTED_BOOK) && random.nextInt(chance) == 0) {
			ItemStack book = mendingBook(registries, result.getCount());
			if (!book.isEmpty()) {
				return book;
			}
		}
		return result;
	}
}

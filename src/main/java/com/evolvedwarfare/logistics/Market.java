package com.evolvedwarfare.logistics;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import org.jetbrains.annotations.Nullable;

/**
 * El mercado: con qué se paga y cuánto se saca por cada cosa.
 *
 * <p>La moneda es la Glory Coin del mod. Los precios son
 * parecidos a los que pagan los aldeanos: "tantos objetos por tantas monedas".</p>
 */
public final class Market {

	/** {@code items} objetos se venden por {@code coins} monedas. */
	public record Price(int items, int coins) {
	}

	private static final Map<Item, Price> PRICES = new HashMap<>();
	private static final Map<TagKey<Item>, Price> TAG_PRICES = new LinkedHashMap<>();

	static {
		// campo
		price(Items.WHEAT, 20, 1);
		price(Items.CARROT, 22, 1);
		price(Items.POTATO, 26, 1);
		price(Items.BEETROOT, 15, 1);
		price(Items.PUMPKIN, 6, 1);
		price(Items.MELON, 4, 1);
		price(Items.MELON_SLICE, 36, 1);
		price(Items.SUGAR_CANE, 24, 1);
		price(Items.SWEET_BERRIES, 24, 1);
		price(Items.GLOW_BERRIES, 20, 1);
		price(Items.COCOA_BEANS, 20, 1);
		price(Items.CACTUS, 32, 1);
		price(Items.BAMBOO, 64, 1);
		price(Items.NETHER_WART, 22, 1);
		price(Items.APPLE, 16, 1);
		price(Items.BREAD, 6, 1);
		price(Items.HONEY_BOTTLE, 4, 1);
		price(Items.HONEYCOMB, 8, 1);
		// animales
		price(Items.LEATHER, 6, 1);
		price(Items.EGG, 16, 1);
		price(Items.FEATHER, 24, 1);
		price(Items.RABBIT_HIDE, 9, 1);
		price(Items.RABBIT_FOOT, 2, 1);
		price(Items.BEEF, 10, 1);
		price(Items.PORKCHOP, 7, 1);
		price(Items.CHICKEN, 14, 1);
		price(Items.MUTTON, 7, 1);
		price(Items.RABBIT, 4, 1);
		price(Items.COOKED_BEEF, 6, 1);
		price(Items.COOKED_PORKCHOP, 5, 1);
		price(Items.COOKED_CHICKEN, 8, 1);
		price(Items.COOKED_MUTTON, 5, 1);
		price(Items.COOKED_RABBIT, 3, 1);
		price(Items.BAKED_POTATO, 12, 1);
		// pesca
		price(Items.COD, 15, 1);
		price(Items.SALMON, 13, 1);
		price(Items.TROPICAL_FISH, 6, 1);
		price(Items.PUFFERFISH, 4, 1);
		price(Items.COOKED_COD, 10, 1);
		price(Items.COOKED_SALMON, 8, 1);
		// mina y fragua
		price(Items.COAL, 15, 1);
		price(Items.CHARCOAL, 15, 1);
		price(Items.IRON_INGOT, 4, 1);
		price(Items.GOLD_INGOT, 3, 1);
		price(Items.COPPER_INGOT, 10, 1);
		price(Items.RAW_IRON, 6, 1);
		price(Items.RAW_GOLD, 5, 1);
		price(Items.RAW_COPPER, 16, 1);
		price(Items.REDSTONE, 24, 1);
		price(Items.LAPIS_LAZULI, 12, 1);
		price(Items.QUARTZ, 12, 1);
		price(Items.AMETHYST_SHARD, 8, 1);
		price(Items.DIAMOND, 1, 4);
		// piedra y tierra
		price(Items.COBBLESTONE, 48, 1);
		price(Items.COBBLED_DEEPSLATE, 48, 1);
		price(Items.STONE, 20, 1);
		price(Items.STONE_BRICKS, 16, 1);
		price(Items.DEEPSLATE_BRICKS, 16, 1);
		price(Items.POLISHED_ANDESITE, 16, 1);
		price(Items.POLISHED_DIORITE, 16, 1);
		price(Items.POLISHED_GRANITE, 16, 1);
		price(Items.TUFF_BRICKS, 16, 1);
		price(Items.POLISHED_BLACKSTONE_BRICKS, 16, 1);
		price(Items.SANDSTONE, 16, 1);
		price(Items.RED_SANDSTONE, 16, 1);
		price(Items.CLAY_BALL, 10, 1);
		price(Items.SAND, 32, 1);
		price(Items.RED_SAND, 32, 1);
		price(Items.GRAVEL, 32, 1);
		price(Items.FLINT, 26, 1);
		// madera y flechas
		price(Items.STICK, 32, 1);
		price(Items.ARROW, 32, 1);
		price(Items.STRING, 14, 1);
		tagPrice(ItemTags.LOGS, 16, 1);
		tagPrice(ItemTags.PLANKS, 64, 1);
		tagPrice(ItemTags.WOOL, 18, 1);
		tagPrice(ItemTags.SAPLINGS, 32, 1);
	}

	private Market() {
	}

	private static void price(Item item, int items, int coins) {
		PRICES.put(item, new Price(items, coins));
	}

	private static void tagPrice(TagKey<Item> tag, int items, int coins) {
		TAG_PRICES.put(tag, new Price(items, coins));
	}

	/** La moneda: la Glory Coin. */
	public static Item currency() {
		return com.evolvedwarfare.registry.ModItems.GLORY_COIN;
	}

	public static boolean isCurrency(ItemStack stack) {
		return !stack.isEmpty() && stack.isOf(currency());
	}

	/** Cuánto paga el mercado por esto, o null si no lo compra (también si tiene encantamientos, nombre, daño...). */
	@Nullable
	public static Price priceOf(ItemStack stack) {
		if (stack.isEmpty() || isCurrency(stack) || !stack.getComponentChanges().isEmpty()) {
			return null;
		}
		Price price = PRICES.get(stack.getItem());
		if (price != null) {
			return price;
		}
		for (Map.Entry<TagKey<Item>, Price> e : TAG_PRICES.entrySet()) {
			if (stack.isIn(e.getKey())) {
				return e.getValue();
			}
		}
		return null;
	}

	/** Monedas sueltas (sin nombre ni nada) que lleva un jugador. */
	public static int countCoins(net.minecraft.entity.player.PlayerInventory inventory) {
		Item coin = currency();
		int total = 0;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (isPlainCoin(stack, coin)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** Le quita monedas al jugador (si no tiene bastantes, no quita ninguna). */
	public static boolean takeCoins(net.minecraft.entity.player.PlayerInventory inventory, int amount) {
		if (countCoins(inventory) < amount) {
			return false;
		}
		Item coin = currency();
		for (int i = 0; i < inventory.size() && amount > 0; i++) {
			ItemStack stack = inventory.getStack(i);
			if (isPlainCoin(stack, coin)) {
				int take = Math.min(amount, stack.getCount());
				stack.decrement(take);
				amount -= take;
			}
		}
		inventory.markDirty();
		return true;
	}

	private static boolean isPlainCoin(ItemStack stack, Item coin) {
		return !stack.isEmpty() && stack.isOf(coin) && stack.getComponentChanges().isEmpty();
	}

	/** Monedas en montones del tamaño máximo. */
	public static java.util.List<ItemStack> coins(int amount) {
		java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
		Item coin = currency();
		int max = coin.getMaxCount();
		while (amount > 0) {
			int n = Math.min(max, amount);
			stacks.add(new ItemStack(coin, n));
			amount -= n;
		}
		return stacks;
	}
}

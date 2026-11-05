package com.evolvedwarfare.logistics;

import com.evolvedwarfare.trade.TraderType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/**
 * Lo que vale cada cosa en el mercado (lo que cuesta nueva, en monedas, en una ciudad de nivel 1): el precio de las
 * tiendas ({@link TraderType#marketPrice}) y, si ninguna la vende ni la compra, lo que valen sus materiales según sus
 * recetas (de mesa, de horno y de cortapiedras), la más barata. Así una espada de hierro vale sus dos lingotes y su palo.
 * Lo que no tiene precio ni se fabrica con cosas con precio vale 0.
 *
 * <p>Se calcula una vez por cada lista de recetas (al cargar el mundo o al recargar los datos) y se guarda.</p>
 */
public final class ItemValues {
	/** Hasta dónde se baja por las recetas (espada → lingote → mena…). */
	private static final int MAX_DEPTH = 6;

	private static RecipeManager cachedFor;
	private static Map<Item, List<RecipeEntry<?>>> byResult = Map.of();
	private static final Map<Item, Double> VALUES = new HashMap<>();
	private static RegistryWrapper.WrapperLookup lookup;

	private ItemValues() {
	}

	/** Lo que vale una unidad de eso, o 0. */
	public static synchronized double value(Item item, World world) {
		double market = TraderType.marketPrice(item);
		if (market > 0.0 || world == null) {
			return market;
		}
		RecipeManager recipes = world.getRecipeManager();
		if (recipes != cachedFor) {
			cachedFor = recipes;
			lookup = world.getRegistryManager();
			index(recipes);
			VALUES.clear();
		}
		double forged = forged(item, new HashSet<>(), 0);
		return forged > 0.0 ? forged : fromRecipes(item, new HashSet<>(), 0);
	}

	private static void index(RecipeManager recipes) {
		Map<Item, List<RecipeEntry<?>>> map = new HashMap<>();
		for (RecipeEntry<?> entry : recipes.values()) {
			Recipe<?> recipe = entry.value();
			RecipeType<?> type = recipe.getType();
			if (type != RecipeType.CRAFTING && type != RecipeType.SMELTING && type != RecipeType.STONECUTTING) {
				continue;
			}
			ItemStack result;
			try {
				result = recipe.getResult(lookup);
			} catch (RuntimeException e) {
				continue;
			}
			if (result.isEmpty() || recipe.getIngredients().isEmpty()) {
				continue;
			}
			map.computeIfAbsent(result.getItem(), k -> new ArrayList<>()).add(entry);
		}
		byResult = map;
	}

	private static double known(Item item, Set<Item> path, int depth) {
		double market = TraderType.marketPrice(item);
		if (market > 0.0) {
			return market;
		}
		double forged = forged(item, path, depth);
		return forged > 0.0 ? forged : fromRecipes(item, path, depth);
	}

	/** Las herramientas y armas forjadas (sin receta de mesa): lo que cuestan sus lingotes (y diamantes), sus palos y su empuñadura. */
	private static double forged(Item item, Set<Item> path, int depth) {
		for (com.evolvedwarfare.smithing.ForgedPart part : com.evolvedwarfare.smithing.ForgedPart.values()) {
			for (com.evolvedwarfare.smithing.ForgeMaterial m : com.evolvedwarfare.smithing.ForgeMaterial.values()) {
				if (part.result(m) != item || depth > 6) {
					continue;
				}
				double ingot = known(m == com.evolvedwarfare.smithing.ForgeMaterial.GOLD ? net.minecraft.item.Items.GOLD_INGOT
						: net.minecraft.item.Items.IRON_INGOT, path, depth + 1);
				double total = part.cost * ingot + part.sticks * known(net.minecraft.item.Items.STICK, path, depth + 1);
				if (m == com.evolvedwarfare.smithing.ForgeMaterial.DIAMOND) {
					total += part.cost * known(net.minecraft.item.Items.DIAMOND, path, depth + 1);
				}
				if (part.extra == com.evolvedwarfare.smithing.ForgedPart.Extra.GRIP) {
					total += known(net.minecraft.item.Items.STRING, path, depth + 1);
				} else if (part.extra == com.evolvedwarfare.smithing.ForgedPart.Extra.CHAIN) {
					total += known(net.minecraft.item.Items.CHAIN, path, depth + 1);
				}
				return total;
			}
		}
		return 0.0;
	}

	/** La receta más barata con todo de precio conocido, dividida entre lo que sale. */
	private static double fromRecipes(Item item, Set<Item> path, int depth) {
		Double cached = VALUES.get(item);
		if (cached != null) {
			return cached;
		}
		if (depth > MAX_DEPTH || !path.add(item)) {
			return 0.0;
		}
		double best = 0.0;
		for (RecipeEntry<?> entry : byResult.getOrDefault(item, List.of())) {
			Recipe<?> recipe = entry.value();
			double total = 0.0;
			boolean ok = true;
			for (Ingredient ingredient : recipe.getIngredients()) {
				if (ingredient.isEmpty()) {
					continue;
				}
				double cheapest = 0.0;
				for (ItemStack option : ingredient.getMatchingStacks()) {
					double v = known(option.getItem(), path, depth + 1);
					if (v > 0.0 && (cheapest == 0.0 || v < cheapest)) {
						cheapest = v;
					}
				}
				if (cheapest <= 0.0) {
					ok = false;
					break;
				}
				total += cheapest;
			}
			int made = Math.max(1, recipe.getResult(lookup).getCount());
			if (ok && total > 0.0) {
				double each = total / made;
				if (best == 0.0 || each < best) {
					best = each;
				}
			}
		}
		path.remove(item);
		// solo se guarda lo que no depende de por dónde se ha llegado (sin cortes por ciclos)
		if (best > 0.0 || path.isEmpty()) {
			VALUES.put(item, best);
		}
		return best;
	}
}

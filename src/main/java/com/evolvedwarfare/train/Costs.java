package com.evolvedwarfare.train;

import java.util.List;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;

/** Lo que cuesta montar algo en la mesa de ensamblaje: ingredientes, cuántos y si los lleva el jugador. */
public final class Costs {
	private Costs() {
	}

	/** Un ingrediente: qué y cuántos (y qué dibujar). */
	public record Cost(Ingredient ingredient, int count, Item icon) {
	}

	/** ¿Cuántos tiene de ese ingrediente? */
	public static int count(PlayerInventory inv, Ingredient ing) {
		int n = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty() && ing.test(s)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** ¿Cuántas veces le llega para eso? */
	public static int affordable(PlayerInventory inv, List<Cost> costs) {
		int best = Integer.MAX_VALUE;
		for (Cost c : costs) {
			best = Math.min(best, count(inv, c.ingredient()) / c.count());
		}
		return best == Integer.MAX_VALUE ? 0 : best;
	}

	/** Gasta esos ingredientes (si los tiene todos). */
	public static boolean pay(PlayerInventory inv, List<Cost> costs) {
		if (affordable(inv, costs) < 1) {
			return false;
		}
		for (Cost c : costs) {
			int left = c.count();
			for (int i = 0; i < inv.size() && left > 0; i++) {
				ItemStack s = inv.getStack(i);
				if (!s.isEmpty() && c.ingredient().test(s)) {
					int take = Math.min(left, s.getCount());
					s.decrement(take);
					left -= take;
				}
			}
		}
		inv.markDirty();
		return true;
	}
}

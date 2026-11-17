package com.evolvedwarfare.food;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/** Salar (fase 98): una sal y hasta 8 de la misma comida (sin salar) dan esa comida salada, que aguanta 4 veces más. */
public class SaltingRecipe extends SpecialCraftingRecipe {
	/** El reloj del mundo al craftear (lo pone {@link #matches}: la receta no recibe el mundo al hacer el resultado). */
	private long now;

	public SaltingRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		int salt = 0, food = 0;
		ItemStack first = ItemStack.EMPTY;
		for (int k = 0; k < input.getSize(); k++) {
			ItemStack s = input.getStackInSlot(k);
			if (s.isEmpty()) {
				continue;
			}
			if (s.isOf(FoodItems.SALT)) {
				salt++;
			} else if (FoodRot.perishable(s) && (FoodRot.preserved(s) & FoodRot.SALTED) == 0
					&& (first.isEmpty() || ItemStack.areItemsAndComponentsEqual(first, s))) {
				if (first.isEmpty()) {
					first = s;
				}
				food++;
			} else {
				return false;
			}
		}
		if (salt == 1 && food >= 1) {
			now = world.getTime();
			return true;
		}
		return false;
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		ItemStack first = ItemStack.EMPTY;
		int food = 0;
		for (int k = 0; k < input.getSize(); k++) {
			ItemStack s = input.getStackInSlot(k);
			if (!s.isEmpty() && !s.isOf(FoodItems.SALT)) {
				if (first.isEmpty()) {
					first = s;
				}
				food++;
			}
		}
		if (first.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack out = first.copyWithCount(food);
		FoodRot.preserve(out, FoodRot.SALTED, now);
		return out;
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return FoodItems.SALTING;
	}
}

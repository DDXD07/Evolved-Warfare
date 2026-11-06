package com.evolvedwarfare.smithing;

import com.evolvedwarfare.registry.ModBlocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/** El molde de arcilla: un frasco de agua, una bola de arcilla y un palo (en cualquier sitio); se queda el frasco vacío. */
public class ClayMoldRecipe extends SpecialCraftingRecipe {
	public static RecipeSerializer<ClayMoldRecipe> SERIALIZER;

	public ClayMoldRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	public static boolean isWater(ItemStack s) {
		if (!s.isOf(Items.POTION)) {
			return false;
		}
		PotionContentsComponent p = s.get(DataComponentTypes.POTION_CONTENTS);
		return p != null && p.matches(Potions.WATER);
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		int water = 0;
		int clay = 0;
		int stick = 0;
		for (int i = 0; i < input.getSize(); i++) {
			ItemStack s = input.getStackInSlot(i);
			if (s.isEmpty()) {
				continue;
			}
			if (isWater(s)) {
				water++;
			} else if (s.isOf(Items.CLAY_BALL)) {
				clay++;
			} else if (s.isOf(Items.STICK)) {
				stick++;
			} else {
				return false;
			}
		}
		return water == 1 && clay == 1 && stick == 1;
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		return new ItemStack(ModBlocks.CLAY_MOLD);
	}

	@Override
	public DefaultedList<ItemStack> getRemainder(CraftingRecipeInput input) {
		DefaultedList<ItemStack> out = DefaultedList.ofSize(input.getSize(), ItemStack.EMPTY);
		for (int i = 0; i < input.getSize(); i++) {
			if (isWater(input.getStackInSlot(i))) {
				out.set(i, new ItemStack(Items.GLASS_BOTTLE));
			}
		}
		return out;
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}
}

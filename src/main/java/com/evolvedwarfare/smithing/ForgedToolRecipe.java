package com.evolvedwarfare.smithing;

import com.evolvedwarfare.registry.ModComponents;
import com.evolvedwarfare.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Montar una pieza forjada en la mesa: la pieza y su mango (los palos que pida; las hojas, con una empuñadura de cuero o
 * cuerda; el mangual, con una cadena), en cualquier sitio de la rejilla. Sale la herramienta o el arma con la calidad de
 * la forja.
 */
public class ForgedToolRecipe extends SpecialCraftingRecipe {
	public static RecipeSerializer<ForgedToolRecipe> SERIALIZER;

	public ForgedToolRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	@Nullable
	private static ItemStack result(CraftingRecipeInput input) {
		ItemStack part = ItemStack.EMPTY;
		int sticks = 0;
		int grips = 0;
		int chains = 0;
		for (int i = 0; i < input.getSize(); i++) {
			ItemStack s = input.getStackInSlot(i);
			if (s.isEmpty()) {
				continue;
			}
			if (s.isOf(ModItems.FORGED_PART)) {
				if (!part.isEmpty()) {
					return null;
				}
				part = s;
			} else if (s.isOf(Items.STICK)) {
				sticks++;
			} else if (s.isOf(Items.LEATHER) || s.isOf(Items.STRING)) {
				grips++;
			} else if (s.isOf(Items.CHAIN)) {
				chains++;
			} else {
				return null;
			}
		}
		PartData data = part.isEmpty() ? null : part.get(ModComponents.FORGED_PART);
		if (data == null) {
			return null;
		}
		ForgedPart kind = ForgedPart.byId(data.part());
		ForgeMaterial material = ForgeMaterial.byId(data.material());
		if (kind == null || material == null || sticks != kind.sticks) {
			return null;
		}
		if (grips != (kind.extra == ForgedPart.Extra.GRIP ? 1 : 0) || chains != (kind.extra == ForgedPart.Extra.CHAIN ? 1 : 0)) {
			return null;
		}
		Item item = kind.result(material);
		return item == null ? null : ForgeQuality.apply(new ItemStack(item), data.quality());
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		return result(input) != null;
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		ItemStack out = result(input);
		return out == null ? ItemStack.EMPTY : out;
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}
}

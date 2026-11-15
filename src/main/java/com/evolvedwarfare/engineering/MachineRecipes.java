package com.evolvedwarfare.engineering;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Lo que hacen la muela y el aserradero. El trabajo se mide en «rpm·tic»: a 16 rpm, 480 son 30 tics (un segundo y
 * medio); más deprisa, antes.
 */
public final class MachineRecipes {
	public record Recipe(int work, Function<Random, List<ItemStack>> output) {
	}

	private MachineRecipes() {
	}

	private static Recipe fixed(int work, Item item, int count) {
		return new Recipe(work, r -> List.of(new ItemStack(item, count)));
	}

	private static Recipe withChance(int work, Item item, int count, Item extra, float chance) {
		return new Recipe(work, r -> {
			List<ItemStack> out = new ArrayList<>();
			out.add(new ItemStack(item, count));
			if (r.nextFloat() < chance) {
				out.add(new ItemStack(extra));
			}
			return out;
		});
	}

	/** La muela. */
	@Nullable
	public static Recipe millstone(ItemStack in) {
		if (in.isOf(Items.WHEAT)) {
			return fixed(480, Engineering.FLOUR, 1);
		}
		if (in.isOf(Items.COBBLESTONE)) {
			return fixed(800, Items.GRAVEL, 1);
		}
		if (in.isOf(Items.GRAVEL)) {
			return withChance(800, Items.SAND, 1, Items.FLINT, 0.1F);
		}
		if (in.isOf(Items.BONE)) {
			return fixed(320, Items.BONE_MEAL, 5);
		}
		if (in.isOf(Items.SUGAR_CANE)) {
			return fixed(320, Items.SUGAR, 2);
		}
		if (in.isOf(Items.RAW_IRON) || in.isOf(Items.RAW_COPPER) || in.isOf(Items.RAW_GOLD)) {
			return withChance(1200, in.getItem(), 1, in.getItem(), 0.35F);
		}
		return null;
	}

	/** El aserradero. */
	@Nullable
	public static Recipe saw(ItemStack in) {
		if (in.isIn(ItemTags.LOGS)) {
			Item planks = planksOf(in.getItem());
			return planks == null ? null : fixed(400, planks, 6);
		}
		if (in.isIn(ItemTags.PLANKS)) {
			return fixed(160, Items.STICK, 3);
		}
		return null;
	}

	/** Los tablones de un tronco o madera (con corteza o sin ella), o null. */
	@Nullable
	public static Item planksOf(Item log) {
		Identifier id = Registries.ITEM.getId(log);
		String path = id.getPath();
		if (path.startsWith("stripped_")) {
			path = path.substring("stripped_".length());
		}
		String wood = path.replaceAll("_(log|wood|stem|hyphae)$", "");
		Identifier planks = Identifier.of(id.getNamespace(), wood + "_planks");
		return !wood.equals(path) && Registries.ITEM.containsId(planks) ? Registries.ITEM.get(planks) : null;
	}
}

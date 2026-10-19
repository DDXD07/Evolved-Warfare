package com.evolvedwarfare.building;

import com.evolvedwarfare.block.WorkSiteBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

/** Qué objetos sirven para cada material de obra y cómo se muestran. */
public final class BuildMaterials {
	private BuildMaterials() {
	}

	public static boolean accepts(Material material, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return switch (material) {
			case PLANKS -> stack.isIn(ItemTags.PLANKS);
			case LOGS -> stack.isIn(ItemTags.LOGS);
			case STONE -> stack.isIn(ItemTags.STONE_CRAFTING_MATERIALS) || stack.isOf(Items.STONE) || stack.isOf(Items.STONE_BRICKS)
					|| stack.isOf(Items.MOSSY_COBBLESTONE) || stack.isOf(Items.SMOOTH_STONE) || stack.isOf(Items.ANDESITE);
			case GLASS -> stack.isOf(Items.GLASS) || stack.isOf(Items.GLASS_PANE);
			case TORCH -> stack.isOf(Items.TORCH) || stack.isOf(Items.LANTERN);
			case WOOL -> stack.isIn(ItemTags.WOOL);
			case HAY -> stack.isOf(Items.HAY_BLOCK);
			case FREE -> true;
		};
	}

	/** ¿Hay de ese material en los suministros del puesto? */
	public static boolean available(WorkSiteBlockEntity site, Material material) {
		return material == Material.FREE || site.count(stack -> accepts(material, stack), false) > 0;
	}

	/** Gasta una unidad de los suministros. */
	public static boolean consume(WorkSiteBlockEntity site, Material material) {
		return material == Material.FREE || site.consume(stack -> accepts(material, stack), 1, false);
	}

	public static Item icon(Material material) {
		return switch (material) {
			case PLANKS -> Items.OAK_PLANKS;
			case LOGS -> Items.OAK_LOG;
			case STONE -> Items.COBBLESTONE;
			case GLASS -> Items.GLASS_PANE;
			case TORCH -> Items.TORCH;
			case WOOL -> Items.WHITE_WOOL;
			case HAY -> Items.HAY_BLOCK;
			case FREE -> Items.DIRT;
		};
	}

	public static String translationKey(Material material) {
		return "material.evolvedwarfare." + material.id();
	}
}

package com.evolvedwarfare.building;

import com.evolvedwarfare.block.WorkSiteBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import org.jetbrains.annotations.Nullable;
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
			case EXACT -> false;
		};
	}

	// ------------------------------------------------------------------ planos escaneados (material exacto)

	/** El objeto que gasta un bloque (aire si no gasta nada: fuego, portales...). */
	public static Item itemFor(BlockState state) {
		Block block = state.getBlock();
		Item item = block.asItem();
		if (item != Items.AIR) {
			return item;
		}
		if (block == Blocks.REDSTONE_WIRE) {
			return Items.REDSTONE;
		}
		if (block == Blocks.TRIPWIRE) {
			return Items.STRING;
		}
		if (block == Blocks.WHEAT) {
			return Items.WHEAT_SEEDS;
		}
		if (block == Blocks.CARROTS) {
			return Items.CARROT;
		}
		if (block == Blocks.POTATOES) {
			return Items.POTATO;
		}
		if (block == Blocks.BEETROOTS) {
			return Items.BEETROOT_SEEDS;
		}
		if (block == Blocks.MELON_STEM || block == Blocks.ATTACHED_MELON_STEM) {
			return Items.MELON_SEEDS;
		}
		if (block == Blocks.PUMPKIN_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM) {
			return Items.PUMPKIN_SEEDS;
		}
		if (block == Blocks.COCOA) {
			return Items.COCOA_BEANS;
		}
		if (block == Blocks.SWEET_BERRY_BUSH) {
			return Items.SWEET_BERRIES;
		}
		if (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT) {
			return Items.GLOW_BERRIES;
		}
		if (block == Blocks.BAMBOO_SAPLING) {
			return Items.BAMBOO;
		}
		if (block == Blocks.KELP_PLANT) {
			return Items.KELP;
		}
		return Items.AIR;
	}

	/** Cuántos objetos gasta (una losa doble, dos). */
	public static int costFor(BlockState state) {
		if (state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE) && state.get(SlabBlock.TYPE) == SlabType.DOUBLE) {
			return 2;
		}
		return 1;
	}

	/** ¿Sirve este objeto para ese bloque? (la tierra vale para césped, camino, tierra de cultivo...) */
	public static boolean acceptsExact(BlockState state, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Item item = itemFor(state);
		if (stack.isOf(item)) {
			return true;
		}
		Block block = state.getBlock();
		boolean soil = block == Blocks.GRASS_BLOCK || block == Blocks.DIRT_PATH || block == Blocks.FARMLAND || block == Blocks.PODZOL
				|| block == Blocks.MYCELIUM || block == Blocks.ROOTED_DIRT || block == Blocks.COARSE_DIRT;
		return soil && stack.isOf(Items.DIRT);
	}

	/** ¿Hay en los suministros lo que gasta esta pieza? */
	public static boolean available(WorkSiteBlockEntity site, Material material, @Nullable BlockState state) {
		if (material != Material.EXACT) {
			return available(site, material);
		}
		if (state == null || itemFor(state) == Items.AIR) {
			return true;
		}
		return site.count(stack -> acceptsExact(state, stack), false) >= costFor(state);
	}

	public static boolean consume(WorkSiteBlockEntity site, Material material, @Nullable BlockState state) {
		if (material != Material.EXACT) {
			return consume(site, material);
		}
		if (state == null || itemFor(state) == Items.AIR) {
			return true;
		}
		return site.consume(stack -> acceptsExact(state, stack), costFor(state), false);
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
			case EXACT -> Items.PAPER;
		};
	}

	public static String translationKey(Material material) {
		return "material.evolvedwarfare." + material.id();
	}
}

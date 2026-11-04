package com.evolvedwarfare.climate;

import com.evolvedwarfare.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Los cultivos nuevos (tomate, maíz, col, cebolla y ajo) y cómo se consiguen: <b>arrancando hierba</b> (a veces suelta
 * semillas, las del clima de ese bioma: tomate y maíz donde hace calor; cebolla, maíz y col en tierras templadas; col y
 * ajo donde hace frío), <b>en el tendero</b> (las vende todas) y, de lo cosechado, <b>en la mesa</b> (un tomate, una col
 * o una mazorca dan semillas; la cebolla y el ajo se plantan tal cual). También se compostan, y quitan sed lo que tiene
 * agua.
 */
public final class Crops {
	/** Una de cada tantas hierbas suelta semillas de los cultivos nuevos. */
	private static final int GRASS_CHANCE = 20;

	private Crops() {
	}

	public static void register() {
		CropSeasons.init();
		CompostingChanceRegistry compost = CompostingChanceRegistry.INSTANCE;
		for (Item seeds : new Item[] {ModItems.TOMATO_SEEDS, ModItems.CORN_SEEDS, ModItems.CABBAGE_SEEDS}) {
			compost.add(seeds, 0.3F);
		}
		for (Item food : new Item[] {ModItems.TOMATO, ModItems.CORN, ModItems.CABBAGE, ModItems.ONION, ModItems.GARLIC}) {
			compost.add(food, 0.65F);
		}
		compost.add(ModItems.ROASTED_CORN, 0.85F);
		Thirst.food(ModItems.TOMATO, 3.0F);
		Thirst.food(ModItems.CABBAGE, 2.0F);
		Thirst.food(ModItems.CORN, 1.0F);
		Thirst.food(ModItems.ONION, 1.0F);
		Thirst.food(ModItems.VEGETABLE_STEW, 6.0F);
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> grass(world, player, pos, state));
	}

	private static boolean isGrass(BlockState state) {
		Block b = state.getBlock();
		return b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS || b == Blocks.FERN || b == Blocks.LARGE_FERN;
	}

	/** Las semillas que pueden salir de la hierba según la temperatura del bioma (sin la estación). */
	public static List<Item> seedsFor(float biomeTemperature) {
		if (biomeTemperature >= 0.95F) {
			return List.of(ModItems.TOMATO_SEEDS, ModItems.CORN_SEEDS);
		}
		if (biomeTemperature >= 0.45F) {
			return List.of(ModItems.ONION, ModItems.CORN_SEEDS, ModItems.CABBAGE_SEEDS, ModItems.TOMATO_SEEDS);
		}
		return List.of(ModItems.CABBAGE_SEEDS, ModItems.GARLIC);
	}

	private static void grass(World world, PlayerEntity player, BlockPos pos, BlockState state) {
		if (world.isClient || !isGrass(state) || player.isCreative() || player.getMainHandStack().isOf(Items.SHEARS)
				|| world.random.nextInt(GRASS_CHANCE) != 0) {
			return;
		}
		List<Item> options = seedsFor(world.getBiome(pos).value().getTemperature());
		Block.dropStack(world, pos, new ItemStack(options.get(world.random.nextInt(options.size()))));
	}
}

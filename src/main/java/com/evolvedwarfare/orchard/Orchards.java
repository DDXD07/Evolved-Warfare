package com.evolvedwarfare.orchard;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.climate.CropSeasons;
import com.evolvedwarfare.climate.Thirst;
import com.evolvedwarfare.registry.ModBlocks;
import com.evolvedwarfare.registry.ModItems;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Los frutales: manzano, peral, ciruelo, melocotonero, limonero y naranjo. Salen sueltos por el mundo (cada uno en sus
 * biomas), sus hojas dan su brote (como cualquier árbol) y su fruta; florecen y maduran en sus estaciones.
 */
public final class Orchards {
	private Orchards() {
	}

	@SafeVarargs
	private static void spawn(FruitKind kind, RegistryKey<Biome>... biomes) {
		RegistryKey<PlacedFeature> placed = RegistryKey.of(RegistryKeys.PLACED_FEATURE, EvolvedWarfare.id(kind.id + "_tree"));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(biomes), GenerationStep.Feature.VEGETAL_DECORATION, placed);
	}

	public static void register() {
		for (FruitKind kind : FruitKind.values()) {
			CropSeasons.register(ModBlocks.FRUIT_LEAVES.get(kind), kind.kind.seasons);
			FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.FRUIT_LEAVES.get(kind), 30, 60);
			CompostingChanceRegistry.INSTANCE.add(ModBlocks.FRUIT_LEAVES.get(kind), 0.3F);
			CompostingChanceRegistry.INSTANCE.add(ModBlocks.FRUIT_SAPLINGS.get(kind), 0.3F);
		}
		for (Item fruit : new Item[] {ModItems.PEAR, ModItems.PLUM, ModItems.PEACH, ModItems.LEMON, ModItems.ORANGE}) {
			CompostingChanceRegistry.INSTANCE.add(fruit, 0.65F);
		}
		Thirst.food(ModItems.PEAR, 3.0F);
		Thirst.food(ModItems.PLUM, 2.0F);
		Thirst.food(ModItems.PEACH, 3.0F);
		Thirst.food(ModItems.LEMON, 1.5F);
		Thirst.food(ModItems.ORANGE, 3.5F);
		spawn(FruitKind.APPLE, BiomeKeys.FOREST, BiomeKeys.FLOWER_FOREST, BiomeKeys.PLAINS, BiomeKeys.MEADOW);
		spawn(FruitKind.PEAR, BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.PLAINS);
		spawn(FruitKind.PLUM, BiomeKeys.FOREST, BiomeKeys.DARK_FOREST, BiomeKeys.FLOWER_FOREST, BiomeKeys.MEADOW);
		spawn(FruitKind.PEACH, BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.SAVANNA);
		spawn(FruitKind.LEMON, BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.SPARSE_JUNGLE, BiomeKeys.BEACH);
		spawn(FruitKind.ORANGE, BiomeKeys.SAVANNA, BiomeKeys.SPARSE_JUNGLE, BiomeKeys.JUNGLE, BiomeKeys.SUNFLOWER_PLAINS);
	}
}

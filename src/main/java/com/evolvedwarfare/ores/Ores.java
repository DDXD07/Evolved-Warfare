package com.evolvedwarfare.ores;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ColoredFallingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.ColorCode;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

/**
 * Las menas realistas (fase 49): los yacimientos de vanilla se cambian (con los mismos nombres, en los datos) por mantos,
 * lentes, cuerpos, vetas y chimeneas que dependen del bioma y de la profundidad, como en la vida real; el oro de aluvión de
 * los ríos; los guijarros que delatan un yacimiento; y el pico de prospector.
 */
public final class Ores {
	public static final Feature<DepositFeature.Config> DEPOSIT = Registry.register(Registries.FEATURE, EvolvedWarfare.id("deposit"), new DepositFeature());
	public static final Feature<DefaultFeatureConfig> ALLUVIAL_GOLD = Registry.register(Registries.FEATURE, EvolvedWarfare.id("alluvial_gold"),
			new AlluvialGoldFeature());
	public static final PlacementModifierType<InBiomesPlacement> IN_BIOMES = Registry.register(Registries.PLACEMENT_MODIFIER_TYPE,
			EvolvedWarfare.id("in_biomes"), () -> InBiomesPlacement.CODEC);

	public static final Block PEBBLES = Registry.register(Registries.BLOCK, EvolvedWarfare.id("mineral_pebbles"), new MineralPebblesBlock(
			AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).noCollision().breakInstantly().sounds(BlockSoundGroup.STONE).nonOpaque()
					.pistonBehavior(net.minecraft.block.piston.PistonBehavior.DESTROY)));
	public static final Block GOLD_GRAVEL = Registry.register(Registries.BLOCK, EvolvedWarfare.id("gold_bearing_gravel"), new ColoredFallingBlock(
			new ColorCode(-8356741), AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).instrument(NoteBlockInstrument.SNARE).strength(0.6F)
					.sounds(BlockSoundGroup.GRAVEL)));

	public static final List<Item> ITEMS = new ArrayList<>();
	public static final Item GOLD_GRAVEL_ITEM = item("gold_bearing_gravel", new BlockItem(GOLD_GRAVEL, new Item.Settings()));
	public static final Item PROSPECTOR_PICK = item("prospector_pick", new ProspectorPickItem(new Item.Settings().maxDamage(160)));

	private Ores() {
	}

	private static Item item(String name, Item item) {
		ITEMS.add(Registry.register(Registries.ITEM, EvolvedWarfare.id(name), item));
		return item;
	}

	public static void register() {
		RegistryKey<PlacedFeature> alluvial = RegistryKey.of(RegistryKeys.PLACED_FEATURE, EvolvedWarfare.id("alluvial_gold"));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_RIVER), GenerationStep.Feature.UNDERGROUND_ORES, alluvial);
	}
}

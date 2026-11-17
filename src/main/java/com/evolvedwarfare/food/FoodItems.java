package com.evolvedwarfare.food;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.List;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** La comida que se pudre (fase 98): la sal, la comida podrida, la nevera y la receta de salar. */
public final class FoodItems {
	private static class Described extends Item {
		Described(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
		}
	}

	public static final Item SALT = Registry.register(Registries.ITEM, EvolvedWarfare.id("salt"), new Described(new Item.Settings()));
	public static final Item ROTTEN_FOOD = Registry.register(Registries.ITEM, EvolvedWarfare.id("rotten_food"), new Described(new Item.Settings()
			.food(new FoodComponent.Builder().nutrition(1).saturationModifier(0.1F)
					.statusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 20 * 30, 0), 0.8F)
					.statusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 20 * 10, 0), 0.5F)
					.statusEffect(new StatusEffectInstance(StatusEffects.POISON, 20 * 5, 0), 0.3F).build())));

	public static final Block ICEBOX = Registry.register(Registries.BLOCK, EvolvedWarfare.id("icebox"), new IceboxBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.PALE_PURPLE).strength(2.0F).sounds(BlockSoundGroup.WOOD)));
	public static final Item ICEBOX_ITEM = Registry.register(Registries.ITEM, EvolvedWarfare.id("icebox"), new BlockItem(ICEBOX, new Item.Settings()) {
		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("block.evolvedwarfare.icebox.desc").formatted(Formatting.GRAY));
		}
	});
	public static final BlockEntityType<IceboxBlockEntity> ICEBOX_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			EvolvedWarfare.id("icebox"), BlockEntityType.Builder.create(IceboxBlockEntity::new, ICEBOX).build(null));

	public static final RecipeSerializer<SaltingRecipe> SALTING = Registry.register(Registries.RECIPE_SERIALIZER, EvolvedWarfare.id("salting"),
			new SpecialRecipeSerializer<>(SaltingRecipe::new));

	private FoodItems() {
	}

	public static void register() {
		CompostingChanceRegistry.INSTANCE.add(ROTTEN_FOOD, 0.65F);
		FoodRot.register();
		net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(
				net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM_GROUP, EvolvedWarfare.id("survival")))
				.register(entries -> {
					entries.add(SALT);
					entries.add(ICEBOX_ITEM);
					entries.add(ROTTEN_FOOD);
				});
	}
}

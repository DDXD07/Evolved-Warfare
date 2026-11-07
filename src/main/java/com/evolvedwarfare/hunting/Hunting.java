package com.evolvedwarfare.hunting;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * La caza (fase 46): un animal cazado cae al suelo y se queda ahí (no suelta nada); con un cuchillo en la mano, clic derecho
 * y se despelleja (cinco segundos agachado): cuero, carne y lo demás que llevara. Queda una mancha de sangre un rato.
 */
public final class Hunting {
	public static final TagKey<Item> KNIVES = TagKey.of(RegistryKeys.ITEM, EvolvedWarfare.id("knives"));
	/** Lo que se tarda en despellejar (tics). */
	public static final int SKIN_TIME = 100;

	public static final EntityType<CarcassEntity> CARCASS = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("carcass"),
			EntityType.Builder.<CarcassEntity>create(CarcassEntity::new, SpawnGroup.MISC).dimensions(1.0F, 0.5F).maxTrackingRange(8).build("carcass"));
	public static final EntityType<BloodStainEntity> BLOOD_STAIN = Registry.register(Registries.ENTITY_TYPE, EvolvedWarfare.id("blood_stain"),
			EntityType.Builder.<BloodStainEntity>create(BloodStainEntity::new, SpawnGroup.MISC).dimensions(1.0F, 0.05F).maxTrackingRange(8)
					.disableSaving().build("blood_stain"));

	public static final ToolMaterial COPPER = material(180, 5.0F, 1.0F, BlockTags.INCORRECT_FOR_STONE_TOOL, 13, Items.COPPER_INGOT);
	public static final ToolMaterial OBSIDIAN = material(900, 7.0F, 2.5F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 9, Items.OBSIDIAN);

	public static final List<Item> ITEMS = new ArrayList<>();
	public static final Item FLINT_KNIFE = knife("flint_knife", com.evolvedwarfare.wood.Woodcutting.FLINT);
	public static final Item COPPER_KNIFE = knife("copper_knife", COPPER);
	public static final Item IRON_KNIFE = knife("iron_knife", ToolMaterials.IRON);
	public static final Item OBSIDIAN_KNIFE = knife("obsidian_knife", OBSIDIAN);

	private Hunting() {
	}

	public static void register() {
	}

	private static ToolMaterial material(int durability, float speed, float damage, TagKey<Block> inverse, int enchant, Item repair) {
		return new ToolMaterial() {
			@Override
			public int getDurability() {
				return durability;
			}

			@Override
			public float getMiningSpeedMultiplier() {
				return speed;
			}

			@Override
			public float getAttackDamage() {
				return damage;
			}

			@Override
			public TagKey<Block> getInverseTag() {
				return inverse;
			}

			@Override
			public int getEnchantability() {
				return enchant;
			}

			@Override
			public Ingredient getRepairIngredient() {
				return Ingredient.ofItems(repair);
			}
		};
	}

	private static Item knife(String name, ToolMaterial material) {
		Item item = Registry.register(Registries.ITEM, EvolvedWarfare.id(name),
				new KnifeItem(material, new Item.Settings().attributeModifiers(SwordItem.createAttributeModifiers(material, 1, -1.8F))));
		ITEMS.add(item);
		return item;
	}

	public static class KnifeItem extends SwordItem {
		public KnifeItem(ToolMaterial material, Settings settings) {
			super(material, settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
			tooltip.add(Text.translatable("item.evolvedwarfare.knife.desc").formatted(Formatting.GRAY));
		}
	}

	public static boolean isKnife(ItemStack stack) {
		return stack.isIn(KNIVES) || stack.getItem() instanceof KnifeItem;
	}

	/** ¿Se queda en el suelo para despellejarlo? Los animales adultos (no las mascotas) que dan carne. */
	public static boolean skinnable(LivingEntity entity) {
		return entity instanceof AnimalEntity && !entity.isBaby() && !(entity instanceof TameableEntity t && t.isTamed());
	}

	public static boolean isMeat(ItemStack stack) {
		return stack.contains(DataComponentTypes.FOOD);
	}

	/** Al morir: si lo que suelta lleva carne, en vez de soltarlo se queda el animal en el suelo (con lo que soltaría guardado). */
	public static boolean spawnCarcass(ServerWorld world, LivingEntity dead, List<ItemStack> loot) {
		boolean meat = loot.stream().anyMatch(Hunting::isMeat);
		if (!meat) {
			return false;
		}
		CarcassEntity carcass = CARCASS.create(world);
		if (carcass == null) {
			return false;
		}
		carcass.copyFrom(dead, loot);
		world.spawnEntity(carcass);
		return true;
	}
}

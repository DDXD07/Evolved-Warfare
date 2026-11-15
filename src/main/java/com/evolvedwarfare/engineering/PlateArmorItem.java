package com.evolvedwarfare.engineering;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * La armadura de placas (fase 78), de hierro o de acero: protege más que la de su metal y resiste el empuje, pero pesa
 * (−3 % de velocidad por pieza). Se hace con placas del martinete y se repara con ellas.
 */
public class PlateArmorItem extends ArmorItem {
	/** Un juego: armadura (casco, peto, grebas, botas), dureza, resistencia al empuje por pieza, durabilidad y encantamiento. */
	public enum Kind {
		IRON("iron_plate", new int[] {3, 7, 6, 3}, 1.0F, 0.05F, 22, 9),
		STEEL("steel_plate", new int[] {3, 8, 7, 3}, 2.5F, 0.1F, 30, 11);

		public final String id;
		final int[] protection;
		public final float toughness, knockback;
		public final int durability, enchantability;
		private RegistryEntry<ArmorMaterial> material;

		Kind(String id, int[] protection, float toughness, float knockback, int durability, int enchantability) {
			this.id = id;
			this.protection = protection;
			this.toughness = toughness;
			this.knockback = knockback;
			this.durability = durability;
			this.enchantability = enchantability;
		}

		public int protection(Type type) {
			return switch (type) {
				case HELMET -> protection[0];
				case CHESTPLATE, BODY -> protection[1];
				case LEGGINGS -> protection[2];
				case BOOTS -> protection[3];
			};
		}

		Item plate() {
			return this == IRON ? Metalwork.IRON_PLATE : Metalwork.STEEL_PLATE;
		}

		RegistryEntry<ArmorMaterial> material() {
			if (material == null) {
				Map<Type, Integer> defense = new EnumMap<>(Type.class);
				for (Type t : Type.values()) {
					defense.put(t, protection(t));
				}
				material = Registry.registerReference(Registries.ARMOR_MATERIAL, EvolvedWarfare.id(id), new ArmorMaterial(defense, enchantability,
						SoundEvents.ITEM_ARMOR_EQUIP_IRON, () -> Ingredient.ofItems(plate()), List.of(new ArmorMaterial.Layer(EvolvedWarfare.id(id))),
						toughness, knockback));
			}
			return material;
		}
	}

	/** Lo que pesa cada pieza (velocidad). */
	public static final double WEIGHT = -0.03;
	private final Kind kind;

	public PlateArmorItem(Kind kind, Type type) {
		super(kind.material(), type, new Item.Settings().maxDamage(type.getMaxDamage(kind.durability)).attributeModifiers(modifiers(kind, type)));
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	private static AttributeModifiersComponent modifiers(Kind kind, Type type) {
		AttributeModifierSlot slot = AttributeModifierSlot.forEquipmentSlot(type.getEquipmentSlot());
		String name = kind.id + "." + type.getName();
		return AttributeModifiersComponent.builder()
				.add(EntityAttributes.GENERIC_ARMOR, new EntityAttributeModifier(EvolvedWarfare.id("plate_armor." + name), kind.protection(type),
						EntityAttributeModifier.Operation.ADD_VALUE), slot)
				.add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, new EntityAttributeModifier(EvolvedWarfare.id("plate_toughness." + name), kind.toughness,
						EntityAttributeModifier.Operation.ADD_VALUE), slot)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, new EntityAttributeModifier(EvolvedWarfare.id("plate_knockback." + name),
						kind.knockback, EntityAttributeModifier.Operation.ADD_VALUE), slot)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, new EntityAttributeModifier(EvolvedWarfare.id("plate_weight." + name), WEIGHT,
						EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), slot)
				.build();
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("armor.evolvedwarfare.plate.weight").formatted(Formatting.RED));
	}
}

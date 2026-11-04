package com.evolvedwarfare.climate;

import com.evolvedwarfare.EvolvedWarfare;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Ropa para el clima: la de piel abriga (sube la temperatura que notas cuando hace frío) y la de lino refresca (la baja
 * cuando hace calor). Cada una estorba un poco en el tiempo contrario: la piel da calor de más en verano y el lino abriga
 * menos en invierno. Protegen poco de los golpes (como el cuero).
 */
public class ClimateWearItem extends ArmorItem {
	public enum Kind {
		/** Gorro, abrigo, pantalones y botas de piel. */
		FUR("fur", new int[] {3, 6, 4, 2}, new int[] {1, 3, 2, 1}),
		/** Sombrero de paja, túnica, calzones de lino y sandalias. */
		LINEN("linen", new int[] {4, 5, 3, 2}, new int[] {1, 1, 1, 0});

		final String id;
		/** Grados que da cada pieza (casco, pecho, piernas, pies). */
		final int[] degrees;
		final int[] armor;

		Kind(String id, int[] degrees, int[] armor) {
			this.id = id;
			this.degrees = degrees;
			this.armor = armor;
		}
	}

	private static final Map<Kind, RegistryEntry<ArmorMaterial>> MATERIALS = new EnumMap<>(Kind.class);

	private final Kind kind;
	private final int degrees;

	public ClimateWearItem(Kind kind, Type type, Settings settings) {
		super(material(kind), type, settings);
		this.kind = kind;
		this.degrees = kind.degrees[index(type)];
	}

	private static int index(Type type) {
		return switch (type) {
			case HELMET -> 0;
			case CHESTPLATE -> 1;
			case LEGGINGS -> 2;
			default -> 3;
		};
	}

	public static RegistryEntry<ArmorMaterial> material(Kind kind) {
		return MATERIALS.computeIfAbsent(kind, k -> {
			Map<Type, Integer> defense = new EnumMap<>(Type.class);
			for (Type type : Type.values()) {
				defense.put(type, type == Type.BODY ? 0 : k.armor[index(type)]);
			}
			Item repair = k == Kind.FUR ? Items.LEATHER : Items.STRING;
			ArmorMaterial value = new ArmorMaterial(defense, 15, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, () -> Ingredient.ofItems(repair),
					List.of(new ArmorMaterial.Layer(EvolvedWarfare.id(k.id + "_clothes"))), 0.0F, 0.0F);
			return Registry.registerReference(Registries.ARMOR_MATERIAL, EvolvedWarfare.id(k.id + "_clothes"), value);
		});
	}

	public Kind kind() {
		return kind;
	}

	/** Grados que abriga (piel) o refresca (lino) esta pieza. */
	public int degrees() {
		return degrees;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		if (kind == Kind.FUR) {
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.wear.warm", degrees).formatted(Formatting.AQUA));
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.wear.warm_drawback").formatted(Formatting.GRAY));
		} else {
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.wear.cool", degrees).formatted(Formatting.GOLD));
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.wear.cool_drawback").formatted(Formatting.GRAY));
		}
	}
}

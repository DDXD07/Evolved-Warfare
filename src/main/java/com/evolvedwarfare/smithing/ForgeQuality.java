package com.evolvedwarfare.smithing;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.registry.ModComponents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Lo que hace la calidad de la forja en la herramienta o el arma: durabilidad (x0,7 tosca, x1 común, x1,25 buena, x1,6
 * obra maestra), daño (-0,5 / 0 / +0,5 / +1) y rapidez al picar (-10 % / 0 / +10 % / +20 %).
 */
public final class ForgeQuality {
	private static final Identifier DAMAGE = EvolvedWarfare.id("forge_damage");
	private static final Identifier SPEED = EvolvedWarfare.id("forge_speed");

	private ForgeQuality() {
	}

	public static String key(int q) {
		return switch (q) {
			case 1 -> "crude";
			case 3 -> "fine";
			case 4 -> "masterwork";
			default -> "common";
		};
	}

	public static Formatting color(int q) {
		return switch (q) {
			case 1 -> Formatting.GRAY;
			case 3 -> Formatting.GREEN;
			case 4 -> Formatting.LIGHT_PURPLE;
			default -> Formatting.WHITE;
		};
	}

	public static float durability(int q) {
		return switch (q) {
			case 1 -> 0.7F;
			case 3 -> 1.25F;
			case 4 -> 1.6F;
			default -> 1.0F;
		};
	}

	public static double damage(int q) {
		return (q - 2) * 0.5;
	}

	public static double speed(int q) {
		return (q - 2) * 0.1;
	}

	/** Pone la calidad (sobre lo que tiene el objeto de serie: así vale también tras mejorarlo a netherita). */
	public static ItemStack apply(ItemStack stack, int q) {
		stack.set(ModComponents.FORGE_QUALITY, q);
		Integer max = stack.getItem().getComponents().get(DataComponentTypes.MAX_DAMAGE);
		if (max != null) {
			stack.set(DataComponentTypes.MAX_DAMAGE, Math.max(1, Math.round(max * durability(q))));
		}
		AttributeModifiersComponent base = stack.getItem().getComponents().getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS,
				AttributeModifiersComponent.DEFAULT);
		AttributeModifiersComponent mods = base;
		if (damage(q) != 0.0) {
			mods = mods.with(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(DAMAGE, damage(q), EntityAttributeModifier.Operation.ADD_VALUE),
					AttributeModifierSlot.MAINHAND);
		}
		if (speed(q) != 0.0) {
			mods = mods.with(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED,
					new EntityAttributeModifier(SPEED, speed(q), EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), AttributeModifierSlot.MAINHAND);
		}
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, mods);
		return stack;
	}
}

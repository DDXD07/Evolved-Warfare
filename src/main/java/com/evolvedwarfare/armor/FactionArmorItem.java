package com.evolvedwarfare.armor;

import java.util.List;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Una pieza de armadura de una facción (se dibuja en 3D con su modelo; ver FactionArmorRenderer en el cliente). */
public class FactionArmorItem extends ArmorItem {
	private final FactionArmor set;

	public FactionArmorItem(FactionArmor set, Type type, Settings settings) {
		super(set.material(), type, settings);
		this.set = set;
	}

	public FactionArmor set() {
		return set;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("armor.evolvedwarfare." + set.id()).formatted(set.faction.color()));
		tooltip.add(Text.translatable("armor.evolvedwarfare.tooltip", set.faction.displayName(), set.tier).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(set.light() ? "armor.evolvedwarfare.light" : "armor.evolvedwarfare.heavy").formatted(Formatting.DARK_GRAY));
	}
}

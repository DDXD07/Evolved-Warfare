package com.evolvedwarfare.item;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

/** Botiquín: dáselo a un recluta y entrénalo para que sea médico. */
public class MedicKitItem extends Item {
	public MedicKitItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.medic_kit.tooltip").withColor(0xAAAAAA));
		super.appendTooltip(stack, context, tooltip, type);
	}
}

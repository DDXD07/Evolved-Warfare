package com.evolvedwarfare.item;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

/**
 * Contrato de reclutamiento: clic derecho sobre un NPC libre (de una ciudad, campamento o torre)
 * para contratarlo. La contratación la gestiona el propio NPC al interactuar con él.
 */
public class RecruitmentContractItem extends Item {
	private static final int GRAY = 0xAAAAAA;

	public RecruitmentContractItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.recruitment_contract.tooltip.0").withColor(GRAY));
		tooltip.add(Text.translatable("item.evolvedwarfare.recruitment_contract.tooltip.1").withColor(GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}

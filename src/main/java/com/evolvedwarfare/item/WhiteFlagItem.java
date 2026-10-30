package com.evolvedwarfare.item;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

/**
 * Bandera blanca: mientras la llevas en una mano, los vikingos y los piratas no te atacan aunque estéis enemistados
 * (los bandidos no la respetan). Si les atacas, durante un minuto ya no te vale.
 */
public class WhiteFlagItem extends Item {
	public WhiteFlagItem(Settings settings) {
		super(settings);
	}

	/** ¿Lleva la bandera blanca en alguna mano? */
	public static boolean isRaised(PlayerEntity player) {
		return player.getMainHandStack().getItem() instanceof WhiteFlagItem || player.getOffHandStack().getItem() instanceof WhiteFlagItem;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.white_flag.tooltip").withColor(0xAAAAAA));
		super.appendTooltip(stack, context, tooltip, type);
	}
}

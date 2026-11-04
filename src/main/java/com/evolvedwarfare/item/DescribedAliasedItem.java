package com.evolvedwarfare.item;

import com.evolvedwarfare.registry.ModEffects;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AliasedBlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/** Lo que se come y también se planta (la cebolla, el ajo), con su descripción; el ajo, además, quita los parásitos. */
public class DescribedAliasedItem extends AliasedBlockItem {
	private final boolean curesParasites;

	public DescribedAliasedItem(Block block, Settings settings, boolean curesParasites) {
		super(block, settings);
		this.curesParasites = curesParasites;
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		ItemStack result = super.finishUsing(stack, world, user);
		if (curesParasites && !world.isClient) {
			user.removeStatusEffect(ModEffects.PARASITES);
		}
		return result;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY));
	}
}

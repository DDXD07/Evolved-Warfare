package com.evolvedwarfare.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

/** Tablones de puente levadizo: se colocan como un puente (o como la pared que forma al estar subido) y los mueve un torno. */
public class DrawbridgeBlock extends Block {
	public static final MapCodec<DrawbridgeBlock> CODEC = createCodec(DrawbridgeBlock::new);

	public DrawbridgeBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		super.appendTooltip(stack, context, tooltip, options);
		tooltip.add(Text.translatable("block.evolvedwarfare.drawbridge.tooltip").withColor(0xAAAAAA));
	}
}

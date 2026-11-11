package com.evolvedwarfare.train;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Una pieza de construcción (de un material): con ella en la mano se ve dónde iría en la plataforma del tren (verde o roja). */
public class PieceItem extends Item {
	public final Building.Kind kind;
	public final Building.Material material;

	public PieceItem(Building.Kind kind, Building.Material material, Settings settings) {
		super(settings);
		this.kind = kind;
		this.material = material;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.piece.desc").formatted(Formatting.GRAY));
		int hp = Math.round(material.hp * (kind == Building.Kind.RAILING ? 0.5F : 1.0F));
		tooltip.add(Text.translatable("gui.evolvedwarfare.build.hp", hp).formatted(Formatting.DARK_GREEN));
	}
}

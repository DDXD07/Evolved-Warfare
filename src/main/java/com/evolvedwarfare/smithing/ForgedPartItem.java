package com.evolvedwarfare.smithing;

import com.evolvedwarfare.registry.ModComponents;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Una pieza forjada (cabeza, hoja...): su nombre sale de la pieza y el metal; se monta en la mesa con su mango. */
public class ForgedPartItem extends Item {
	public ForgedPartItem(Settings settings) {
		super(settings);
	}

	@Override
	public Text getName(ItemStack stack) {
		PartData data = stack.get(ModComponents.FORGED_PART);
		if (data == null) {
			return super.getName(stack);
		}
		return Text.translatable("item.evolvedwarfare.forged_part.named", Text.translatable("part.evolvedwarfare." + data.part()),
				Text.translatable("material.evolvedwarfare." + data.material()));
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		PartData data = stack.get(ModComponents.FORGED_PART);
		if (data == null) {
			return;
		}
		tooltip.add(Text.translatable("tooltip.evolvedwarfare.forged.quality", Text.translatable("forge_quality.evolvedwarfare." + ForgeQuality.key(data.quality())))
				.formatted(ForgeQuality.color(data.quality())));
		ForgedPart part = ForgedPart.byId(data.part());
		if (part != null) {
			String key = switch (part.extra) {
				case GRIP -> "tooltip.evolvedwarfare.handle.grip";
				case CHAIN -> "tooltip.evolvedwarfare.handle.chain";
				default -> "tooltip.evolvedwarfare.handle.sticks";
			};
			tooltip.add(Text.translatable("tooltip.evolvedwarfare.forged_part", Text.translatable(key, part.sticks)).formatted(Formatting.GRAY));
		}
	}
}

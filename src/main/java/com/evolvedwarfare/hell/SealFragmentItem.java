package com.evolvedwarfare.hell;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Un fragmento de uno de los siete sellos (cuál, en su componente). Los siete juntos, en un círculo de ritual, hacen la Llave del Abismo. */
public class SealFragmentItem extends Item {
	public static final int SEALS = 7;

	public SealFragmentItem(Settings settings) {
		super(settings);
	}

	public static int kind(ItemStack stack) {
		Integer k = stack.get(Apocalypse.SEAL);
		return k == null ? 0 : Math.floorMod(k, SEALS);
	}

	public static ItemStack of(int kind) {
		ItemStack s = new ItemStack(Apocalypse.SEAL_FRAGMENT);
		s.set(Apocalypse.SEAL, kind);
		return s;
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.translatable("seal.evolvedwarfare." + kind(stack));
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evolvedwarfare.seal_fragment.desc").formatted(Formatting.GRAY));
	}
}

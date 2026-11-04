package com.evolvedwarfare.client;

import com.evolvedwarfare.climate.CropSeasons;
import com.evolvedwarfare.climate.Season;
import java.util.Set;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** En lo que se planta (semillas, patatas, zanahorias, cebollas...): en qué estaciones crece. */
public final class CropTooltips {
	private CropTooltips() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			Item item = stack.getItem();
			net.minecraft.block.Block block = item instanceof BlockItem blockItem ? blockItem.getBlock()
					: item == Items.MELON_SEEDS ? net.minecraft.block.Blocks.MELON_STEM
					: item == Items.PUMPKIN_SEEDS ? net.minecraft.block.Blocks.PUMPKIN_STEM : null;
			if (block == null) {
				return;
			}
			Set<Season> seasons = CropSeasons.of(block);
			if (seasons == null) {
				if (item == Items.NETHER_WART) {
					lines.add(Text.translatable("tooltip.evolvedwarfare.crop.all_seasons").formatted(Formatting.DARK_GREEN));
				}
				return;
			}
			MutableText list = Text.empty();
			boolean first = true;
			for (Season season : seasons) {
				if (!first) {
					list.append(Text.translatable("season.evolvedwarfare.list_sep"));
				}
				list.append(Text.translatable("season.evolvedwarfare." + season.name().toLowerCase(java.util.Locale.ROOT)).styled(s -> s.withColor(season.color)));
				first = false;
			}
			lines.add(Text.translatable("tooltip.evolvedwarfare.crop.seasons", list).formatted(Formatting.GRAY));
			if (seasons.size() < 4) {
				lines.add(Text.translatable("tooltip.evolvedwarfare.crop.indoors").formatted(Formatting.DARK_GRAY));
			}
		});
	}
}

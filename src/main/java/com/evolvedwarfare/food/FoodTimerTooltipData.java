package com.evolvedwarfare.food;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;

/** El contador de la comida en la descripción (fase 122): se dibuja aparte, con la letra al 80 %. */
public record FoodTimerTooltipData(ItemStack stack) implements TooltipData {
}

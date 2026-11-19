package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.food.ClientFood;
import com.evolvedwarfare.food.FoodTimerTooltipData;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.Optional;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** El contador de la comida va en un trozo propio de la descripción, para poder dibujarlo más pequeño (fase 122). */
@Mixin(ItemStack.class)
public abstract class ItemStackFoodTimerMixin {
	@ModifyReturnValue(method = "getTooltipData", at = @At("RETURN"))
	private Optional<TooltipData> evolvedwarfare$foodTimer(Optional<TooltipData> data) {
		ItemStack self = (ItemStack) (Object) this;
		if (data.isEmpty() && ClientFood.showsTimer(self)) {
			return Optional.of(new FoodTimerTooltipData(self));
		}
		return data;
	}
}

package com.evolvedwarfare.mixin;

import com.evolvedwarfare.food.FoodRot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** La barra de frescura de la comida (como la de durabilidad; de verde a rojo) (fase 98). */
@Mixin(Item.class)
public abstract class ItemRotBarMixin {
	@Inject(method = "isItemBarVisible", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$rotVisible(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (FoodRot.showBar(stack)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getItemBarStep", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$rotStep(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (FoodRot.showBar(stack)) {
			cir.setReturnValue(FoodRot.barStep(stack));
		}
	}

	@Inject(method = "getItemBarColor", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$rotColor(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (FoodRot.showBar(stack)) {
			cir.setReturnValue(FoodRot.barColor(stack));
		}
	}
}

package com.evolvedwarfare.mixin;

import com.evolvedwarfare.engineering.MechanicalCrossbowItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Las ballestas mecánicas (fase 79) tardan más en cargar. */
@Mixin(CrossbowItem.class)
public abstract class CrossbowPullTimeMixin {
	@Inject(method = "getPullTime", at = @At("RETURN"), cancellable = true)
	private static void evolvedwarfare$mechanicalCharge(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
		if (stack.getItem() instanceof MechanicalCrossbowItem m) {
			cir.setReturnValue(Math.round(cir.getReturnValue() * m.chargeFactor));
		}
	}
}

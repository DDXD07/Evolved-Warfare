package com.evolvedwarfare.mixin;

import com.evolvedwarfare.cart.CartEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dormir en la cama de una carreta: para vanilla cuenta como estar en una cama (si no, te despierta al momento). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCartSleepMixin {
	@Inject(method = "isSleepingInBed", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$cartBed(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof PlayerEntity && CartEntity.sleepingOnCart(self)) {
			cir.setReturnValue(true);
		}
	}
}

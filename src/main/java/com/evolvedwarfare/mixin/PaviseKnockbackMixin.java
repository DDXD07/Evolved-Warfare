package com.evolvedwarfare.mixin;

import com.evolvedwarfare.engineering.MetalShieldItem;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Con el pavés en alto no te empujan (fase 78). */
@Mixin(LivingEntity.class)
public abstract class PaviseKnockbackMixin {
	@Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$paviseHolds(double strength, double x, double z, CallbackInfo ci) {
		if (MetalShieldItem.blockingWith((LivingEntity) (Object) this, true)) {
			ci.cancel();
		}
	}
}

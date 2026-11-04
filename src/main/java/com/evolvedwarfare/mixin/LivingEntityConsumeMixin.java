package com.evolvedwarfare.mixin;

import com.evolvedwarfare.climate.Climate;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Al terminar de comer o beber algo, lo que hace con la temperatura del cuerpo (sopas que calientan, agua que refresca). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityConsumeMixin {
	@Inject(method = "consumeItem", at = @At("HEAD"))
	private void evolvedwarfare$climate(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.getWorld().isClient && self.isUsingItem()) {
			Climate.onConsume(self, self.getActiveItem());
		}
	}
}

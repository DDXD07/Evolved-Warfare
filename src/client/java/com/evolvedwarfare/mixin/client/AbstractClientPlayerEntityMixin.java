package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.weapon.MusketItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Un poco de zoom al apuntar con la mira del mosquete (en primera persona). */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
	@Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$musketZoom(CallbackInfoReturnable<Float> cir) {
		AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
		if (MusketItem.isAiming(self) && !MusketItem.isReloading(self.getActiveItem())
				&& MinecraftClient.getInstance().options.getPerspective().isFirstPerson()) {
			cir.setReturnValue(cir.getReturnValueF() * 0.8F);
		}
	}
}

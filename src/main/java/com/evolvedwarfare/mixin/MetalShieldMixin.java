package com.evolvedwarfare.mixin;

import com.evolvedwarfare.engineering.MetalShieldItem;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los escudos reforzados y el pavés (fase 78): se desgastan al parar golpes y las hachas no los bajan. */
@Mixin(PlayerEntity.class)
public abstract class MetalShieldMixin {
	@Inject(method = "damageShield", at = @At("HEAD"))
	private void evolvedwarfare$wearMetalShield(float amount, CallbackInfo ci) {
		MetalShieldItem.wear((PlayerEntity) (Object) this, amount);
	}

	@Inject(method = "disableShield", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$axesCantDisable(CallbackInfo ci) {
		if (((PlayerEntity) (Object) this).getActiveItem().getItem() instanceof MetalShieldItem) {
			ci.cancel();
		}
	}
}

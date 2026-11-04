package com.evolvedwarfare.mixin;

import com.evolvedwarfare.climate.Thirst;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** El cansancio que da hambre (correr, saltar, pelear, picar...) también da sed. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityThirstMixin {
	@Inject(method = "addExhaustion", at = @At("HEAD"))
	private void evolvedwarfare$thirst(float exhaustion, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.getWorld().isClient && !self.getAbilities().invulnerable) {
			Thirst.exhaust(self, exhaustion);
		}
	}
}

package com.evolvedwarfare.mixin;

import com.evolvedwarfare.health.Stamina;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Saltar gasta aguante (fase 97; en el servidor, que es donde cuenta). */
@Mixin(PlayerEntity.class)
public abstract class PlayerJumpStaminaMixin {
	@Inject(method = "jump", at = @At("TAIL"))
	private void evolvedwarfare$jumpStamina(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.getWorld().isClient) {
			Stamina.onJump(self);
		}
	}
}

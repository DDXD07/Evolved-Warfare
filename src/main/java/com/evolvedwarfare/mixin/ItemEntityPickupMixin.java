package com.evolvedwarfare.mixin;

import com.evolvedwarfare.pickup.Pickup;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lo del suelo no se recoge solo al pasar: hay que agacharse encima o darle clic derecho. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupMixin {
	@Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$handPickup(PlayerEntity player, CallbackInfo ci) {
		if (!Pickup.mayCollect(player)) {
			ci.cancel();
		}
	}
}

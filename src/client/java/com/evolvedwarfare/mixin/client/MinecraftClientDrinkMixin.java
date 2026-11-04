package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.thirst.DrinkAnimation;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Agachado, con la mano vacía, clic derecho al agua (o al cielo bajo la lluvia): se bebe con las manos. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientDrinkMixin {
	@Shadow
	private int itemUseCooldown;

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$drink(CallbackInfo ci) {
		if (DrinkAnimation.tryDrink((MinecraftClient) (Object) this)) {
			itemUseCooldown = 5;
			ci.cancel();
		}
	}
}

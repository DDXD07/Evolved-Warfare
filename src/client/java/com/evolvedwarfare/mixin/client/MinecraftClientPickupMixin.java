package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.pickup.Pickup;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clic derecho mirando algo tirado en el suelo (más cerca que el bloque o la criatura de la mira): se recoge. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientPickupMixin {
	@Shadow
	private int itemUseCooldown;

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$pickup(CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player == null || client.interactionManager == null || client.player.isSpectator()) {
			return;
		}
		double reach = Pickup.REACH;
		if (client.crosshairTarget != null && client.crosshairTarget.getType() != HitResult.Type.MISS) {
			reach = Math.min(reach, Math.sqrt(client.crosshairTarget.getPos().squaredDistanceTo(client.player.getEyePos())) + 0.3);
		}
		ItemEntity item = Pickup.aimed(client.player, reach);
		if (item == null) {
			return;
		}
		ClientPlayNetworking.send(new Pickup.PickupPayload(item.getId()));
		client.player.swingHand(Hand.MAIN_HAND);
		itemUseCooldown = 4;
		ci.cancel();
	}
}

package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.ActivityPoses;
import com.evolvedwarfare.network.ActivityPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Despellejando: agachado y quieto sobre el animal (como en Red Dead Redemption 2). */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputSkinningMixin extends Input {
	@Inject(method = "tick", at = @At("TAIL"))
	private void evolvedwarfare$kneel(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		var player = MinecraftClient.getInstance().player;
		if (player != null && ActivityPoses.activity(player) == ActivityPayload.SKINNING) {
			this.sneaking = true;
			this.jumping = false;
			this.movementForward = 0.0F;
			this.movementSideways = 0.0F;
			this.pressingForward = this.pressingBack = this.pressingLeft = this.pressingRight = false;
		}
	}
}

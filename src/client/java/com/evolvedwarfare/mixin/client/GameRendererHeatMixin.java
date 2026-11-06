package com.evolvedwarfare.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** El aire que tiembla con el calor del Nether: el campo de visión ondula un poco. */
@Mixin(GameRenderer.class)
public abstract class GameRendererHeatMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$heatHaze(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		double wobble = com.evolvedwarfare.client.hell.HellClient.heatWobble();
		if (wobble != 0.0) {
			cir.setReturnValue(cir.getReturnValue() + wobble);
		}
	}
}

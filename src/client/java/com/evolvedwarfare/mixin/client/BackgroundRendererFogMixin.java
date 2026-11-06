package com.evolvedwarfare.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La niebla de cada bioma del Nether bíblico ({@link com.evolvedwarfare.client.hell.HellClient#fog}). */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererFogMixin {
	@Inject(method = "applyFog", at = @At("TAIL"))
	private static void evolvedwarfare$hellFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta,
			CallbackInfo ci) {
		if (fogType != BackgroundRenderer.FogType.FOG_TERRAIN || camera.getSubmersionType() != CameraSubmersionType.NONE) {
			return;
		}
		float[] fog = com.evolvedwarfare.client.hell.HellClient.fog(camera, viewDistance);
		if (fog != null) {
			RenderSystem.setShaderFogStart(fog[0]);
			RenderSystem.setShaderFogEnd(fog[1]);
		}
	}
}

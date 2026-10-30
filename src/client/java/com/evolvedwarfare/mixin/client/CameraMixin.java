package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.SiegeAerialCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La cámara aérea de las máquinas de asedio (clic izquierdo mantenido): se pone encima del blanco. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract void setPos(double x, double y, double z);

	@Inject(method = "update", at = @At("TAIL"))
	private void evolvedwarfare$siegeAerial(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta,
			CallbackInfo ci) {
		SiegeAerialCamera.View view = SiegeAerialCamera.view(tickDelta);
		if (view != null) {
			setRotation(view.yaw(), view.pitch());
			setPos(view.x(), view.y(), view.z());
		}
	}
}

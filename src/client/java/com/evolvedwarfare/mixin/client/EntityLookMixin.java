package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.SiegeAerialCamera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Con la cámara aérea, el ratón mueve el punto al que apunta la máquina de asedio en vez de girar la cabeza. */
@Mixin(Entity.class)
public abstract class EntityLookMixin {
	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$siegeAerialLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		if (SiegeAerialCamera.captureLook((Entity) (Object) this, cursorDeltaX, cursorDeltaY)) {
			ci.cancel();
		}
	}
}

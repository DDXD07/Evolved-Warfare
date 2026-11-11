package com.evolvedwarfare.mixin;

import com.evolvedwarfare.train.Trains;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** De pie sobre un tren no se está "volando" (que no te echen del servidor). */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerTrainMixin {
	@Inject(method = "isEntityOnAir", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$onTrain(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Trains.onTrain(entity)) {
			cir.setReturnValue(false);
		}
	}
}

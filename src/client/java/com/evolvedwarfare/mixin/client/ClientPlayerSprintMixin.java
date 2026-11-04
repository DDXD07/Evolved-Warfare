package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.thirst.ClientThirst;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Con mucha sed no se puede correr (como con hambre). */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerSprintMixin {
	@Inject(method = "canSprint", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$thirst(CallbackInfoReturnable<Boolean> cir) {
		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		if (cir.getReturnValueZ() && ClientThirst.parched() && !self.hasVehicle() && !self.getAbilities().allowFlying) {
			cir.setReturnValue(false);
		}
	}
}

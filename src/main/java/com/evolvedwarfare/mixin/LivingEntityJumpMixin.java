package com.evolvedwarfare.mixin;

import com.evolvedwarfare.combat.Dismemberment;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Desmembramiento: un NPC sin una pierna no puede saltar. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpMixin {
	@Inject(method = "getJumpVelocity()F", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$oneLeg(CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (Dismemberment.isNpc(self) && Dismemberment.missingLegs(self) > 0) {
			cir.setReturnValue(0.0F);
		}
	}
}

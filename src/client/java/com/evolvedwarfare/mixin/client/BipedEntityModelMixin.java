package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.render.CrateCarry;
import com.evolvedwarfare.client.render.melee.MeleeArmPoses;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los brazos al golpear con un arma cuerpo a cuerpo del mod (cada arma tiene su movimiento) y al llevar una caja. */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void evolvedwarfare$meleeSwing(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
			float headYaw, float headPitch, CallbackInfo ci) {
		MeleeArmPoses.apply((BipedEntityModel<?>) (Object) this, entity);
		CrateCarry.pose((BipedEntityModel<?>) (Object) this, entity, limbAngle, limbDistance, animationProgress);
	}
}

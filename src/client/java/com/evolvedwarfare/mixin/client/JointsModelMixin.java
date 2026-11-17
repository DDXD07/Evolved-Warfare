package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.Joints;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Codos y rodillas en los cuerpos de jugador (fase 89): ver {@link Joints}. */
@Mixin(BipedEntityModel.class)
public abstract class JointsModelMixin {
	@Inject(method = "getModelData", at = @At("RETURN"))
	private static void evolvedwarfare$splitLimbs(Dilation dilation, float pivotOffsetY, CallbackInfoReturnable<ModelData> cir) {
		Joints.splitBiped(cir.getReturnValue(), dilation, pivotOffsetY);
	}

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
	private void evolvedwarfare$straighten(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		Joints.reset((BipedEntityModel<?>) (Object) this);
	}

	@Inject(method = "copyBipedStateTo", at = @At("TAIL"))
	private void evolvedwarfare$copyJoints(BipedEntityModel<?> model, CallbackInfo ci) {
		Joints.copyAll((BipedEntityModel<?>) (Object) this, model);
	}

	@Inject(method = "setArmAngle", at = @At("TAIL"))
	private void evolvedwarfare$handFollowsForearm(Arm arm, MatrixStack matrices, CallbackInfo ci) {
		BipedEntityModel<?> self = (BipedEntityModel<?>) (Object) this;
		Joints.followForearm(arm == Arm.RIGHT ? self.rightArm : self.leftArm, matrices);
	}
}

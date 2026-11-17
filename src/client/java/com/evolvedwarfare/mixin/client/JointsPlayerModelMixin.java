package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.Joints;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Codos y rodillas del jugador (y piglins): los brazos finos o anchos, las mangas y las perneras (fase 89). */
@Mixin(PlayerEntityModel.class)
public abstract class JointsPlayerModelMixin {
	@Inject(method = "getTexturedModelData", at = @At("RETURN"))
	private static void evolvedwarfare$splitPlayer(Dilation dilation, boolean slim, CallbackInfoReturnable<ModelData> cir) {
		ModelPartData root = cir.getReturnValue().getRoot();
		Dilation outer = dilation.add(0.25F);
		if (slim) {
			Joints.split(root, "left_arm", Joints.LEFT_FOREARM, 32, 48, false, -1, -2, -2, 3, 12, 4, dilation, ModelTransform.pivot(5.0F, 2.5F, 0.0F));
			Joints.split(root, "right_arm", Joints.RIGHT_FOREARM, 40, 16, false, -2, -2, -2, 3, 12, 4, dilation, ModelTransform.pivot(-5.0F, 2.5F, 0.0F));
			Joints.split(root, "left_sleeve", Joints.LEFT_FOREARM, 48, 48, false, -1, -2, -2, 3, 12, 4, outer, ModelTransform.pivot(5.0F, 2.5F, 0.0F));
			Joints.split(root, "right_sleeve", Joints.RIGHT_FOREARM, 40, 32, false, -2, -2, -2, 3, 12, 4, outer, ModelTransform.pivot(-5.0F, 2.5F, 0.0F));
		} else {
			Joints.split(root, "left_arm", Joints.LEFT_FOREARM, 32, 48, false, -1, -2, -2, 4, 12, 4, dilation, ModelTransform.pivot(5.0F, 2.0F, 0.0F));
			Joints.split(root, "left_sleeve", Joints.LEFT_FOREARM, 48, 48, false, -1, -2, -2, 4, 12, 4, outer, ModelTransform.pivot(5.0F, 2.0F, 0.0F));
			Joints.split(root, "right_sleeve", Joints.RIGHT_FOREARM, 40, 32, false, -3, -2, -2, 4, 12, 4, outer, ModelTransform.pivot(-5.0F, 2.0F, 0.0F));
		}
		Joints.split(root, "left_leg", Joints.LEFT_SHIN, 16, 48, false, -2, 0, -2, 4, 12, 4, dilation, ModelTransform.pivot(1.9F, 12.0F, 0.0F));
		Joints.split(root, "left_pants", Joints.LEFT_SHIN, 0, 48, false, -2, 0, -2, 4, 12, 4, outer, ModelTransform.pivot(1.9F, 12.0F, 0.0F));
		Joints.split(root, "right_pants", Joints.RIGHT_SHIN, 0, 32, false, -2, 0, -2, 4, 12, 4, outer, ModelTransform.pivot(-1.9F, 12.0F, 0.0F));
	}

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void evolvedwarfare$sleevesFollow(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		PlayerEntityModel<?> self = (PlayerEntityModel<?>) (Object) this;
		Joints.copy(self.leftArm, self.leftSleeve);
		Joints.copy(self.rightArm, self.rightSleeve);
		Joints.copy(self.leftLeg, self.leftPants);
		Joints.copy(self.rightLeg, self.rightPants);
	}

	@Inject(method = "setArmAngle", at = @At("TAIL"))
	private void evolvedwarfare$handFollowsForearm(Arm arm, MatrixStack matrices, CallbackInfo ci) {
		PlayerEntityModel<?> self = (PlayerEntityModel<?>) (Object) this;
		Joints.followForearm(arm == Arm.RIGHT ? self.rightArm : self.leftArm, matrices);
	}
}

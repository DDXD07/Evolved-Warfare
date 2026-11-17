package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.Joints;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.SkeletonEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Los huesos de los esqueletos, con codo y rodilla (fase 89). */
@Mixin(SkeletonEntityModel.class)
public abstract class JointsSkeletonModelMixin {
	@ModifyArg(method = "getTexturedModelData", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/TexturedModelData;of(Lnet/minecraft/client/model/ModelData;II)Lnet/minecraft/client/model/TexturedModelData;"),
			index = 0)
	private static ModelData evolvedwarfare$splitBones(ModelData data) {
		ModelPartData root = data.getRoot();
		Dilation d = Dilation.NONE;
		Joints.split(root, "right_arm", Joints.RIGHT_FOREARM, 40, 16, false, -1, -2, -1, 2, 12, 2, d, ModelTransform.pivot(-5.0F, 2.0F, 0.0F));
		Joints.split(root, "left_arm", Joints.LEFT_FOREARM, 40, 16, true, -1, -2, -1, 2, 12, 2, d, ModelTransform.pivot(5.0F, 2.0F, 0.0F));
		Joints.split(root, "right_leg", Joints.RIGHT_SHIN, 0, 16, false, -1, 0, -1, 2, 12, 2, d, ModelTransform.pivot(-2.0F, 12.0F, 0.0F));
		Joints.split(root, "left_leg", Joints.LEFT_SHIN, 0, 16, true, -1, 0, -1, 2, 12, 2, d, ModelTransform.pivot(2.0F, 12.0F, 0.0F));
		return data;
	}
}

package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.Joints;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Las grebas y las botas también se doblan por la rodilla (fase 89). */
@Mixin(ArmorEntityModel.class)
public abstract class JointsArmorModelMixin {
	@Inject(method = "getModelData", at = @At("RETURN"))
	private static void evolvedwarfare$splitArmorLegs(Dilation dilation, CallbackInfoReturnable<ModelData> cir) {
		ModelPartData root = cir.getReturnValue().getRoot();
		Dilation legs = dilation.add(-0.1F);
		Joints.split(root, "right_leg", Joints.RIGHT_SHIN, 0, 16, false, -2, 0, -2, 4, 12, 4, legs, ModelTransform.pivot(-1.9F, 12.0F, 0.0F));
		Joints.split(root, "left_leg", Joints.LEFT_SHIN, 0, 16, true, -2, 0, -2, 4, 12, 4, legs, ModelTransform.pivot(1.9F, 12.0F, 0.0F));
	}
}

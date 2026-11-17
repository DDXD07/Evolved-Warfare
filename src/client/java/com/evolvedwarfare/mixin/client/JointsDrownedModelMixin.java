package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.Joints;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.DrownedEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** El ahogado tiene el brazo y la pierna izquierdos con su propia textura: también con codo y rodilla (fase 89). */
@Mixin(DrownedEntityModel.class)
public abstract class JointsDrownedModelMixin {
	@org.spongepowered.asm.mixin.Unique
	private static Dilation evolvedwarfare$dilation = Dilation.NONE;

	@org.spongepowered.asm.mixin.injection.Inject(method = "getTexturedModelData", at = @At("HEAD"), require = 0)
	private static void evolvedwarfare$rememberDilation(Dilation dilation,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.client.model.TexturedModelData> cir) {
		evolvedwarfare$dilation = dilation;
	}

	@ModifyArg(method = "getTexturedModelData", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/TexturedModelData;of(Lnet/minecraft/client/model/ModelData;II)Lnet/minecraft/client/model/TexturedModelData;"),
			index = 0, require = 0)
	private static ModelData evolvedwarfare$splitDrowned(ModelData data) {
		ModelPartData root = data.getRoot();
		Dilation d = evolvedwarfare$dilation;
		Joints.split(root, "left_arm", Joints.LEFT_FOREARM, 32, 48, false, -1, -2, -2, 4, 12, 4, d, ModelTransform.pivot(5.0F, 2.0F, 0.0F));
		Joints.split(root, "left_leg", Joints.LEFT_SHIN, 16, 48, false, -2, 0, -2, 4, 12, 4, d, ModelTransform.pivot(1.9F, 12.0F, 0.0F));
		return data;
	}
}

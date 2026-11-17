package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.CreeperAnims;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.CreeperEntityModel;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los creepers trotan, respiran, miran alrededor y se plantan temblando antes de explotar (fase 88). */
@Mixin(CreeperEntityModel.class)
public abstract class CreeperModelMixin {
	@Shadow
	@Final
	private ModelPart root;
	@Shadow
	@Final
	private ModelPart head;
	@Shadow
	@Final
	private ModelPart rightHindLeg;
	@Shadow
	@Final
	private ModelPart leftHindLeg;
	@Shadow
	@Final
	private ModelPart rightFrontLeg;
	@Shadow
	@Final
	private ModelPart leftFrontLeg;

	@Inject(method = "setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", at = @At("TAIL"), require = 0)
	private void evolvedwarfare$creeper(Entity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch,
			CallbackInfo ci) {
		CreeperAnims.apply(root, head, rightHindLeg, leftHindLeg, rightFrontLeg, leftFrontLeg, entity, limbAngle, limbDistance, animationProgress);
	}
}

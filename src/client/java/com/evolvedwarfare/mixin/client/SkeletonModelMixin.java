package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.SkeletonAnims;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.SkeletonEntityModel;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los esqueletos se mueven rígidos, como huesos (fase 87): ver {@link SkeletonAnims}. */
@Mixin(SkeletonEntityModel.class)
public abstract class SkeletonModelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/mob/MobEntity;FFFFF)V", at = @At("TAIL"), require = 0)
	private void evolvedwarfare$bones(MobEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		SkeletonAnims.apply((BipedEntityModel<?>) (Object) this, entity, limbAngle, limbDistance, animationProgress);
	}
}

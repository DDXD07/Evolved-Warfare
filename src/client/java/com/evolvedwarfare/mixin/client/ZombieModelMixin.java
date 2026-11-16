package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.ZombieAnims;
import net.minecraft.client.render.entity.model.AbstractZombieModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.mob.HostileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los zombis, zombis del desierto y ahogados se mueven como muertos vivientes (fase 86): ver {@link ZombieAnims}. */
@Mixin(AbstractZombieModel.class)
public abstract class ZombieModelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/mob/HostileEntity;FFFFF)V", at = @At("TAIL"), require = 0)
	private void evolvedwarfare$undead(HostileEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		ZombieAnims.apply((BipedEntityModel<?>) (Object) this, entity, limbAngle, limbDistance, animationProgress);
	}
}

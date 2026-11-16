package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.ZombieAnims;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.ZombieVillagerEntityModel;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los aldeanos zombi, como los demás zombis (fase 86). */
@Mixin(ZombieVillagerEntityModel.class)
public abstract class ZombieVillagerModelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/mob/ZombieEntity;FFFFF)V", at = @At("TAIL"), require = 0)
	private void evolvedwarfare$undead(ZombieEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
			float headPitch, CallbackInfo ci) {
		ZombieAnims.apply((BipedEntityModel<?>) (Object) this, entity, limbAngle, limbDistance, animationProgress);
	}
}

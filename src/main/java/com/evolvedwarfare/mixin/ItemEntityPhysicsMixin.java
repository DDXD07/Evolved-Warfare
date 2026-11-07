package com.evolvedwarfare.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Física de lo que se tira: no flota en el agua (se hunde despacio, frenado por el agua) y al caer al suelo se para enseguida
 * en vez de deslizarse.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPhysicsMixin extends Entity {
	protected ItemEntityPhysicsMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@Inject(method = "applyWaterBuoyancy", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$sink(CallbackInfo ci) {
		Vec3d v = getVelocity();
		setVelocity(v.x * 0.8, Math.max(v.y * 0.8 - 0.012, -0.12), v.z * 0.8);
		ci.cancel();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void evolvedwarfare$settle(CallbackInfo ci) {
		if (isOnGround() && !isTouchingWater()) {
			Vec3d v = getVelocity();
			// se para en el suelo (rodar un poco al caer, pero no patinar)
			setVelocity(v.x * 0.6, v.y, v.z * 0.6);
		}
	}
}

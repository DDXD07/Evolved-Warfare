package com.evolvedwarfare.mixin;

import com.evolvedwarfare.climate.Thirst;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Con mucha sed (o sangrando, o con el torso destrozado) no se recupera vida; y si se recupera, despacio (fase 92). */
@Mixin(HungerManager.class)
public abstract class HungerManagerThirstMixin {
	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;heal(F)V"))
	private void evolvedwarfare$noRegenWhenParched(PlayerEntity player, float amount, Operation<Void> original) {
		if (!Thirst.parched(player) && com.evolvedwarfare.health.Health.canRegenerate(player)) {
			// regeneración lenta (fase 92)
			original.call(player, com.evolvedwarfare.health.Health.enabled() ? amount * com.evolvedwarfare.health.Health.regenRate() : amount);
		}
	}
}

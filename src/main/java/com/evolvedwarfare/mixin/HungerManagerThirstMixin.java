package com.evolvedwarfare.mixin;

import com.evolvedwarfare.climate.Thirst;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Con mucha sed no se recupera vida (aunque el hambre esté llena). */
@Mixin(HungerManager.class)
public abstract class HungerManagerThirstMixin {
	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;heal(F)V"))
	private void evolvedwarfare$noRegenWhenParched(PlayerEntity player, float amount, Operation<Void> original) {
		if (!Thirst.parched(player)) {
			original.call(player, amount);
		}
	}
}

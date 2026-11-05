package com.evolvedwarfare.mixin;

import com.evolvedwarfare.fishing.Fishing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * La pesca con minijuego ({@link Fishing}): al recoger la caña con un pez picando empieza el minijuego en lugar de salir
 * el pez; mientras dura, el anzuelo se queda quieto (el pez no se suelta) y volver a recoger no hace nada.
 */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberMinigameMixin {
	@Shadow
	private int hookCountdown;
	@Shadow
	private int luckBonus;
	@Shadow
	@Nullable
	private Entity hookedEntity;

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$minigame(ItemStack usedItem, CallbackInfoReturnable<Integer> cir) {
		FishingBobberEntity self = (FishingBobberEntity) (Object) this;
		if (self.getWorld().isClient || !(self.getPlayerOwner() instanceof ServerPlayerEntity player)) {
			return;
		}
		if (Fishing.frozen(self)) {
			cir.setReturnValue(0);
			return;
		}
		if (hookedEntity == null && hookCountdown > 0) {
			int result = Fishing.onReel(player, self, usedItem, luckBonus);
			if (result >= 0) {
				cir.setReturnValue(result);
			}
		}
	}

	@Inject(method = "tickFishingLogic", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$hold(BlockPos pos, CallbackInfo ci) {
		if (Fishing.frozen((FishingBobberEntity) (Object) this)) {
			ci.cancel();
		}
	}
}

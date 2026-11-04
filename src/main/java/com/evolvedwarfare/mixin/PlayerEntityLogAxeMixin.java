package com.evolvedwarfare.mixin;

import com.evolvedwarfare.wood.Woodcutting;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Los troncos solo se talan con hacha: sin ella, no avanza la rotura ({@link Woodcutting}). */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityLogAxeMixin {
	@Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$needsAxe(BlockState state, CallbackInfoReturnable<Float> cir) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (Woodcutting.needsAxe(self.isCreative(), state, self.getMainHandStack())) {
			cir.setReturnValue(0.0F);
		}
	}
}

package com.evolvedwarfare.mixin;

import com.evolvedwarfare.skill.Engrams;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Con una herramienta que no sabe usar (fase 113: sin su engrama) se pica como con la mano. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEngramMixin {
	@ModifyReturnValue(method = "getBlockBreakingSpeed", at = @At("RETURN"))
	private float evolvedwarfare$engramSpeed(float speed) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		return Engrams.heldLocked(self) ? Math.min(speed, 1.0F) : speed;
	}

	@ModifyReturnValue(method = "canHarvest", at = @At("RETURN"))
	private boolean evolvedwarfare$engramHarvest(boolean can, BlockState state) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		return can && !(state.isToolRequired() && Engrams.heldLocked(self));
	}
}

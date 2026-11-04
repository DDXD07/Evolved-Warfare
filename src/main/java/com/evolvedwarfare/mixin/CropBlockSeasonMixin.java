package com.evolvedwarfare.mixin;

import com.evolvedwarfare.climate.Climate;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** En invierno, los cultivos al raso casi no crecen. */
@Mixin(CropBlock.class)
public abstract class CropBlockSeasonMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$winter(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
		if (Climate.frozenCrop(world, pos, random)) {
			ci.cancel();
		}
	}
}

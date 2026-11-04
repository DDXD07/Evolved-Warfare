package com.evolvedwarfare.mixin;

import com.evolvedwarfare.climate.CropSeasons;
import net.minecraft.block.BlockState;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fuera de su estación y al raso, la harina de hueso no hace crecer un cultivo. */
@Mixin({CropBlock.class, StemBlock.class, SweetBerryBushBlock.class, CocoaBlock.class})
public abstract class CropFertilizeSeasonMixin {
	@Inject(method = "isFertilizable", at = @At("RETURN"), cancellable = true)
	private void evolvedwarfare$season(WorldView world, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !CropSeasons.canFertilize(world, pos, state.getBlock())) {
			cir.setReturnValue(false);
		}
	}
}

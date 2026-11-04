package com.evolvedwarfare.mixin;

import com.evolvedwarfare.climate.CropSeasons;
import net.minecraft.block.BlockState;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los cultivos solo crecen en su estación (fuera de ella, bajo techo y despacio): {@link CropSeasons}. */
@Mixin({CropBlock.class, StemBlock.class, SweetBerryBushBlock.class, CocoaBlock.class, SugarCaneBlock.class})
public abstract class CropGrowthSeasonMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$season(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
		if (!CropSeasons.canGrow(world, pos, state.getBlock(), random)) {
			ci.cancel();
		}
	}
}

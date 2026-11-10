package com.evolvedwarfare.mixin;

import com.evolvedwarfare.fluid.FiniteFluids;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Agua y lava realistas: en lugar de como fluye en vanilla (que crea líquido), se mueve sin crearse ni perderse ({@link FiniteFluids}). */
@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {
	@Inject(method = "onScheduledTick", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$finite(World world, BlockPos pos, FluidState state, CallbackInfo ci) {
		if (world.isClient || !FiniteFluids.enabled() || !FiniteFluids.handles(state.getFluid())) {
			return;
		}
		if (FiniteFluids.withinBudget(world, pos, state.getFluid())) {
			FiniteFluids.tick(world, pos, state.getFluid());
		}
		ci.cancel();
	}
}

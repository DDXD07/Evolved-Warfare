package com.evolvedwarfare.mixin;

import com.evolvedwarfare.fluid.FiniteFluids;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Agua y lava realistas: el cubo no se lleva el bloque que miras, sino 4 capas (de 25 que tiene un bloque) de la superficie de todo el cuerpo de
 * líquido (que baja un poco); si no hay bastante, no se llena. En el mar y los ríos, como siempre.
 */
@Mixin(FluidBlock.class)
public abstract class FluidBlockDrainMixin {
	@Inject(method = "tryDrainFluid", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$layers(PlayerEntity player, WorldAccess access, BlockPos pos, BlockState state, CallbackInfoReturnable<ItemStack> cir) {
		Fluid fluid = state.getFluidState().getFluid();
		if (!(access instanceof World world) || world.isClient || !FiniteFluids.enabled() || !FiniteFluids.handles(fluid)
				|| !state.getFluidState().isStill() || FiniteFluids.isInfinite(world, pos, fluid)) {
			return;
		}
		cir.setReturnValue(FiniteFluids.drain(world, pos, fluid, FiniteFluids.BUCKET) ? new ItemStack(fluid.getBucketItem()) : ItemStack.EMPTY);
	}
}

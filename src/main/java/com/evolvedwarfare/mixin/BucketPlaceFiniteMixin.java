package com.evolvedwarfare.mixin;

import com.evolvedwarfare.fluid.FiniteFluids;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Agua y lava realistas: un cubo vacía solo 4 capas (de las 25 de un bloque) en ese sitio; si ya está casi lleno, lo que
 * sobra va encima. Los bloques anegables, el Nether y el mar, como siempre.
 */
@Mixin(BucketItem.class)
public abstract class BucketPlaceFiniteMixin {
	@Shadow
	@Final
	private Fluid fluid;

	@Inject(method = "placeFluid", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$layers(@Nullable PlayerEntity player, World world, BlockPos pos, @Nullable BlockHitResult hit,
			CallbackInfoReturnable<Boolean> cir) {
		if (world.isClient || !FiniteFluids.enabled() || !FiniteFluids.handles(fluid) || FiniteFluids.isInfinite(world, pos, fluid)
				|| fluid.matchesType(Fluids.WATER) && world.getDimension().ultrawarm()) {
			return;
		}
		if (FiniteFluids.amountAt(world, pos, fluid) < 0) {
			return;
		}
		int put = FiniteFluids.add(world, pos, fluid, FiniteFluids.BUCKET);
		if (put < FiniteFluids.BUCKET) {
			FiniteFluids.add(world, pos.up(), fluid, FiniteFluids.BUCKET - put);
		}
		world.playSound(player, pos, fluid.matchesType(Fluids.LAVA) ? SoundEvents.ITEM_BUCKET_EMPTY_LAVA : SoundEvents.ITEM_BUCKET_EMPTY,
				SoundCategory.BLOCKS, 1.0F, 1.0F);
		world.emitGameEvent(player, GameEvent.FLUID_PLACE, pos);
		cir.setReturnValue(true);
	}
}

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

	/**
	 * Un cubo vacío también recoge agua (o lava) de un charco o de agua que corre (no solo de un bloque lleno), si entre
	 * todo ese cuerpo hay al menos un cubo (4 capas). Vanilla solo apunta a bloques llenos.
	 */
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$puddles(World world, PlayerEntity user, net.minecraft.util.Hand hand,
			CallbackInfoReturnable<net.minecraft.util.TypedActionResult<net.minecraft.item.ItemStack>> cir) {
		if (world.isClient || fluid != Fluids.EMPTY || !FiniteFluids.enabled()) {
			return;
		}
		net.minecraft.util.hit.HitResult hit = world.raycast(new net.minecraft.world.RaycastContext(user.getEyePos(),
				user.getEyePos().add(user.getRotationVec(1.0F).multiply(user.getBlockInteractionRange())),
				net.minecraft.world.RaycastContext.ShapeType.OUTLINE, net.minecraft.world.RaycastContext.FluidHandling.ANY, user));
		if (!(hit instanceof BlockHitResult b) || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = b.getBlockPos();
		net.minecraft.fluid.FluidState fs = world.getFluidState(pos);
		if (fs.isEmpty() || fs.isStill() || !FiniteFluids.handles(fs.getFluid()) || FiniteFluids.amountAt(world, pos, fs.getFluid()) <= 0
				|| FiniteFluids.isInfinite(world, pos, fs.getFluid()) || !world.canPlayerModifyAt(user, pos)) {
			return; // un bloque lleno, el mar...: como siempre
		}
		net.minecraft.item.ItemStack stack = user.getStackInHand(hand);
		Fluid kind = fs.getFluid().matchesType(Fluids.LAVA) ? Fluids.LAVA : Fluids.WATER;
		if (!FiniteFluids.drain(world, pos, kind, FiniteFluids.BUCKET)) {
			cir.setReturnValue(net.minecraft.util.TypedActionResult.fail(stack));
			return;
		}
		world.playSound(null, pos, kind == Fluids.LAVA ? SoundEvents.ITEM_BUCKET_FILL_LAVA : SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
		world.emitGameEvent(user, GameEvent.FLUID_PICKUP, pos);
		cir.setReturnValue(net.minecraft.util.TypedActionResult.success(
				net.minecraft.item.ItemUsage.exchangeStack(stack, user, new net.minecraft.item.ItemStack(kind.getBucketItem())), false));
	}

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

package com.evolvedwarfare.mixin;

import com.evolvedwarfare.fluid.FiniteFluids;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.GlassBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Agua realista: llenar un frasco se lleva 2 capas del agua (fuera del mar y los ríos); si no hay, no se llena. */
@Mixin(GlassBottleItem.class)
public abstract class GlassBottleFiniteMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$layers(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		if (world.isClient || !FiniteFluids.enabled()) {
			return;
		}
		HitResult hit = world.raycast(new RaycastContext(user.getEyePos(), user.getEyePos().add(user.getRotationVec(1.0F).multiply(user.getBlockInteractionRange())),
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.SOURCE_ONLY, user));
		if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK && world.getFluidState(b.getBlockPos()).isIn(FluidTags.WATER)
				&& world.canPlayerModifyAt(user, b.getBlockPos()) && !FiniteFluids.drain(world, b.getBlockPos(), Fluids.WATER, FiniteFluids.BOTTLE)) {
			cir.setReturnValue(TypedActionResult.fail(user.getStackInHand(hand)));
		}
	}
}

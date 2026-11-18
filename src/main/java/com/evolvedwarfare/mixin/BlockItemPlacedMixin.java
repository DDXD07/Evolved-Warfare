package com.evolvedwarfare.mixin;

import com.evolvedwarfare.skill.Survivor;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lo que pone un jugador se apunta: al romperlo no da experiencia de superviviente (fase 112). */
@Mixin(BlockItem.class)
public abstract class BlockItemPlacedMixin {
	@Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z", at = @At("RETURN"))
	private void evolvedwarfare$rememberPlaced(ItemPlacementContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && context.getPlayer() != null) {
			Survivor.placed(context.getWorld(), context.getBlockPos());
		}
	}
}

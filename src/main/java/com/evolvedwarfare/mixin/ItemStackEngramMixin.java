package com.evolvedwarfare.mixin;

import com.evolvedwarfare.skill.Engram;
import com.evolvedwarfare.skill.Engrams;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lo que no sabe usar (fase 113: sin su engrama) no funciona con clic derecho: ni en el aire, ni en un bloque, ni en un
 * bicho. Los bloques y la comida, sí (ver {@link Engrams#canUse}).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackEngramMixin {
	@Unique
	private boolean evolvedwarfare$blocked(PlayerEntity player) {
		ItemStack self = (ItemStack) (Object) this;
		if (player == null || Engrams.canUse(player, self)) {
			return false;
		}
		Engram engram = Engrams.missing(player, self);
		if (engram != null) {
			Engrams.warn(player, engram, "use");
		}
		return true;
	}

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$engramUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		if (evolvedwarfare$blocked(user)) {
			cir.setReturnValue(TypedActionResult.fail((ItemStack) (Object) this));
		}
	}

	@Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$engramUseOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
		if (evolvedwarfare$blocked(context.getPlayer())) {
			cir.setReturnValue(ActionResult.FAIL);
		}
	}

	@Inject(method = "useOnEntity", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$engramUseOnEntity(PlayerEntity user, LivingEntity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (evolvedwarfare$blocked(user)) {
			cir.setReturnValue(ActionResult.FAIL);
		}
	}
}

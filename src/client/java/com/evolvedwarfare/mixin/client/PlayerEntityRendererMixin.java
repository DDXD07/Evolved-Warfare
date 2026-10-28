package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.weapon.MusketItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Con el mosquete, en tercera persona: apunta con las dos manos (como la ballesta cargada) y al recargar mueve los brazos. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private static void evolvedwarfare$musketPose(AbstractClientPlayerEntity player, Hand hand,
			CallbackInfoReturnable<BipedEntityModel.ArmPose> cir) {
		ItemStack stack = player.getStackInHand(hand);
		if (!(stack.getItem() instanceof MusketItem)) {
			return;
		}
		if (player.isUsingItem() && player.getActiveHand() == hand) {
			cir.setReturnValue(BipedEntityModel.ArmPose.CROSSBOW_CHARGE);
		} else if (!player.handSwinging) {
			cir.setReturnValue(BipedEntityModel.ArmPose.CROSSBOW_HOLD);
		}
	}
}

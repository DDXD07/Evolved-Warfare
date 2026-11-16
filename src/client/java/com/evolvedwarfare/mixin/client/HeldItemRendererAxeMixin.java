package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.anim.AxeSwing;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** En primera persona, las hachas de siempre (fase 84) golpean distinto al talar y al atacar ({@link AxeSwing}). */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererAxeMixin {
	@Shadow
	protected abstract void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress);

	@Unique
	private ItemStack evolvedwarfare$axeItem = ItemStack.EMPTY;
	@Unique
	private AbstractClientPlayerEntity evolvedwarfare$axePlayer;

	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
	private void evolvedwarfare$rememberAxe(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
			ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		evolvedwarfare$axeItem = item;
		evolvedwarfare$axePlayer = player;
	}

	@Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applySwingOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"),
			require = 0)
	private void evolvedwarfare$axeSwing(HeldItemRenderer self, MatrixStack matrices, Arm arm, float swingProgress) {
		if (AxeSwing.isPlainAxe(evolvedwarfare$axeItem) && evolvedwarfare$axePlayer != null && swingProgress > 0.0F) {
			AxeSwing.firstPerson(matrices, evolvedwarfare$axePlayer, arm, swingProgress);
		} else {
			applySwingOffset(matrices, arm, swingProgress);
		}
	}
}

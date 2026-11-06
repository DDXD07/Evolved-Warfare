package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.backpack.HolsterAnimations;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tercera persona: al envainar y desenvainar, en la mano se ve lo de antes (o nada) hasta que se cambia; con la mochila, las manos ocupadas. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureHolsterMixin {
	@Unique
	private boolean evolvedwarfare$inner;

	@Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$holster(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (evolvedwarfare$inner || !(entity instanceof PlayerEntity player)) {
			return;
		}
		if (HolsterAnimations.handsBusy(player)) {
			ci.cancel();
			return;
		}
		if (arm != player.getMainArm() || !HolsterAnimations.animating(player)) {
			return;
		}
		ItemStack visible = HolsterAnimations.visibleHand(player, HolsterAnimations.delta());
		ci.cancel();
		if (!visible.isEmpty()) {
			evolvedwarfare$inner = true;
			((HeldItemFeatureInvoker) this).evolvedwarfare$renderItem(entity, visible, mode, arm, matrices, vertexConsumers, light);
			evolvedwarfare$inner = false;
		}
	}
}

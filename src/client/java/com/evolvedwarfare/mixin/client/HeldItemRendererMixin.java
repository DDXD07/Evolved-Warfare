package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.render.CrateCarry;
import com.evolvedwarfare.client.render.musket.MusketItemRenderer;
import com.evolvedwarfare.item.CropCrateItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Para que el mosquete sepa quién lo lleva al dibujarse en la mano (y así animar la recarga); y la Caja de cultivos en
 * primera persona, con las dos manos (lo de la otra mano no se dibuja).
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$carryCrate(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
			ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (com.evolvedwarfare.client.catalog.CatalogSession.active()) {
			// con el catálogo abierto, las manos están en el libro
			ci.cancel();
			return;
		}
		if (com.evolvedwarfare.client.thirst.DrinkAnimation.active(player)) {
			// bebiendo con las manos: los dos brazos, juntos
			if (hand == Hand.MAIN_HAND) {
				com.evolvedwarfare.client.thirst.DrinkAnimation.renderFirstPerson(player, matrices, vertexConsumers, light);
			}
			ci.cancel();
			return;
		}
		// los minijuegos: forjando, el martillo y el lingote; pescando, la caña en alto (se deshace al acabar)
		if (com.evolvedwarfare.client.anim.ActivityPoses.activity(player) != com.evolvedwarfare.network.ActivityPayload.NONE) {
			byte activity = com.evolvedwarfare.client.anim.ActivityPoses.activity(player);
			if (activity == com.evolvedwarfare.network.ActivityPayload.FISHING && !(item.getItem() instanceof net.minecraft.item.FishingRodItem)) {
				// solo se mueve la mano de la caña
			} else if (com.evolvedwarfare.client.anim.ActivityPoses.firstPerson((HeldItemRenderer) (Object) this, player, hand, item, matrices,
					vertexConsumers, light)) {
				ci.cancel();
				return;
			} else {
				evolvedwarfare$pushed = true;
				return;
			}
		}
		if (hand == Hand.MAIN_HAND ? CropCrateItem.isCrate(item) : CrateCarry.carrying(player)) {
			if (hand == Hand.MAIN_HAND) {
				CrateCarry.renderFirstPerson(player, tickDelta, equipProgress, swingProgress, item, matrices, vertexConsumers, light);
			}
			ci.cancel();
		}
	}

	@org.spongepowered.asm.mixin.Unique
	private boolean evolvedwarfare$pushed;

	@Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
	private void evolvedwarfare$popActivity(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
			ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (evolvedwarfare$pushed) {
			evolvedwarfare$pushed = false;
			matrices.pop();
		}
	}

	@Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"))
	private void evolvedwarfare$rememberHolder(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		MusketItemRenderer.holder = entity;
	}

	@Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("RETURN"))
	private void evolvedwarfare$forgetHolder(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		MusketItemRenderer.holder = null;
	}
}

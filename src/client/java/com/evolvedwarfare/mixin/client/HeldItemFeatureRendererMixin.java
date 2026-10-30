package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.render.CrateCarry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tercera persona: quien lleva una Caja de cultivos (y tiene cuerpo de jugador) la lleva entre las dos manos, no en una;
 * lo de la otra mano no se dibuja.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
	@Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$carryCrate(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!CrateCarry.carrying(entity)
				|| !(((FeatureRenderer<?, ?>) (Object) this).getContextModel() instanceof BipedEntityModel<?> model)) {
			return;
		}
		ci.cancel();
		if (arm == entity.getMainArm()) {
			CrateCarry.renderThirdPerson(model, entity, entity.getMainHandStack(), matrices, vertexConsumers, light);
		}
	}
}

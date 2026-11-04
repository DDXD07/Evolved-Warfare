package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.client.dismember.ClientDismember;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Desmembramiento: la armadura de lo que le han cortado a un NPC no se dibuja. */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureDismemberMixin {
	@Inject(method = "setVisible", at = @At("RETURN"))
	private void evolvedwarfare$severed(BipedEntityModel<?> bipedModel, EquipmentSlot slot, CallbackInfo ci) {
		if (((FeatureRenderer<?, ?>) (Object) this).getContextModel() instanceof BipedEntityModel<?> context) {
			ClientDismember.hideArmor(context, bipedModel);
		}
	}
}

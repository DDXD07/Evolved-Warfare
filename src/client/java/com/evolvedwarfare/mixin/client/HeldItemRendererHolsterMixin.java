package com.evolvedwarfare.mixin.client;

import com.evolvedwarfare.backpack.Backpacks;
import com.evolvedwarfare.client.backpack.HolsterAnimations;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Primera persona: al envainar, la mano se lleva lo que tenía hacia el hombro (la espalda) o hacia abajo (la cadera); al
 * desenvainar, la mano vacía va a por lo nuevo y lo trae. Y la mochila: se ve bajándola al suelo, o subiéndola al hombro.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererHolsterMixin {
	@Unique
	private boolean evolvedwarfare$inner;

	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$holster(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item,
			float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (evolvedwarfare$inner) {
			return;
		}
		HolsterAnimations.PackPose pack = HolsterAnimations.packPose(player);
		if (HolsterAnimations.handsBusy(player)) {
			ci.cancel();
			ItemStack worn = Backpacks.worn(player);
			if (hand == Hand.MAIN_HAND && pack.where() > 0 && !worn.isEmpty()) {
				float q = pack.progress();
				matrices.push();
				if (pack.where() == 1) {
					matrices.translate(0.0F, -0.35F - 0.9F * q, -0.75F + 0.1F * q);
				} else {
					matrices.translate(0.0F, MathHelper.lerp(q, -1.1F, 0.4F), MathHelper.lerp(q, -0.8F, -0.35F));
					matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-40.0F * q));
				}
				matrices.scale(1.4F, 1.4F, 1.4F);
				((HeldItemRenderer) (Object) this).renderItem(player, worn, ModelTransformationMode.FIXED, false, matrices, vertexConsumers, light);
				matrices.pop();
			}
			return;
		}
		if (hand != Hand.MAIN_HAND) {
			return;
		}
		HolsterAnimations.Frame f = HolsterAnimations.frame(player, tickDelta);
		if (f == null) {
			return;
		}
		ci.cancel();
		float w = f.weight();
		float side = player.getMainArm() == Arm.RIGHT ? 1.0F : -1.0F;
		matrices.push();
		switch (f.spot()) {
			case BACK -> {
				// hacia el hombro, por detrás de la cabeza
				matrices.translate(side * 0.15F * w, 0.55F * w, 0.35F * w);
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(75.0F * w));
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-side * 20.0F * w));
			}
			case RIGHT_HIP, LEFT_HIP -> {
				boolean same = (f.spot() == HolsterAnimations.Spot.RIGHT_HIP) == (side > 0);
				matrices.translate((same ? 0.05F : -0.45F) * side * w, -0.75F * w, 0.15F * w);
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-45.0F * w));
			}
			default -> {
			}
		}
		evolvedwarfare$inner = true;
		((HeldItemRendererInvoker) this).evolvedwarfare$renderFirstPersonItem(player, tickDelta, pitch, hand, swingProgress, f.hand(), 0.0F, matrices,
				vertexConsumers, light);
		evolvedwarfare$inner = false;
		matrices.pop();
	}
}

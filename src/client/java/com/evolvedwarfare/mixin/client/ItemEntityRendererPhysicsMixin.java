package com.evolvedwarfare.mixin.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lo tirado se ve como algo de verdad: no flota ni gira en el aire sobre el suelo. Al caer da vueltas; en el suelo queda
 * tumbado (los objetos planos) o apoyado (los bloques), cada uno con su giro, y un montón se ve como varias piezas.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererPhysicsMixin {
	@Shadow
	@Final
	private ItemRenderer itemRenderer;

	@Inject(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void evolvedwarfare$realistic(ItemEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light,
			CallbackInfo ci) {
		ItemStack stack = entity.getStack();
		if (stack.isEmpty()) {
			return;
		}
		ci.cancel();
		BakedModel model = itemRenderer.getModel(stack, entity.getWorld(), null, entity.getId());
		boolean depth = model.hasDepth();
		int seed = stack.isEmpty() ? 187 : net.minecraft.item.Item.getRawId(stack.getItem()) + stack.getDamage();
		long id = entity.getUuid().getLeastSignificantBits();
		float spin = (float) Math.floorMod(id, 360L);
		int copies = stack.getCount() > 48 ? 4 : stack.getCount() > 32 ? 3 : stack.getCount() > 16 ? 2 : 1;
		boolean resting = entity.isOnGround() || entity.isTouchingWater() && Math.abs(entity.getVelocity().y) < 0.02;
		float age = entity.getItemAge() + tickDelta;
		// desplazamientos de cada pieza del montón, fijos (sin crear un Random por objeto y fotograma)
		long h = seed * 0x9E3779B97F4A7C15L + id;
		for (int i = 0; i < copies; i++) {
			matrices.push();
			if (resting) {
				if (i > 0) {
					matrices.translate((jitter(h, i * 2) - 0.5F) * 0.18F, 0, (jitter(h, i * 2 + 1) - 0.5F) * 0.18F);
				}
				if (depth) {
					matrices.translate(0, -0.0625F + i * 0.0F, 0);
					matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin + i * 23));
					if (i > 0) {
						matrices.translate(0, 0.125F * (i % 2), 0);
					}
				} else {
					matrices.translate(0, 0.02F + i * 0.035F, 0);
					matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin + i * 37));
					matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
				}
			} else {
				// en el aire (o hundiéndose): da vueltas
				matrices.translate(0, 0.125F, 0);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin + age * 9.0F));
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.wrapDegrees(age * 23.0F + spin)));
				if (i > 0) {
					matrices.translate((jitter(h, i * 3) - 0.5F) * 0.15F, (jitter(h, i * 3 + 1) - 0.5F) * 0.15F,
							(jitter(h, i * 3 + 2) - 0.5F) * 0.15F);
				}
			}
			itemRenderer.renderItem(stack, ModelTransformationMode.GROUND, false, matrices, consumers, light, OverlayTexture.DEFAULT_UV, model);
			matrices.pop();
		}
	}

	private static float jitter(long h, int k) {
		long x = h + k * 0xBF58476D1CE4E5B9L;
		x ^= x >>> 31;
		x *= 0x94D049BB133111EBL;
		x ^= x >>> 29;
		return (x & 0xFFFFFF) / (float) 0x1000000;
	}
}

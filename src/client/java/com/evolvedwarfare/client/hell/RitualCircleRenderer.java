package com.evolvedwarfare.client.hell;

import com.evolvedwarfare.hell.RitualCircleBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Las ofrendas del círculo de ritual: flotan en corro, girando despacio sobre el dibujo. */
public class RitualCircleRenderer implements BlockEntityRenderer<RitualCircleBlockEntity> {
	public RitualCircleRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public void render(RitualCircleBlockEntity circle, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		var items = circle.items();
		long time = circle.getWorld() == null ? 0 : circle.getWorld().getTime();
		float t = (time + tickDelta) * 0.02F;
		for (int i = 0; i < items.size(); i++) {
			ItemStack s = items.get(i);
			if (s.isEmpty()) {
				continue;
			}
			float a = t + i * MathHelper.TAU / items.size();
			matrices.push();
			matrices.translate(0.5 + MathHelper.cos(a) * 0.34, 0.35 + MathHelper.sin(t * 3 + i) * 0.04, 0.5 + MathHelper.sin(a) * 0.34);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotation(-a));
			matrices.scale(0.45F, 0.45F, 0.45F);
			MinecraftClient.getInstance().getItemRenderer().renderItem(s, ModelTransformationMode.GROUND, light, overlay, matrices, consumers,
					circle.getWorld(), i);
			matrices.pop();
		}
	}
}

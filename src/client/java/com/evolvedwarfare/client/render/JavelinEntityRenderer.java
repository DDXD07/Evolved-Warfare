package com.evolvedwarfare.client.render;

import com.evolvedwarfare.registry.ModItems;
import com.evolvedwarfare.weapon.JavelinEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** La jabalina en vuelo o clavada: el dibujo del objeto, orientado hacia donde va (la punta delante). */
public class JavelinEntityRenderer extends EntityRenderer<JavelinEntity> {
	private final ItemRenderer items;
	private final ItemStack stack = new ItemStack(ModItems.JAVELIN);

	public JavelinEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.items = context.getItemRenderer();
	}

	@Override
	public Identifier getTexture(JavelinEntity entity) {
		return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
	}

	@Override
	public void render(JavelinEntity javelin, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(tickDelta, javelin.prevYaw, javelin.getYaw()) - 90.0F));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.lerp(tickDelta, javelin.prevPitch, javelin.getPitch())));
		// el dibujo va en diagonal (punta arriba a la derecha): girarlo para que la punta mire hacia delante
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-45.0F));
		matrices.scale(1.8F, 1.8F, 1.8F);
		items.renderItem(stack, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, matrices, vertices, javelin.getWorld(),
				javelin.getId());
		matrices.pop();
		super.render(javelin, yaw, tickDelta, matrices, vertices, light);
	}
}

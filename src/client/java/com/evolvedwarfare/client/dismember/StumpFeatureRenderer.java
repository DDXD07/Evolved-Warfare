package com.evolvedwarfare.client.dismember;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.combat.Dismemberment;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

/** Los muñones de lo cortado: carne y hueso donde estaba el brazo, la pierna o la cabeza. */
public class StumpFeatureRenderer<T extends MobEntity> extends FeatureRenderer<T, PlayerEntityModel<T>> {
	private static final Identifier TEXTURE = EvolvedWarfare.id("textures/entity/stump.png");
	private static ModelPart parts;

	public StumpFeatureRenderer(FeatureRendererContext<T, PlayerEntityModel<T>> context) {
		super(context);
	}

	private static ModelPart parts() {
		if (parts == null) {
			ModelData data = new ModelData();
			ModelPartData root = data.getRoot();
			root.addChild("right_arm", ModelPartBuilder.create().uv(0, 0).cuboid(-3.0F, -2.0F, -2.0F, 4, 3, 4, new Dilation(0.05F)), ModelTransform.NONE);
			root.addChild("left_arm", ModelPartBuilder.create().uv(0, 0).cuboid(-1.0F, -2.0F, -2.0F, 4, 3, 4, new Dilation(0.05F)), ModelTransform.NONE);
			root.addChild("leg", ModelPartBuilder.create().uv(0, 0).cuboid(-2.0F, 0.0F, -2.0F, 4, 2, 4, new Dilation(0.05F)), ModelTransform.NONE);
			root.addChild("neck", ModelPartBuilder.create().uv(0, 8).cuboid(-2.0F, -1.0F, -2.0F, 4, 1, 4), ModelTransform.NONE);
			parts = TexturedModelData.of(data, 32, 16).createModel();
		}
		return parts;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T entity, float limbAngle, float limbDistance,
			float tickDelta, float animationProgress, float headYaw, float headPitch) {
		int mask = ClientDismember.mask(entity);
		if (mask == 0) {
			return;
		}
		PlayerEntityModel<T> model = getContextModel();
		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		ModelPart root = parts();
		if ((mask & Dismemberment.RIGHT_ARM) != 0) {
			draw(root.getChild("right_arm"), model.rightArm, matrices, vc, light);
		}
		if ((mask & Dismemberment.LEFT_ARM) != 0) {
			draw(root.getChild("left_arm"), model.leftArm, matrices, vc, light);
		}
		if ((mask & Dismemberment.RIGHT_LEG) != 0) {
			draw(root.getChild("leg"), model.rightLeg, matrices, vc, light);
		}
		if ((mask & Dismemberment.LEFT_LEG) != 0) {
			draw(root.getChild("leg"), model.leftLeg, matrices, vc, light);
		}
		if ((mask & Dismemberment.HEAD) != 0) {
			draw(root.getChild("neck"), model.head, matrices, vc, light);
		}
	}

	private static void draw(ModelPart stump, ModelPart follow, MatrixStack matrices, VertexConsumer vc, int light) {
		stump.copyTransform(follow);
		stump.render(matrices, vc, light, OverlayTexture.DEFAULT_UV);
	}
}

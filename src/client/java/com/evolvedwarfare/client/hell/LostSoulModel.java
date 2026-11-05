package com.evolvedwarfare.client.hell;

import com.evolvedwarfare.hell.LostSoulEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/** El alma en pena: cabeza con la boca abierta, un torso que se deshace en jirones y los brazos extendidos. Medio transparente. */
public class LostSoulModel extends EntityModel<LostSoulEntity> {
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart tail1;
	private final ModelPart tail2;
	private final ModelPart rightArm;
	private final ModelPart leftArm;

	public LostSoulModel(ModelPart root) {
		super(net.minecraft.client.render.RenderLayer::getEntityTranslucent);
		this.root = root;
		ModelPart body = root.getChild("body");
		this.head = body.getChild("head");
		this.tail1 = body.getChild("tail1");
		this.tail2 = tail1.getChild("tail2");
		this.rightArm = body.getChild("right_arm");
		this.leftArm = body.getChild("left_arm");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData body = root.addChild("body", ModelPartBuilder.create().uv(0, 16).cuboid(-4.0F, 0.0F, -2.0F, 8, 10, 4),
				ModelTransform.pivot(0.0F, 2.0F, 0.0F));
		body.addChild("head", ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8, 8, 8), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
		ModelPartData tail1 = body.addChild("tail1", ModelPartBuilder.create().uv(0, 32).cuboid(-3.0F, 0.0F, -1.5F, 6, 6, 3),
				ModelTransform.pivot(0.0F, 10.0F, 0.0F));
		tail1.addChild("tail2", ModelPartBuilder.create().uv(0, 44).cuboid(-1.5F, 0.0F, -1.0F, 3, 6, 2), ModelTransform.pivot(0.0F, 6.0F, 0.0F));
		body.addChild("right_arm", ModelPartBuilder.create().uv(32, 16).cuboid(-1.5F, -1.0F, -1.5F, 3, 11, 3), ModelTransform.pivot(-5.5F, 1.0F, 0.0F));
		body.addChild("left_arm", ModelPartBuilder.create().uv(48, 16).cuboid(-1.5F, -1.0F, -1.5F, 3, 11, 3), ModelTransform.pivot(5.5F, 1.0F, 0.0F));
		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void setAngles(LostSoulEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		float t = animationProgress;
		root.getChild("body").pivotY = 2.0F + MathHelper.sin(t * 0.08F) * 1.5F;
		head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
		head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE * 0.5F;
		float reach = entity.isAttacking() ? -1.5F : -1.1F;
		rightArm.pitch = reach + MathHelper.sin(t * 0.11F) * 0.12F;
		leftArm.pitch = reach + MathHelper.sin(t * 0.11F + 1.7F) * 0.12F;
		rightArm.roll = 0.1F;
		leftArm.roll = -0.1F;
		tail1.pitch = 0.25F + MathHelper.sin(t * 0.1F) * 0.15F;
		tail1.yaw = MathHelper.sin(t * 0.07F) * 0.2F;
		tail2.pitch = 0.3F + MathHelper.sin(t * 0.1F + 1.0F) * 0.25F;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		// medio transparente, y cambia un poco
		int alpha = (int) (((color >>> 24) & 255) * 0.6F);
		root.render(matrices, vertices, light, overlay, alpha << 24 | (color & 0xFFFFFF));
	}
}

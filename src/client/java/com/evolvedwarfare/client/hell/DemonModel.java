package com.evolvedwarfare.client.hell;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.MathHelper;

/** Demonio (y diablillo): cuerpo de persona con cuernos curvos, rabo y alas de murciélago que se mueven. */
public class DemonModel<T extends HostileEntity> extends BipedEntityModel<T> {
	private final ModelPart leftWing;
	private final ModelPart rightWing;
	private final ModelPart tail;

	public DemonModel(ModelPart root) {
		super(root);
		this.leftWing = body.getChild("left_wing");
		this.rightWing = body.getChild("right_wing");
		this.tail = body.getChild("tail");
	}

	public static TexturedModelData getTexturedModelData(boolean imp) {
		ModelData data = BipedEntityModel.getModelData(Dilation.NONE, 0.0F);
		ModelPartData root = data.getRoot();
		ModelPartData head = root.getChild("head");
		for (int side : new int[] {-1, 1}) {
			String name = side < 0 ? "right" : "left";
			ModelPartData horn = head.addChild(name + "_horn", ModelPartBuilder.create().uv(0, 32).mirrored(side > 0).cuboid(-0.5F, -4.0F, -0.5F, 1, 4, 1),
					ModelTransform.of(side * 3.0F, -7.5F, -1.0F, -0.35F, 0.0F, side * 0.45F));
			horn.addChild("tip", ModelPartBuilder.create().uv(0, 40).cuboid(-0.5F, -2.0F, -0.5F, 1, 2, 1),
					ModelTransform.of(0.0F, -3.6F, 0.0F, -0.6F, 0.0F, -side * 0.3F));
		}
		ModelPartData body = root.getChild("body");
		body.addChild("tail", ModelPartBuilder.create().uv(24, 32).cuboid(-0.5F, -0.5F, 0.0F, 1, 1, 8),
				ModelTransform.of(0.0F, 10.5F, 2.0F, -0.7F, 0.0F, 0.0F));
		int w = imp ? 9 : 12;
		int h = imp ? 7 : 10;
		body.addChild("left_wing", ModelPartBuilder.create().uv(0, 48).mirrored().cuboid(0.0F, 0.0F, 0.0F, w, h, 0),
				ModelTransform.of(1.0F, 1.5F, 2.0F, 0.0F, -0.4F, 0.0F));
		body.addChild("right_wing", ModelPartBuilder.create().uv(0, 48).cuboid(-w, 0.0F, 0.0F, w, h, 0),
				ModelTransform.of(-1.0F, 1.5F, 2.0F, 0.0F, 0.4F, 0.0F));
		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
		float flap = MathHelper.sin(animationProgress * 0.12F) * 0.25F + (entity.isAttacking() ? 0.35F : 0.0F);
		leftWing.yaw = -0.45F - flap;
		rightWing.yaw = 0.45F + flap;
		tail.yaw = MathHelper.sin(animationProgress * 0.08F) * 0.35F;
		tail.pitch = -0.7F + MathHelper.sin(animationProgress * 0.05F) * 0.1F;
		if (entity.isAttacking()) {
			// los brazos, garras por delante
			rightArm.pitch = -1.3F + MathHelper.sin(animationProgress * 0.3F) * 0.15F;
			leftArm.pitch = -1.3F - MathHelper.sin(animationProgress * 0.3F) * 0.15F;
		}
	}
}

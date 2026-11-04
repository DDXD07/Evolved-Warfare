package com.evolvedwarfare.client.dismember;

import com.evolvedwarfare.client.render.ModModelLayers;
import com.evolvedwarfare.combat.Dismemberment;
import com.evolvedwarfare.combat.SeveredLimbEntity;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Un brazo, una pierna o una cabeza cortados, con el aspecto del NPC de quien eran: gira al volar y queda tumbado en el suelo. */
public class SeveredLimbRenderer extends EntityRenderer<SeveredLimbEntity> {
	private final PlayerEntityModel<LivingEntity> model;
	/** El aspecto de cada trozo (se busca una vez, mientras el NPC aún existe). */
	private static final WeakHashMap<SeveredLimbEntity, Identifier> TEXTURES = new WeakHashMap<>();

	public SeveredLimbRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.model = new PlayerEntityModel<>(ctx.getPart(ModModelLayers.NPC), false);
		this.shadowRadius = 0.15F;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Identifier getTexture(SeveredLimbEntity limb) {
		Identifier cached = TEXTURES.get(limb);
		if (cached != null) {
			return cached;
		}
		Identifier texture = DefaultSkinHelper.getTexture();
		Entity owner = limb.getWorld().getEntityById(limb.ownerId());
		if (owner != null) {
			EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(owner);
			texture = renderer.getTexture(owner);
		}
		TEXTURES.put(limb, texture);
		return texture;
	}

	@Override
	public void render(SeveredLimbEntity limb, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		int part = limb.part();
		ModelPart[] pieces;
		float cx;
		float cy;
		switch (part) {
			case Dismemberment.HEAD -> {
				pieces = new ModelPart[] {model.head, model.hat};
				cx = 0.0F;
				cy = -4.0F;
			}
			case Dismemberment.LEFT_ARM -> {
				pieces = new ModelPart[] {model.leftArm, model.leftSleeve};
				cx = 1.0F;
				cy = 4.0F;
			}
			case Dismemberment.RIGHT_LEG -> {
				pieces = new ModelPart[] {model.rightLeg, model.rightPants};
				cx = 0.0F;
				cy = 6.0F;
			}
			case Dismemberment.LEFT_LEG -> {
				pieces = new ModelPart[] {model.leftLeg, model.leftPants};
				cx = 0.0F;
				cy = 6.0F;
			}
			default -> {
				pieces = new ModelPart[] {model.rightArm, model.rightSleeve};
				cx = -1.0F;
				cy = 4.0F;
			}
		}
		matrices.push();
		boolean head = part == Dismemberment.HEAD;
		matrices.translate(0.0F, head ? 0.25F : 0.13F, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(limb.restYaw));
		if (limb.isOnGround()) {
			// tumbado (la cabeza, de lado)
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(head ? 0.0F : 90.0F));
			if (head) {
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(80.0F));
			}
		} else {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(tickDelta, limb.prevSpin, limb.spin)));
		}
		matrices.scale(-1.0F, -1.0F, 1.0F);
		matrices.translate(-cx / 16.0F, -cy / 16.0F, 0.0F);
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(getTexture(limb)));
		for (ModelPart p : pieces) {
			p.resetTransform();
			p.setPivot(0.0F, 0.0F, 0.0F);
			p.setAngles(0.0F, 0.0F, 0.0F);
			p.visible = true;
			p.render(matrices, vc, light, OverlayTexture.DEFAULT_UV);
		}
		matrices.pop();
		super.render(limb, yaw, tickDelta, matrices, consumers, light);
	}
}

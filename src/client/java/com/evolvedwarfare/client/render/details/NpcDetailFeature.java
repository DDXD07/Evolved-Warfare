package com.evolvedwarfare.client.render.details;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;

/** Dibuja las piezas en 3D del aspecto de cada NPC del mod (fase 111). */
public class NpcDetailFeature<T extends MobEntity> extends FeatureRenderer<T, PlayerEntityModel<T>> {
	private final FeatureRendererContext<T, PlayerEntityModel<T>> context;

	public NpcDetailFeature(FeatureRendererContext<T, PlayerEntityModel<T>> context) {
		super(context);
		this.context = context;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, T entity, float limbAngle, float limbDistance, float tickDelta,
			float animationProgress, float headYaw, float headPitch) {
		if (entity.isInvisible()) {
			return;
		}
		NpcDetails details = NpcDetails.forSkin(context.getTexture(entity));
		if (details != null) {
			details.render(getContextModel(), entity, matrices, consumers, light, LivingEntityRenderer.getOverlay(entity, 0.0F));
		}
	}
}

package com.evolvedwarfare.client.engineering;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.engineering.HoistEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** La plataforma del elevador y el gancho de la grúa, con su cuerda hasta arriba y (el gancho) el bloque que lleve. */
public class HoistRenderer extends EntityRenderer<HoistEntity> {
	private final ModelPart platform, hook, rope;

	public HoistRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		platform = ctx.getPart(KineticRenderer.layer("hoist_platform"));
		hook = ctx.getPart(KineticRenderer.layer("hoist_hook"));
		rope = ctx.getPart(KineticRenderer.layer("hoist_rope"));
		shadowRadius = 0.0F;
	}

	private static Identifier tex(String id) {
		return EvolvedWarfare.id("textures/entity/engineering/" + id + ".png");
	}

	@Override
	public void render(HoistEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider consumers, int light) {
		boolean isHook = e.isHook();
		float y = (float) MathHelper.lerp(tickDelta, e.prevY, e.getY());
		float start = isHook ? 0.5F : 0.375F;
		float length = e.ropeTop() - y - start;
		// la cuerda
		if (length > 0.01F) {
			m.push();
			m.translate(0.0, start, 0.0);
			m.scale(1.0F, -length, 1.0F);
			rope.render(m, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(tex("hoist_rope"))), light, OverlayTexture.DEFAULT_UV);
			m.pop();
		}
		m.push();
		if (isHook) {
			m.translate(0.0, 0.25, 0.0);
		}
		m.scale(1.0F, -1.0F, 1.0F);
		(isHook ? hook : platform).render(m, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(tex(isHook ? "hoist_hook" : "hoist_platform"))),
				light, OverlayTexture.DEFAULT_UV);
		m.pop();
		e.carried().ifPresent(state -> {
			m.push();
			m.translate(-0.5, -1.0, -0.5);
			MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, m, consumers, light, OverlayTexture.DEFAULT_UV);
			m.pop();
		});
		super.render(e, yaw, tickDelta, m, consumers, light);
	}

	@Override
	public Identifier getTexture(HoistEntity e) {
		return tex(e.isHook() ? "hoist_hook" : "hoist_platform");
	}
}

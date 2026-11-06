package com.evolvedwarfare.client.hell;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.Identifier;

/** Un mini jefe: su modelo, su piel y lo que le brilla (ojos, el horno de Moloc). */
public class BossRenderer<T extends HostileEntity> extends MobEntityRenderer<T, BossModel<T>> {
	private final Identifier texture;
	private final RenderLayer eyes;
	private float scale = 1.0F;
	/** Una variante (otro modelo y otra piel) para las que la piden. */
	private java.util.function.Predicate<T> variant = e -> false;
	private BossModel<T> variantModel;
	private BossModel<T> baseModel;
	private Identifier variantTexture;
	private RenderLayer variantEyes;

	public BossRenderer(EntityRendererFactory.Context ctx, String id, EntityModelLayer layer, float shadow) {
		this(ctx, id, layer, shadow, false);
	}

	public BossRenderer(EntityRendererFactory.Context ctx, String id, EntityModelLayer layer, float shadow, boolean translucent) {
		super(ctx, new BossModel<>(ctx.getPart(layer), id, translucent), shadow);
		this.baseModel = this.model;
		this.texture = EvolvedWarfare.id("textures/entity/hell/" + id + ".png");
		this.eyes = RenderLayer.getEyes(EvolvedWarfare.id("textures/entity/hell/" + id + "_eyes.png"));
		addFeature(new EyesFeatureRenderer<>(this) {
			@Override
			public RenderLayer getEyesTexture() {
				return current;
			}
		});
	}

	private RenderLayer current;

	public BossRenderer<T> scaled(float scale) {
		this.scale = scale;
		return this;
	}

	public BossRenderer<T> withVariant(java.util.function.Predicate<T> when, BossModel<T> model, String id) {
		this.variant = when;
		this.variantModel = model;
		this.variantTexture = EvolvedWarfare.id("textures/entity/hell/" + id + ".png");
		this.variantEyes = RenderLayer.getEyes(EvolvedWarfare.id("textures/entity/hell/" + id + "_eyes.png"));
		return this;
	}

	@Override
	public void render(T entity, float yaw, float tickDelta, net.minecraft.client.util.math.MatrixStack matrices,
			net.minecraft.client.render.VertexConsumerProvider consumers, int light) {
		boolean v = variantModel != null && variant.test(entity);
		this.model = v ? variantModel : baseModel;
		this.current = v ? variantEyes : eyes;
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	@Override
	protected void scale(T entity, net.minecraft.client.util.math.MatrixStack matrices, float amount) {
		if (scale != 1.0F) {
			matrices.scale(scale, scale, scale);
		}
	}

	@Override
	public Identifier getTexture(T entity) {
		return variantModel != null && variant.test(entity) ? variantTexture : texture;
	}
}

package com.evolvedwarfare.client.hell;

import com.evolvedwarfare.EvolvedWarfare;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.Identifier;

/** El demonio o el diablillo: su modelo (más grande o más pequeño), su piel y unos ojos que brillan en la oscuridad. */
public class DemonRenderer<T extends HostileEntity> extends MobEntityRenderer<T, DemonModel<T>> {
	public static final EntityModelLayer DEMON = new EntityModelLayer(EvolvedWarfare.id("demon"), "main");
	public static final EntityModelLayer IMP = new EntityModelLayer(EvolvedWarfare.id("imp"), "main");
	private final Identifier texture;
	private final float scale;

	public DemonRenderer(EntityRendererFactory.Context ctx, String name, EntityModelLayer layer, float scale) {
		super(ctx, new DemonModel<>(ctx.getPart(layer)), 0.5F * scale);
		this.texture = EvolvedWarfare.id("textures/entity/hell/" + name + ".png");
		this.scale = scale;
		RenderLayer eyes = RenderLayer.getEyes(EvolvedWarfare.id("textures/entity/hell/" + name + "_eyes.png"));
		addFeature(new EyesFeatureRenderer<>(this) {
			@Override
			public RenderLayer getEyesTexture() {
				return eyes;
			}
		});
	}

	@Override
	protected void scale(T entity, MatrixStack matrices, float amount) {
		matrices.scale(scale, scale, scale);
	}

	@Override
	public Identifier getTexture(T entity) {
		return texture;
	}
}

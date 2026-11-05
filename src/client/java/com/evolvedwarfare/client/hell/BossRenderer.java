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

	public BossRenderer(EntityRendererFactory.Context ctx, String id, EntityModelLayer layer, float shadow) {
		super(ctx, new BossModel<>(ctx.getPart(layer), id), shadow);
		this.texture = EvolvedWarfare.id("textures/entity/hell/" + id + ".png");
		RenderLayer eyes = RenderLayer.getEyes(EvolvedWarfare.id("textures/entity/hell/" + id + "_eyes.png"));
		addFeature(new EyesFeatureRenderer<>(this) {
			@Override
			public RenderLayer getEyesTexture() {
				return eyes;
			}
		});
	}

	@Override
	public Identifier getTexture(T entity) {
		return texture;
	}
}

package com.evolvedwarfare.client.wildlife;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.wildlife.WildAnimalEntity;
import com.evolvedwarfare.wildlife.WildSpecies;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Dibuja un animal salvaje con su modelo y su textura (las crías, más pequeñas). */
public class WildAnimalRenderer extends MobEntityRenderer<WildAnimalEntity, WildAnimalModel> {
	private final Identifier texture;

	public WildAnimalRenderer(EntityRendererFactory.Context ctx, WildSpecies species, EntityModelLayer layer) {
		super(ctx, new WildAnimalModel(ctx.getPart(layer), species), species.width * 0.6F);
		this.texture = EvolvedWarfare.id("textures/entity/wildlife/" + species.id() + ".png");
	}

	@Override
	public Identifier getTexture(WildAnimalEntity entity) {
		return texture;
	}

	@Override
	protected void scale(WildAnimalEntity entity, MatrixStack matrices, float amount) {
		float s = entity.renderScale();
		matrices.scale(s, s, s);
		shadowRadius = entity.species().width * 0.6F * s;
	}
}

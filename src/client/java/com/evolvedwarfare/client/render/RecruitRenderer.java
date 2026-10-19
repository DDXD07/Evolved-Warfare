package com.evolvedwarfare.client.render;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.RecruitEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Dibuja al recluta con forma de jugador, su skin, la armadura puesta y lo que lleva en las manos. */
public class RecruitRenderer extends BipedEntityRenderer<RecruitEntity, PlayerEntityModel<RecruitEntity>> {
	private static final Identifier[] SKINS = new Identifier[RecruitEntity.VARIANTS];

	static {
		for (int i = 0; i < SKINS.length; i++) {
			SKINS[i] = EvolvedWarfare.id("textures/entity/recruit/recruit_" + i + ".png");
		}
	}

	public RecruitRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(ModModelLayers.NPC), false), 0.5F);
		addFeature(new ArmorFeatureRenderer<>(this,
				new ArmorEntityModel<>(context.getPart(ModModelLayers.NPC_INNER_ARMOR)),
				new ArmorEntityModel<>(context.getPart(ModModelLayers.NPC_OUTER_ARMOR)),
				context.getModelManager()));
	}

	@Override
	public Identifier getTexture(RecruitEntity entity) {
		return SKINS[Math.floorMod(entity.getVariant(), SKINS.length)];
	}
}

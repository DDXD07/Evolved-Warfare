package com.evolvedwarfare.client.render;

import com.evolvedwarfare.entity.TraderEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/** Comerciantes de las ciudades: túnicas de colores ricos con ribetes, turbantes, boinas, fajas y la bolsa del dinero. */
public class TraderRenderer extends NpcBipedRenderer<TraderEntity> {
	private static final Identifier[] SKINS = RecruitRenderer.skins("trader", 10);

	public TraderRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(TraderEntity entity) {
		return SKINS[RecruitRenderer.index(entity, entity.getVariant(), SKINS.length)];
	}
}

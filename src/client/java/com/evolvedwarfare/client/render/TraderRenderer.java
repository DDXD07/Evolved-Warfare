package com.evolvedwarfare.client.render;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.NpcNames;
import com.evolvedwarfare.entity.TraderEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/** Comerciantes de las ciudades: gente corriente (las caras de los reclutas) con el delantal de su oficio. */
public class TraderRenderer extends NpcBipedRenderer<TraderEntity> {
	private static final Identifier[] SKINS = new Identifier[NpcNames.VARIANTS];

	static {
		for (int i = 0; i < SKINS.length; i++) {
			SKINS[i] = EvolvedWarfare.id("textures/entity/recruit/recruit_" + i + ".png");
		}
	}

	public TraderRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(TraderEntity entity) {
		return SKINS[Math.floorMod(entity.getVariant(), SKINS.length)];
	}
}

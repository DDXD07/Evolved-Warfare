package com.evolvedwarfare.client.render;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.BanditEntity;
import com.evolvedwarfare.entity.NpcRole;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/** Bandidos: 4 aspectos (capucha, pañuelo, cicatriz...) y uno propio para el capitán. */
public class BanditRenderer extends NpcBipedRenderer<BanditEntity> {
	private static final Identifier[] SKINS = new Identifier[BanditEntity.VARIANTS];
	private static final Identifier CAPTAIN = EvolvedWarfare.id("textures/entity/bandit/captain.png");

	static {
		for (int i = 0; i < SKINS.length; i++) {
			SKINS[i] = EvolvedWarfare.id("textures/entity/bandit/bandit_" + i + ".png");
		}
	}

	public BanditRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(BanditEntity entity) {
		if (entity.getRole() == NpcRole.CAPTAIN) {
			return CAPTAIN;
		}
		return SKINS[Math.floorMod(entity.getVariant(), SKINS.length)];
	}
}

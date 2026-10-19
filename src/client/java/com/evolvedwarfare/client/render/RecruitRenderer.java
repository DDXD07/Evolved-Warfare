package com.evolvedwarfare.client.render;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import com.evolvedwarfare.entity.NpcNames;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/**
 * Reclutas y soldados del jugador: la misma persona con su skin (6 aspectos); lo que cambia según su
 * clase es el equipo que lleva.
 */
public class RecruitRenderer<T extends AbstractNpcEntity> extends NpcBipedRenderer<T> {
	private static final Identifier[] SKINS = new Identifier[NpcNames.VARIANTS];

	static {
		for (int i = 0; i < SKINS.length; i++) {
			SKINS[i] = EvolvedWarfare.id("textures/entity/recruit/recruit_" + i + ".png");
		}
	}

	public RecruitRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(T entity) {
		return SKINS[Math.floorMod(entity.getVariant(), SKINS.length)];
	}
}

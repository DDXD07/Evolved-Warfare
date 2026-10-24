package com.evolvedwarfare.client.render;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.FactionNpcEntity;
import com.evolvedwarfare.entity.NpcRole;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/**
 * Guerreros de las facciones: varios aspectos por facción y uno propio para el jefe (capitán, jarl...).
 * Texturas en {@code textures/entity/<carpeta>/<carpeta>_N.png} y {@code captain.png}.
 */
public class FactionNpcRenderer<T extends FactionNpcEntity> extends NpcBipedRenderer<T> {
	private final Identifier[] skins;
	private final Identifier captain;

	public FactionNpcRenderer(EntityRendererFactory.Context context, String folder, int variants) {
		super(context);
		this.skins = new Identifier[variants];
		for (int i = 0; i < variants; i++) {
			skins[i] = EvolvedWarfare.id("textures/entity/" + folder + "/" + folder + "_" + i + ".png");
		}
		this.captain = EvolvedWarfare.id("textures/entity/" + folder + "/captain.png");
	}

	@Override
	public Identifier getTexture(T entity) {
		if (entity.getRole() == NpcRole.CAPTAIN) {
			return captain;
		}
		return skins[Math.floorMod(entity.getVariant(), skins.length)];
	}
}

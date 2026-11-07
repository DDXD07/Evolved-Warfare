package com.evolvedwarfare.client.render;

import com.evolvedwarfare.EvolvedWarfare;
import com.evolvedwarfare.entity.AbstractNpcEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

/**
 * Las personas del jugador y de las ciudades (reclutas, soldados, guardias, trabajadores): cada oficio con sus aspectos
 * ({@code textures/entity/<carpeta>/<carpeta>_N.png}); a cada uno le toca siempre el mismo (por su variante y su UUID).
 */
public class RecruitRenderer<T extends AbstractNpcEntity> extends NpcBipedRenderer<T> {
	private final Identifier[] skins;

	public RecruitRenderer(EntityRendererFactory.Context context, String folder, int count) {
		super(context);
		this.skins = skins(folder, count);
	}

	public static Identifier[] skins(String folder, int count) {
		Identifier[] out = new Identifier[count];
		for (int i = 0; i < count; i++) {
			out[i] = EvolvedWarfare.id("textures/entity/" + folder + "/" + folder + "_" + i + ".png");
		}
		return out;
	}

	/** El aspecto que le toca a alguien: el mismo siempre, repartido entre todos (no solo entre las pocas variantes guardadas). */
	public static int index(Entity entity, int variant, int count) {
		long h = entity.getUuid().getLeastSignificantBits() * 31 + variant;
		h ^= h >>> 29;
		return (int) Math.floorMod(h, (long) count);
	}

	@Override
	public Identifier getTexture(T entity) {
		return skins[index(entity, entity.getVariant(), skins.length)];
	}
}

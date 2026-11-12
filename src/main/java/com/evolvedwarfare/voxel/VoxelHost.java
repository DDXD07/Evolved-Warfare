package com.evolvedwarfare.voxel;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Una entidad que lleva una rejilla de vóxeles encima (la carreta; luego, el tren). */
public interface VoxelHost {
	VoxelGrid grid();

	Entity self();

	/** De coordenadas de la entidad a coordenadas del mundo, y al revés. */
	Vec3d gridToWorld(Vec3d local);

	Vec3d worldToGrid(Vec3d world);

	/** Lo macizo que no es de la rejilla (la tabla), en coordenadas de la entidad: sobre su cara de arriba se construye. */
	List<Box> baseBoxes();

	/** Algo ha cambiado (para guardarlo y que lo vean los clientes). */
	void gridChanged();

	/** Dormir en la cama de esa celda (la cabecera). */
	default void sleepOn(int head, PlayerEntity player) {
	}
}

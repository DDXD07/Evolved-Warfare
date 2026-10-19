package com.evolvedwarfare.work;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Zona de trabajo de un puesto.
 *
 * <ul>
 *   <li><b>Alrededor</b>: un cuadrado de lado {@code 2 * radius + 1} centrado en el puesto, de {@code down} bloques
 *       por debajo a {@code up} por encima.</li>
 *   <li><b>Pozo</b> (mina, excavación): un cuadrado de lado {@code 2 * radius + 1} <i>detrás</i> del puesto, separado
 *       por un pasillo de un bloque, que se excava hacia abajo capa a capa.</li>
 *   <li><b>Ninguna</b>: los talleres trabajan en el propio puesto.</li>
 * </ul>
 */
public record WorkArea(int radius, boolean pit, int down, int up) {
	public static final WorkArea NONE = new WorkArea(0, false, 0, 0);

	public static WorkArea around(int radius, int down, int up) {
		return new WorkArea(radius, false, down, up);
	}

	public static WorkArea pit(int radius) {
		return new WorkArea(radius, true, 1, -1);
	}

	public boolean exists() {
		return radius > 0;
	}

	/** Centro de la zona: el propio puesto o, para los pozos, detrás de él. */
	public BlockPos center(BlockPos site, Direction facing) {
		return pit ? site.offset(facing.getOpposite(), radius + 2) : site;
	}

	public BlockBox box(BlockPos site, Direction facing) {
		BlockPos c = center(site, facing);
		return new BlockBox(c.getX() - radius, site.getY() - down, c.getZ() - radius,
				c.getX() + radius, site.getY() + up, c.getZ() + radius);
	}

	/** Lado del cuadrado (para los textos: "zona 11×11"). */
	public int size() {
		return radius * 2 + 1;
	}
}

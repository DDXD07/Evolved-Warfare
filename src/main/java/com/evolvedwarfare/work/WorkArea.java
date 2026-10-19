package com.evolvedwarfare.work;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Zona de trabajo de un puesto.
 *
 * <ul>
 *   <li><b>Delante</b>: un cuadrado de lado {@code 2 * radius + 1} justo delante del puesto (empieza a 2 bloques, para
 *       que quepa el edificio del oficio detrás), de {@code down} bloques por debajo a {@code up} por encima.</li>
 *   <li><b>Pozo</b>: un cuadrado de lado {@code 2 * radius + 1} <i>detrás</i> del puesto que se vacía capa a capa.
 *       El del excavador se abre desde la superficie, separado del puesto por un pasillo de un bloque; el del minero
 *       es una mina bajo tierra al final de una entrada de 5 escalones (ver {@link ExcavationJob}).</li>
 *   <li><b>Ninguna</b>: los talleres trabajan en el propio puesto.</li>
 * </ul>
 */
public record WorkArea(int radius, boolean pit, boolean underground, int down, int up) {
	public static final WorkArea NONE = new WorkArea(0, false, false, 0, 0);

	public static WorkArea around(int radius, int down, int up) {
		return new WorkArea(radius, false, false, down, up);
	}

	/** Pozo abierto desde la superficie, detrás del puesto. */
	public static WorkArea pit(int radius) {
		return new WorkArea(radius, true, false, 1, -1);
	}

	/** Mina bajo tierra, al final de una entrada de 5 escalones. */
	public static WorkArea mine(int radius) {
		return new WorkArea(radius, true, true, 1, -1);
	}

	public boolean exists() {
		return radius > 0;
	}

	/** Centro de la zona: delante del puesto o, para los pozos, detrás de él (la mina, después de la entrada). */
	public BlockPos center(BlockPos site, Direction facing) {
		if (!pit) {
			return site.offset(facing, radius + 2);
		}
		return site.offset(facing.getOpposite(), radius + (underground ? ExcavationJob.PIT_START : 2));
	}

	/** Clave del texto que describe la zona ("Zona: 11×11...", "Pozo de 7×7..."). */
	public String descriptionKey() {
		if (!pit) {
			return "gui.evolvedwarfare.site.area";
		}
		return underground ? "gui.evolvedwarfare.site.area_mine" : "gui.evolvedwarfare.site.area_pit";
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

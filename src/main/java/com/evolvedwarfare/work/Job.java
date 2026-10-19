package com.evolvedwarfare.work;

import org.jetbrains.annotations.Nullable;

/**
 * El trabajo de un oficio. Cada trabajador tiene su propia instancia (puede recordar cosas entre tareas,
 * como qué vacas ordeñó hace poco).
 */
public interface Job {
	/**
	 * Busca la siguiente tarea. Si no hay nada que hacer devuelve {@code null} y deja el motivo con
	 * {@link WorkContext#idle(WorkStatus)} (devolver {@code ctx.idle(...)} hace las dos cosas).
	 */
	@Nullable
	WorkAction plan(WorkContext ctx);
}

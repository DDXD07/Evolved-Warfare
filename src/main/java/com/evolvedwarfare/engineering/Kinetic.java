package com.evolvedwarfare.engineering;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Un bloque de la red de fuerza (fase 72): cómo se une a sus vecinos. La velocidad (en rpm) va con signo, según el sentido
 * positivo de su eje; un eje la pasa igual, dos engranajes que se tocan la invierten y uno grande con uno pequeño en
 * diagonal la reparten a la mitad o al doble.
 */
public interface Kinetic {
	/** ¿Tiene eje (o una salida) por esa cara? */
	boolean hasAxle(BlockState state, Direction face);

	/** El eje sobre el que gira (para los engranajes y para dibujarlo); null si no gira sobre uno. */
	@Nullable
	Direction.Axis axis(BlockState state);

	/** 0 sin engranaje, 1 pequeño, 2 grande. */
	default int gear(BlockState state) {
		return 0;
	}

	/** Lo que da como fuente (rpm con signo; 0 si no da), mirado cada poco. */
	default float generate(ServerWorld world, BlockPos pos, BlockState state, KineticBlockEntity be) {
		return 0.0F;
	}

	/** La fuerza que aporta como fuente cuando gira (en unidades de fuerza). */
	default float capacity(ServerWorld world, BlockPos pos, BlockState state, KineticBlockEntity be) {
		return 0.0F;
	}

	/** La fuerza que gasta por cada rpm (las máquinas; 0 los ejes y engranajes). */
	default float impact(BlockState state) {
		return 0.0F;
	}

	/** Cada cuántos tics mira si su fuente ha cambiado (0: no es fuente). */
	default int sourceInterval() {
		return 0;
	}
}

package com.evolvedwarfare.hell;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Solo para que el cliente dibuje la cabeza del ídolo (que gira hacia el jugador); no guarda nada. */
public class IdolBlockEntity extends BlockEntity {
	/** Hacia dónde mira ahora la cabeza (solo en el cliente, para suavizar el giro). */
	public float yaw = Float.NaN;
	public float pitch;

	public IdolBlockEntity(BlockPos pos, BlockState state) {
		super(HellStructures.IDOL_ENTITY, pos, state);
	}
}

package com.evolvedwarfare.work;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Se queda con la posición más cercana al trabajador de las que se le ofrecen (y un número para saber de qué tipo es). */
final class Nearest {
	@Nullable
	BlockPos pos;
	int kind;
	private double distance = Double.MAX_VALUE;

	void offer(WorkContext ctx, BlockPos candidate) {
		offer(ctx, candidate, 0);
	}

	void offer(WorkContext ctx, BlockPos candidate, int candidateKind) {
		double d = ctx.distanceSq(candidate);
		if (d < distance) {
			distance = d;
			pos = candidate.toImmutable();
			kind = candidateKind;
		}
	}

	boolean found() {
		return pos != null;
	}
}

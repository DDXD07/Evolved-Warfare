package com.evolvedwarfare.blueprint;

import com.evolvedwarfare.building.BuildPlan;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Coloca un plano de golpe (en creativo): limpia su hueco, rellena bajo el suelo y pone todos los bloques. */
public final class BlueprintPlacer {
	private BlueprintPlacer() {
	}

	/** 'post' hace de puesto: el edificio queda detrás, con el frente mirando hacia 'facing'. Devuelve los bloques puestos. */
	public static int placeInstantly(ServerWorld world, StoredBlueprint stored, BlockPos post, Direction facing) {
		BuildPlan plan = BuildPlan.of(stored.toBlueprint(), post, facing);
		BlockState air = Blocks.AIR.getDefaultState();
		for (BuildPlan.Entry entry : plan.clearOrder()) {
			if (!world.getBlockState(entry.pos).isAir()) {
				world.setBlockState(entry.pos, air, Block.NOTIFY_LISTENERS);
			}
		}
		BlockState dirt = Blocks.DIRT.getDefaultState();
		for (BuildPlan.Entry entry : plan.foundations()) {
			BlockPos below = entry.pos.down();
			for (int i = 0; i < 4 && world.getBlockState(below).isReplaceable(); i++) {
				world.setBlockState(below, dirt, Block.NOTIFY_LISTENERS);
				below = below.down();
			}
		}
		int placed = 0;
		for (BuildPlan.Entry entry : plan.placeOrder()) {
			if (entry.state != null) {
				world.setBlockState(entry.pos, entry.state, Block.NOTIFY_LISTENERS);
				placed++;
			}
		}
		return placed;
	}
}
